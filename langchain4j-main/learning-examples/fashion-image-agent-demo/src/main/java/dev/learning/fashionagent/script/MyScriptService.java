package dev.learning.fashionagent.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.comfyui.ComfyUiVideoGenerationService;
import dev.learning.fashionagent.comfyui.ComfyUiVideoView;
import dev.learning.fashionagent.config.QwenProperties;
import dev.learning.fashionagent.director.ShortDramaDirectorPromptLibrary;
import dev.learning.fashionagent.service.QwenRestClientProvider;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class MyScriptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyScriptService.class);
    private static final Pattern EPISODE_MARKER = Pattern.compile("(?m)^【?第\\s*(\\d+)\\s*集[^】]*】?\\s*$");
    private static final Pattern TITLE_MARKER = Pattern.compile("(?m)^【剧本名称】\\s*(.+)$");
    private static final Pattern SETTINGS_MARKER = Pattern.compile("(?s)【剧本设定】\\s*(.*?)(?=【第\\s*1\\s*集|$)");
    private final MyScriptRepository repository;
    private final QwenProperties properties;
    private final QwenRestClientProvider clients;
    private final ShortDramaDirectorPromptLibrary prompts;
    private final ComfyUiVideoGenerationService comfy;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final URI endpoint;
    private final Map<UUID, Object> replicationLocks = new ConcurrentHashMap<>();

    public MyScriptService(MyScriptRepository repository, QwenProperties properties, QwenRestClientProvider clients,
                           ShortDramaDirectorPromptLibrary prompts, ComfyUiVideoGenerationService comfy,
                           ObjectMapper mapper, @Qualifier("storyVideoExecutor") Executor executor) {
        this.repository = repository; this.properties = properties; this.clients = clients; this.prompts = prompts;
        this.comfy = comfy; this.mapper = mapper; this.executor = executor;
        this.endpoint = URI.create(properties.getBaseUrl().toString().replaceAll("/+$", "") + "/chat/completions");
    }

    public void archiveInitial(UUID sourceJobId, String sourceText, String result, String tier, String platform, String ratio) {
        if (result == null || result.isBlank()) return;
        List<MyScriptRepository.Project> existing = repository.listProjects();
        if (existing.stream().anyMatch(project -> project.sourceJobId().equals(sourceJobId))) return;
        ParsedScript parsed = parseInitial(result, sourceText);
        Instant now = Instant.now();
        MyScriptRepository.Project project = new MyScriptRepository.Project(UUID.randomUUID(), sourceJobId, parsed.title(), parsed.settings(), now, now);
        repository.saveProject(project);
        LOGGER.info("短剧导演剧本设定已归档，等待用户开始剧情推演 projectId={} sourceJobId={} title={}", project.id(), sourceJobId, project.title());
    }

    public List<ProjectView> list() { return repository.listProjects().stream().map(this::view).toList(); }
    public ProjectView get(UUID id) { return view(requireProject(id)); }
    public EpisodeView episode(UUID id) { return episodeView(requireEpisode(id)); }
    public List<SegmentView> segments(UUID episodeId) { return repository.listSegments(episodeId).stream().map(this::segmentView).toList(); }
    public List<CharacterView> characters(UUID projectId) { return repository.listCharacterAssets(projectId).stream().map(this::characterView).toList(); }
    public void saveCharacters(UUID projectId, List<CharacterRequest> requests) {
        requireProject(projectId); if (requests == null) return; Instant now = Instant.now();
        for (int i = 0; i < requests.size(); i++) { CharacterRequest r = requests.get(i); if (r == null || r.characterName() == null || r.characterName().isBlank()) continue;
            repository.saveCharacterAsset(new MyScriptRepository.CharacterAsset(UUID.nameUUIDFromBytes((projectId + ":" + r.characterName().trim()).getBytes()), projectId, r.characterName().trim(), r.roleLevel(), r.anchor(), r.imageSourcesJson() == null ? "[]" : r.imageSourcesJson(), i, now, now)); }
    }
    public EpisodeView startFirstEpisode(UUID projectId) { return continueEpisode(projectId); }
    public SegmentView updateSegment(UUID id, String content, Integer durationSeconds) {
        MyScriptRepository.Segment original = requireSegment(id);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("段落提示词不能为空");
        int duration = durationSeconds == null ? original.durationSeconds() : Math.max(1, Math.min(15, durationSeconds));
        MyScriptRepository.Segment changed = new MyScriptRepository.Segment(original.id(), original.episodeId(), original.number(), content.trim(), duration, original.status(), original.comfyTaskId(), original.error(), original.createdAt(), Instant.now());
        repository.saveSegment(changed); return segmentView(changed);
    }

    public EpisodeView continueEpisode(UUID projectId) {
        MyScriptRepository.Project project = requireProject(projectId);
        List<MyScriptRepository.Episode> episodes = repository.listEpisodes(projectId);
        MyScriptRepository.Episode activeEpisode = episodes.stream()
                .filter(item -> "QUEUED".equals(item.status()) || "RUNNING".equals(item.status()))
                .findFirst()
                .orElse(null);
        if (activeEpisode != null) {
            LOGGER.info("剧本续写已有进行中的请求，返回现有任务 projectId={} episodeId={} episode={}",
                    projectId, activeEpisode.id(), activeEpisode.number());
            return episodeView(activeEpisode);
        }
        int number = episodes.size() + 1;
        Instant now = Instant.now();
        MyScriptRepository.Episode episode = new MyScriptRepository.Episode(UUID.randomUUID(), projectId, number, "第" + number + "集", null, "QUEUED", "已排队续写", null, now, now);
        repository.saveEpisode(episode);
        String apiKey = properties.requiredApiKey();
        executor.execute(() -> writeNextEpisode(project, episodes, episode, apiKey));
        return episodeView(episode);
    }

    public List<SegmentView> prepareReplication(UUID episodeId) {
        Object lock = replicationLocks.computeIfAbsent(episodeId, ignored -> new Object());
        synchronized (lock) {
            return prepareReplicationLocked(episodeId);
        }
    }

    private List<SegmentView> prepareReplicationLocked(UUID episodeId) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        List<MyScriptRepository.Segment> existing = repository.listSegments(episodeId);
        // Older builds stored the entire project setting in every visible card.
        // Rebuild those untouched legacy cards once, while preserving generated/edited cards.
        if (!existing.isEmpty() && existing.stream().allMatch(this::isLegacySegment)
                && existing.stream().noneMatch(segment -> segment.comfyTaskId() != null)) {
            repository.deleteSegments(episodeId);
            existing = List.of();
            LOGGER.info("检测到旧版重复复刻段落，已按当前集正文重新整理 episodeId={}", episodeId);
        }
        if (!existing.isEmpty()) return existing.stream().map(this::segmentView).toList();
        if (episode.content() == null || episode.content().isBlank()) throw new IllegalStateException("该集尚未生成内容");
        List<PlannedSegment> parts = planReplication(episode);
        Instant now = Instant.now();
        List<MyScriptRepository.Segment> segments = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            PlannedSegment part = parts.get(index);
            MyScriptRepository.Segment segment = new MyScriptRepository.Segment(UUID.randomUUID(), episodeId, index + 1, part.text(), part.durationSeconds(), "READY", null, null, now, now);
            repository.saveSegment(segment); segments.add(segment);
        }
        return segments.stream().map(this::segmentView).toList();
    }

    public List<SegmentView> replanReplication(UUID episodeId) {
        Object lock = replicationLocks.computeIfAbsent(episodeId, ignored -> new Object());
        synchronized (lock) {
            return replanReplicationLocked(episodeId);
        }
    }

    private List<SegmentView> replanReplicationLocked(UUID episodeId) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        if (episode.content() == null || episode.content().isBlank()) throw new IllegalStateException("该集尚未生成内容");
        if (repository.listSegments(episodeId).stream().anyMatch(segment -> segment.comfyTaskId() != null)) {
            throw new IllegalStateException("本集已有生成视频，不能覆盖现有段落；请直接编辑段落或重新生成视频");
        }
        repository.deleteSegments(episodeId);
        return prepareReplicationLocked(episodeId);
    }

    public SegmentView replicate(UUID segmentId, List<String> images, String resolution) {
        MyScriptRepository.Segment segment = requireSegment(segmentId);
        MyScriptRepository.Episode episode = requireEpisode(segment.episodeId());
        MyScriptRepository.Project project = requireProject(episode.projectId());
        List<MyScriptRepository.CharacterAsset> assets = repository.listCharacterAssets(project.id());
        List<MyScriptRepository.CharacterAsset> matchedAssets = assets.stream()
                .filter(asset -> segment.content() != null && segment.content().contains(asset.characterName()))
                .toList();
        List<String> references = new ArrayList<>();
        for (MyScriptRepository.CharacterAsset asset : (matchedAssets.isEmpty() ? assets : matchedAssets)) try { JsonNode node = mapper.readTree(asset.imageSourcesJson()); if (node.isArray()) node.forEach(v -> { if (v.isTextual() && !v.asText().isBlank()) references.add(v.asText()); }); } catch (Exception ignored) { }
        if (references.isEmpty() && images != null) references.addAll(images);
        if (references.isEmpty()) throw new IllegalStateException("请先在复刻页面角色资产区上传参考图");
        String generationPrompt = buildGenerationPrompt(project.settings(), episode, segment.number(), segment.content());
        ComfyUiVideoView video = comfy.create(generationPrompt, Math.max(1, Math.min(15, segment.durationSeconds())), resolution, references);
        MyScriptRepository.Segment running = new MyScriptRepository.Segment(segment.id(), segment.episodeId(), segment.number(), segment.content(), segment.durationSeconds(), "SUBMITTED", video.id(), null, segment.createdAt(), Instant.now());
        repository.saveSegment(running);
        return segmentView(running);
    }

    private String projectSettings(UUID id) { return repository.findProject(id).map(MyScriptRepository.Project::settings).orElse(""); }
    private List<PlannedSegment> planReplication(MyScriptRepository.Episode episode) {
        try {
            String system = "你是资深影视分镜和镜头语言分析师。只处理当前集正文，不要复述整份剧本设定。按剧情因果把当前集拆成连续独立段落；一集约3分钟时必须优先输出6段，每段承载约30秒的实际剧情。只输出JSON数组，不要Markdown或解释。每个元素格式：{\"text\":\"一段完整、可直接交给视频模型的中文描述\",\"durationSeconds\":30}。每段只能使用当前集对应的不同剧情推进，不能复制其他段落。每段文字必须是一段话，清楚写出：本段出场人物及人物关系；具体环境、空间锚点和主导环境压力；主体构图、景别、视角、焦段感和摄影机运动；清晰度、真实光线、色彩与景深；人物可观察的表情生理变化、动作因果、受力和视线；对白必须用中文双引号并加字幕；声音和节奏；与上一段承接的动作/视线/光源，以及结尾留给下一段的动作状态。遵守镜头三任务法则、空间可读性、30°~60°斜角和180度轴线，避免空洞形容词，改写为摄影机能捕捉的物理事实。不要输出分镜编号、时间轴、时长说明、剧本设定标题、规则解释或无效套话；人物外貌和服装只引用上传资产，不要自行描写。";
            String user = "【剧本设定】\n" + projectSettings(episode.projectId()) + "\n【" + episode.title() + "】\n" + episode.content();
            String raw = callQwen(system, user, properties.requiredApiKey());
            JsonNode array = mapper.readTree(raw.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim());
            if (!array.isArray() || array.isEmpty()) throw new IllegalStateException("千问复刻规划为空");
            List<PlannedSegment> result = new ArrayList<>();
            for (JsonNode item : array) {
                String text = item.path("text").asText(item.path("prompt").asText("")).trim();
                text = cleanVisibleSegment(text);
                final String plannedText = text;
                if (!plannedText.isBlank() && result.stream().noneMatch(previous -> sameText(previous.text(), plannedText))) {
                    int duration = item.path("durationSeconds").asInt(30);
                    result.add(new PlannedSegment(plannedText, Math.max(1, Math.min(30, duration))));
                }
            }
            if (result.size() >= 2) { LOGGER.info("千问复刻规划完成 episodeId={} segments={}", episode.id(), result.size()); return result.size() > 6 ? result.subList(0, 6) : result; }
        } catch (Exception error) { LOGGER.warn("千问复刻规划失败，使用本地兜底 episodeId={} reason={}", episode.id(), rootMessage(error)); }
        return splitEpisode(episode.content()).stream().map(text -> new PlannedSegment(cleanVisibleSegment(text), 30)).toList();
    }
    private static String buildGenerationPrompt(String settings, MyScriptRepository.Episode episode, int number, String content) {
        String handoff = number == 1 ? "建立本集开场空间" : "承接上一段最后一帧的动作、视线、光线和空间位置";
        return "【仅供视频模型使用的全局设定，不要在画面生成文字】\n" + settings
                + "\n【当前集实际剧情】\n" + content
                + "\n保持人物参考图1:1，不描述或改变人物外貌服装；保持空间锚点、光源方向、色彩、镜头轴线和时间连续；中景/全景采用30°~60°斜角；" + handoff
                + "；所有对白使用中文双引号并加字幕；禁止文字水印乱码、额外人物、人物资产漂移、跳轴和突然变更场景。";
    }
    private static String cleanVisibleSegment(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?m)^\\s*[【\\[]?(?:剧本设定|全局不变量|负面约束|连续性承接|时间线与镜头|独立视频段落)[】\\]]?[^\\n]*\\n?", "")
                .replaceAll("(?m)^\\s*(?:段落|分镜|镜头)\\s*\\d+[：:、.]?\\s*", "")
                .replaceAll("\\s+", " ").trim();
        return cleaned;
    }
    private static boolean sameText(String left, String right) { return left.replaceAll("\\s+", "").equals(right.replaceAll("\\s+", "")); }
    private boolean isLegacySegment(MyScriptRepository.Segment segment) { return segment.content() != null && (segment.content().contains("【独立视频段落") || segment.content().contains("全局不变量：")); }
    private record PlannedSegment(String text, int durationSeconds) {}

    private void writeNextEpisode(MyScriptRepository.Project project, List<MyScriptRepository.Episode> previous, MyScriptRepository.Episode episode, String apiKey) {
        long startedNanos = System.nanoTime();
        try {
            updateEpisode(episode, "RUNNING", "正在调用千问续写", null, null);
            LOGGER.info("剧本续写后台任务开始 projectId={} episodeId={} episode={} previousEpisodes={}",
                    project.id(), episode.id(), episode.number(), previous.size());
            String last = previous.isEmpty() ? "无" : previous.get(previous.size() - 1).content();
            String system = prompts.episodeInstructions("R2", "抖音", "9:16")
                    + "\n你现在只创作第" + episode.number() + "集。必须承接已给剧本设定和上一集结尾，不要重复剧本设定；输出以【第" + episode.number() + "集】开始，给出完整可拍摄内容，按每段不超过15秒的节拍分段。";
            String user = "【剧本设定】\n" + project.settings() + "\n【上一集】\n" + last;
            String content = callQwen(system, user, apiKey);
            content = content.replaceFirst("(?s)^.*?【第\\s*" + episode.number() + "\\s*集[^】]*】", "").trim();
            if (content.isBlank()) throw new IllegalStateException("千问未返回第" + episode.number() + "集正文");
            updateEpisode(episode, "SUCCESS", "续写完成", content, null);
            LOGGER.info("剧本续写完成 projectId={} episodeId={} episode={} durationMs={} contentChars={}",
                    project.id(), episode.id(), episode.number(), elapsedMillis(startedNanos), content.length());
        } catch (Exception exception) {
            LOGGER.error("剧本续写失败 projectId={} episodeId={} episode={} durationMs={} reason={}",
                    project.id(), episode.id(), episode.number(), elapsedMillis(startedNanos), rootMessage(exception), exception);
            updateEpisode(episode, "FAILED", "续写失败，可再次点击再来一集", null, rootMessage(exception));
        }
    }

    private String callQwen(String system, String user, String apiKey) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        body.put("stream", false); body.put("enable_thinking", true);
        QwenRestClientProvider.Selection selection = clients.select();
        long startedNanos = System.nanoTime();
        LOGGER.info("我的剧本千问续写请求发送 endpoint={} route={} model={} thinking={} systemChars={} userChars={}",
                endpoint, selection.route(), properties.getModel(), body.get("enable_thinking"), system.length(), user.length());
        try {
            JsonNode response = selection.client().post().uri(endpoint).headers(headers -> {
                headers.setBearerAuth(apiKey); headers.setAccept(List.of(MediaType.APPLICATION_JSON)); headers.set("User-Agent", "atelier-flow/1.0");
            }).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().onStatus(HttpStatusCode::isError, (request, result) -> {
                throw new IllegalStateException("千问接口请求失败（HTTP " + result.getStatusCode().value() + "）：" + new String(result.getBody().readAllBytes()));
            }).body(JsonNode.class);
            String text = extractText(response);
            LOGGER.info("我的剧本千问续写响应完成 endpoint={} route={} durationMs={} contentChars={}",
                    endpoint, selection.route(), elapsedMillis(startedNanos), text == null ? 0 : text.length());
            if (text == null || text.isBlank()) throw new IllegalStateException("千问未返回下一集内容");
            return text.trim();
        } catch (RuntimeException exception) {
            LOGGER.warn("我的剧本千问续写请求异常 endpoint={} route={} durationMs={} reason={}",
                    endpoint, selection.route(), elapsedMillis(startedNanos), rootMessage(exception), exception);
            throw exception;
        }
    }

    private ProjectView view(MyScriptRepository.Project p) { return new ProjectView(p.id(), p.title(), p.settings(), repository.listEpisodes(p.id()).stream().map(this::episodeView).toList(), p.createdAt(), p.updatedAt()); }
    private EpisodeView episodeView(MyScriptRepository.Episode e) { return new EpisodeView(e.id(), e.projectId(), e.number(), e.title(), e.content(), e.status(), e.message(), e.error(), e.createdAt(), e.updatedAt()); }
    private SegmentView segmentView(MyScriptRepository.Segment s) { return new SegmentView(s.id(), s.episodeId(), s.number(), s.content(), s.durationSeconds(), s.status(), s.comfyTaskId(), s.error(), s.createdAt(), s.updatedAt()); }
    private MyScriptRepository.Project requireProject(UUID id) { return repository.findProject(id).orElseThrow(() -> new IllegalArgumentException("剧本不存在")); }
    private MyScriptRepository.Episode requireEpisode(UUID id) { return repository.findEpisode(id).orElseThrow(() -> new IllegalArgumentException("剧集不存在")); }
    private MyScriptRepository.Segment requireSegment(UUID id) { return repository.findSegment(id).orElseThrow(() -> new IllegalArgumentException("复刻分段不存在")); }
    private void updateEpisode(MyScriptRepository.Episode original, String status, String message, String content, String error) { repository.saveEpisode(new MyScriptRepository.Episode(original.id(), original.projectId(), original.number(), original.title(), content == null ? original.content() : content, status, message, error, original.createdAt(), Instant.now())); }
    private static ParsedScript parseInitial(String result, String source) { Matcher title = TITLE_MARKER.matcher(result); String name = title.find() ? title.group(1).trim() : fallbackTitle(source); Matcher settings = SETTINGS_MARKER.matcher(result); String set = settings.find() ? settings.group(1).trim() : result.trim(); return new ParsedScript(name, set, ""); }
    private static String fallbackTitle(String source) { String cleaned = source == null ? "未命名剧本" : source.replaceAll("\\s+", " ").trim(); return cleaned.isBlank() ? "未命名剧本" : cleaned.substring(0, Math.min(28, cleaned.length())); }
    private static List<String> splitEpisode(String content) { List<String> units = new ArrayList<>(); for (String piece : content.split("(?=【(?:场景|段落|镜头|第?\\d+段)|\\n\\s*\\n)")) { String trimmed = piece.trim(); if (!trimmed.isBlank()) units.add(trimmed); } if (units.size() >= 2) return units.size() > 6 ? units.subList(0, 6) : units; List<String> chunks = new ArrayList<>(); StringBuilder current = new StringBuilder(); for (String sentence : content.split("(?<=[。！？!?])")) { if (current.length() + sentence.length() > 420 && !current.isEmpty()) { chunks.add(current.toString().trim()); current.setLength(0); } current.append(sentence); } if (!current.isEmpty()) chunks.add(current.toString().trim()); return chunks.isEmpty() ? List.of(content) : chunks.size() > 6 ? chunks.subList(0, 6) : chunks; }
    private static String extractText(JsonNode response) { if (response == null) return null; JsonNode node = response.at("/choices/0/message/content"); if (node.isTextual()) return node.asText(); node = response.at("/output/0/content/0/text"); if (node.isTextual()) return node.asText(); node = response.at("/output/text"); return node.isTextual() ? node.asText() : null; }
    private static long elapsedMillis(long startedNanos) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos); }
    private static String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.toString() : current.getMessage(); }
    private record ParsedScript(String title, String settings, String firstEpisode) {}
    public record ProjectView(UUID id, String title, String settings, List<EpisodeView> episodes, Instant createdAt, Instant updatedAt) {}
    public record EpisodeView(UUID id, UUID projectId, int number, String title, String content, String status, String message, String error, Instant createdAt, Instant updatedAt) {}
    public record SegmentView(UUID id, UUID episodeId, int number, String content, int durationSeconds, String status, UUID comfyTaskId, String error, Instant createdAt, Instant updatedAt) {}
    public record CharacterView(UUID id, UUID projectId, String characterName, String roleLevel, String anchor, String imageSourcesJson, int sortOrder, Instant createdAt, Instant updatedAt) {}
    public record CharacterRequest(String characterName, String roleLevel, String anchor, String imageSourcesJson) {}
    private CharacterView characterView(MyScriptRepository.CharacterAsset a) { return new CharacterView(a.id(), a.projectId(), a.characterName(), a.roleLevel(), a.anchor(), a.imageSourcesJson(), a.sortOrder(), a.createdAt(), a.updatedAt()); }
}
