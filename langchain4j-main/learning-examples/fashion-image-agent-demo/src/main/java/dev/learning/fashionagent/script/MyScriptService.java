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
    private static final int MAX_REPLICATION_SEGMENT_SECONDS = 15;
    private static final int MAX_REPLICATION_SEGMENTS = 15;
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
    public EpisodeView rewriteEpisode(UUID episodeId, String idea) {
        return rewriteEpisode(episodeId, idea, null);
    }
    public EpisodeView rewriteEpisode(UUID episodeId, String idea, UUID promptId) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        if (idea == null || idea.isBlank()) throw new IllegalArgumentException("请先填写本集重写想法");
        if ("QUEUED".equals(episode.status()) || "RUNNING".equals(episode.status())) {
            throw new IllegalStateException("本集已有任务正在执行，请等待完成后再重写");
        }
        if (repository.listSegments(episodeId).stream().anyMatch(segment -> segment.comfyTaskId() != null)) {
            throw new IllegalStateException("本集已有生成视频，不能直接重写；请先保留现有版本或另建剧本");
        }
        MyScriptRepository.Project project = requireProject(episode.projectId());
        MyScriptRepository.Prompt basePrompt = promptId == null ? null : repository.findPrompt(promptId)
                .filter(prompt -> prompt.episodeId().equals(episodeId))
                .orElseThrow(() -> new IllegalArgumentException("提示词记录不存在或不属于当前集"));
        MyScriptRepository.Episode queued = new MyScriptRepository.Episode(episode.id(), episode.projectId(), episode.number(), episode.title(), episode.content(), "QUEUED", "正在排队重写本集", null, episode.createdAt(), Instant.now());
        repository.saveEpisode(queued);
        String apiKey = properties.requiredApiKey();
        executor.execute(() -> rewriteEpisodeInBackground(project, episode, idea.trim(), basePrompt, apiKey));
        return episodeView(queued);
    }

    private void rewriteEpisodeInBackground(MyScriptRepository.Project project, MyScriptRepository.Episode original, String idea, MyScriptRepository.Prompt basePrompt, String apiKey) {
        long startedNanos = System.nanoTime();
        MyScriptRepository.Prompt prompt = null;
        try {
            updateEpisode(original, "RUNNING", "正在结合创作想法调用千问重写", null, null);
            MyScriptRepository.Episode previous = previousEpisode(original);
            String previousText = previous == null ? "无上一集" : safe(previous.content());
            String system = prompts.episodeInstructions("R2", "抖音", "9:16")
                    + "\n现在重写第" + original.number() + "集。只输出这一集完整正文，不要输出剧本设定、解释、分析或Markdown。必须保留剧本设定中的人物资产、世界观、画面基调和已确定的连续性；根据用户重写想法调整冲突、节奏、动作、对白和结尾钩子。每段不超过15秒，人物服装和外貌按已锁定资产执行；对白使用中文双引号并加字幕。输出以【第" + original.number() + "集】开始。";
            String user = "【剧本设定】\n" + project.settings()
                    + "\n【上一集正文，用于衔接】\n" + previousText
                    + "\n【当前第" + original.number() + "集原稿】\n" + safe(original.content())
                    + (basePrompt == null ? "" : "\n【参考历史提示词版本" + basePrompt.version() + "】\n" + safe(basePrompt.promptText()))
                    + "\n【用户对本集的重写想法】\n" + idea;
            prompt = newPrompt(original.id(), "USER_REWRITE", basePrompt == null ? "用户重写" : "基于历史提示词重写", idea, system, user);
            repository.savePrompt(prompt);
            prompt = updatePrompt(prompt, "RUNNING", null, null);
            String content = callQwen(system, user, apiKey);
            content = stripEpisodeHeading(content, original.number());
            if (content.isBlank()) throw new IllegalStateException("千问未返回重写后的第" + original.number() + "集正文");
            updateEpisode(original, "SUCCESS", "本集重写完成", content, null);
            updatePrompt(prompt, "SUCCESS", content, null);
            repository.deleteSegments(original.id());
            repository.deleteReplicationMaterial(original.id());
            LOGGER.info("剧本本集重写完成 projectId={} episodeId={} episode={} durationMs={} contentChars={}", project.id(), original.id(), original.number(), elapsedMillis(startedNanos), content.length());
        } catch (Exception exception) {
            LOGGER.error("剧本本集重写失败 projectId={} episodeId={} episode={} durationMs={} reason={}", project.id(), original.id(), original.number(), elapsedMillis(startedNanos), rootMessage(exception), exception);
            updateEpisode(original, "FAILED", "重写失败，原内容已保留，可再次提交重写", null, rootMessage(exception));
            if (prompt != null) updatePrompt(prompt, "FAILED", null, rootMessage(exception));
        }
    }

    private static String stripEpisodeHeading(String content, int number) {
        if (content == null) return "";
        String marker = "(?s)^.*?【第\\s*" + number + "\\s*集[^】]*】";
        return content.replaceFirst(marker, "").trim();
    }
    public SegmentView updateSegment(UUID id, String content, Integer durationSeconds) {
        MyScriptRepository.Segment original = requireSegment(id);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("段落提示词不能为空");
        int duration = durationSeconds == null ? original.durationSeconds() : Math.max(1, Math.min(MAX_REPLICATION_SEGMENT_SECONDS, durationSeconds));
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

    public ReplicationView prepareReplication(UUID episodeId) {
        Object lock = replicationLocks.computeIfAbsent(episodeId, ignored -> new Object());
        synchronized (lock) {
            return prepareReplicationLocked(episodeId);
        }
    }

    private ReplicationView prepareReplicationLocked(UUID episodeId) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        List<MyScriptRepository.Segment> existing = repository.listSegments(episodeId);
        // Older builds stored the entire project setting in every visible card.
        // Rebuild those untouched legacy cards once, while preserving generated/edited cards.
        if (!existing.isEmpty() && (existing.stream().allMatch(this::isLegacySegment) || isRepeatedLegacySet(existing) || isLowQualitySegmentSet(existing))
                && existing.stream().noneMatch(segment -> segment.comfyTaskId() != null)) {
            repository.deleteSegments(episodeId);
            existing = List.of();
            LOGGER.info("检测到旧版重复复刻段落，已按当前集正文重新整理 episodeId={}", episodeId);
        }
        if (!existing.isEmpty()) return replicationView(episodeId, existing);
        if (episode.content() == null || episode.content().isBlank()) throw new IllegalStateException("该集尚未生成内容");
        ReplicationPlan plan = planReplication(episode);
        saveMaterial(episodeId, plan.material());
        Instant now = Instant.now();
        List<MyScriptRepository.Segment> segments = new ArrayList<>();
        for (int index = 0; index < plan.segments().size(); index++) {
            PlannedSegment part = plan.segments().get(index);
            MyScriptRepository.Segment segment = new MyScriptRepository.Segment(UUID.randomUUID(), episodeId, index + 1, part.text(), part.durationSeconds(), "READY", null, null, now, now);
            repository.saveSegment(segment); segments.add(segment);
        }
        return new ReplicationView(plan.material(), segments.stream().map(this::segmentView).toList());
    }

    public ReplicationView replanReplication(UUID episodeId) {
        Object lock = replicationLocks.computeIfAbsent(episodeId, ignored -> new Object());
        synchronized (lock) {
            return replanReplicationLocked(episodeId);
        }
    }

    private ReplicationView replanReplicationLocked(UUID episodeId) {
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
        String generationPrompt = buildGenerationPrompt(project.settings(), episode, segment.number(), segment.content(), materialFor(episode));
        ComfyUiVideoView video = comfy.create(generationPrompt, Math.max(1, Math.min(MAX_REPLICATION_SEGMENT_SECONDS, segment.durationSeconds())), resolution, references);
        MyScriptRepository.Segment running = new MyScriptRepository.Segment(segment.id(), segment.episodeId(), segment.number(), segment.content(), segment.durationSeconds(), "SUBMITTED", video.id(), null, segment.createdAt(), Instant.now());
        repository.saveSegment(running);
        return segmentView(running);
    }

    private String projectSettings(UUID id) { return repository.findProject(id).map(MyScriptRepository.Project::settings).orElse(""); }
    private static String replicationInstructions() {
        return """
                你是资深影视分镜和镜头语言分析师。只允许调用一次千问，必须一次性返回当前集资料和全部段落，严禁为每个段落再次请求模型或输出第二个版本。输出严格为JSON对象，不要Markdown：
                {"episodeMaterial":{"charactersWardrobe":"本集实际出场人物、人物关系、当前服装装束和资产短锚点","environment":"本集实际场景、空间锚点、光线方向、环境压力、色彩、画质、景深和声音氛围","plot":"本集主要剧情、冲突推进、情绪曲线、关键道具和结尾钩子","continuity":"承接上一集的具体动作、视线、光源、轴线和情绪变化"},"segments":[{"text":"一段完整可直接交给视频模型的中文描述","plotBeat":"本段独立且不可替代的剧情推进","characters":"本段实际出场人物、关系、服装装束和资产锚点","environment":"本段实际环境、空间锚点和场面氛围","camera":"构图、景别、视角、焦段感和摄影机运镜","visual":"清晰度、真实光线、色彩、景深","performance":"可观察的表情生理变化、动作因果、受力和视线落点","sound":"对白双引号、字幕和声音节奏","handoff":"本段结尾如何把动作、视线、光源、轴线和情绪交给下一段","durationSeconds":15}]}
                约3分钟按实际剧情拆成12至15段，单段内容完整且适合30秒以内的口播/拍摄节奏；若视频模型限制15秒，durationSeconds填15。每段必须对应当前集一个新的因果节拍，不得复制、合并或虚构；每段至少写清一个具体动作、一个可观察反应、一个空间变化和一个剧情结果，建议正文不少于180个汉字。禁止出现“同上一段一致”“同上一集一致”“上一段结尾连续性参考”等占位语句，也不要粘贴上一段正文。禁止分镜编号、时间轴、时长字段、规则解释或空泛形容词；遵守镜头三任务法则、空间可读性、30°~60°斜角、Eye-Trace、反形容词转译和对白语速规则。人物外貌按上传参考资产1:1复刻，不扩写五官，不自行改变服装。""";
    }
    private ReplicationPlan planReplication(MyScriptRepository.Episode episode) {
        try {
            String system = replicationInstructions();
            MyScriptRepository.Episode previous = previousEpisode(episode);
            String previousContext = previous == null ? "无上一集，本集建立新的连续性基准" :
                    "【上一集连续性资料】\n" + materialFor(previous).asText() + "\n【上一集结尾正文】\n" + safe(previous.content());
            String user = "【剧本设定】\n" + projectSettings(episode.projectId()) + "\n" + previousContext + "\n【当前集正文：" + episode.title() + "】\n" + episode.content();
            String raw = callQwen(system, user, properties.requiredApiKey());
            JsonNode array = mapper.readTree(raw.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim());
            JsonNode materialNode = array.path("episodeMaterial");
            JsonNode segmentsNode = array.path("segments");
            if (!segmentsNode.isArray() || segmentsNode.isEmpty()) throw new IllegalStateException("千问复刻规划为空");
            EpisodeMaterial material = sanitizeMaterial(new EpisodeMaterial(materialNode.path("charactersWardrobe").asText("本集人物装束按参考资产锁定"), materialNode.path("environment").asText("本集环境按剧本设定锁定"), materialNode.path("plot").asText(episode.content()), materialNode.path("continuity").asText("承接上一集结尾")));
            List<PlannedSegment> result = new ArrayList<>();
            for (JsonNode item : segmentsNode) {
                String text = segmentText(item).trim();
                text = completeSegmentContext(cleanVisibleSegment(text), material);
                final String plannedText = text;
                if (!plannedText.isBlank() && result.stream().noneMatch(existingPart -> sameText(existingPart.text(), plannedText))) {
                    int duration = item.path("durationSeconds").asInt(MAX_REPLICATION_SEGMENT_SECONDS);
                    result.add(new PlannedSegment(plannedText, Math.max(1, Math.min(MAX_REPLICATION_SEGMENT_SECONDS, duration))));
                }
            }
            if (result.size() >= 2) { LOGGER.info("千问复刻规划完成 episodeId={} segments={} materialChars={}", episode.id(), result.size(), material.asText().length()); return new ReplicationPlan(material, result.size() > MAX_REPLICATION_SEGMENTS ? result.subList(0, MAX_REPLICATION_SEGMENTS) : result); }
        } catch (Exception error) { LOGGER.warn("千问复刻规划失败，使用本地兜底 episodeId={} reason={}", episode.id(), rootMessage(error)); }
        EpisodeMaterial material = fallbackMaterial(episode);
        List<String> parts = splitEpisode(episode.content());
        List<PlannedSegment> fallback = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            fallback.add(new PlannedSegment(completeSegmentContext(cleanVisibleSegment(parts.get(index)), material), MAX_REPLICATION_SEGMENT_SECONDS));
        }
        return new ReplicationPlan(material, fallback);
    }
    private static String buildGenerationPrompt(String settings, MyScriptRepository.Episode episode, int number, String content, EpisodeMaterial material) {
        String handoff = number == 1 ? "建立本集开场空间" : "承接上一段最后一帧的动作、视线、光线和空间位置";
        return "【仅供视频模型使用的全局设定，不要在画面生成文字】\n" + settings + "\n【本集复刻资料】\n" + material.asText()
                + "\n【当前集实际剧情】\n" + content
                + "\n人物外貌按参考图1:1复刻，当前服装装束必须按本集复刻资料明确保持，不自行设计或改变；保持空间锚点、光源方向、色彩、镜头轴线和时间连续；中景/全景采用30°~60°斜角；" + handoff
                + "；所有对白使用中文双引号并加字幕；禁止文字水印乱码、额外人物、人物资产漂移、跳轴和突然变更场景。";
    }
    private static String cleanVisibleSegment(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("(?m)^\\s*[【\\[]?(?:剧本设定|全局不变量|负面约束|连续性承接|时间线与镜头|独立视频段落)[】\\]]?[^\\n]*\\n?", "")
                .replaceAll("(?m)^\\s*(?:段落|分镜|镜头)\\s*\\d+[：:、.]?\\s*", "")
                .replaceAll("\\s+", " ").trim();
        return cleaned;
    }
    private static String segmentText(JsonNode item) {
        String text = item.path("text").asText(item.path("prompt").asText("")).trim();
        StringBuilder result = new StringBuilder();
        if (!text.isBlank()) result.append(text);
        appendMissingClause(result, text, "本段剧情推进", item.path("plotBeat").asText(item.path("story").asText("")));
        appendMissingClause(result, text, "本段人物与装束", item.path("characters").asText(item.path("characterContext").asText("")));
        appendMissingClause(result, text, "本段环境与场面氛围", item.path("environment").asText(""));
        appendMissingClause(result, text, "构图与摄影机", item.path("camera").asText(""));
        appendMissingClause(result, text, "画面质感", item.path("visual").asText(""));
        appendMissingClause(result, text, "表情动作与视线", item.path("performance").asText(""));
        appendMissingClause(result, text, "对白字幕与声音", item.path("sound").asText(""));
        appendMissingClause(result, text, "连续性衔接", item.path("handoff").asText(""));
        return result.toString().trim();
    }
    private static void appendMissingClause(StringBuilder target, String original, String label, String value) {
        if (value != null && !value.isBlank() && (original == null || !original.contains(label))) appendClause(target, label, value);
    }
    private static void appendClause(StringBuilder target, String label, String value) {
        if (value != null && !value.isBlank()) {
            if (target.length() > 0) target.append('；');
            target.append(label).append('：').append(value.trim());
        }
    }
    private static boolean sameText(String left, String right) { return left.replaceAll("\\s+", "").equals(right.replaceAll("\\s+", "")); }
    private boolean isLegacySegment(MyScriptRepository.Segment segment) { return segment.content() != null && (segment.content().contains("【独立视频段落") || segment.content().contains("全局不变量：")); }
    private boolean isRepeatedLegacySet(List<MyScriptRepository.Segment> segments) {
        if (segments.size() < 2) return false;
        Map<String, Long> frequencies = segments.stream()
                .map(item -> item.content() == null ? "" : item.content().replaceAll("\\s+", "").trim())
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(item -> item, java.util.stream.Collectors.counting()));
        long highestFrequency = frequencies.values().stream().mapToLong(Long::longValue).max().orElse(0);
        return highestFrequency >= Math.max(2, (segments.size() + 1) / 2);
    }
    private static boolean isLowQualitySegmentSet(List<MyScriptRepository.Segment> segments) {
        if (segments.size() < 2) return false;
        long forbidden = segments.stream().filter(item -> containsInternalContinuity(item.content())).count();
        double averageLength = segments.stream().mapToInt(item -> item.content() == null ? 0 : item.content().length()).average().orElse(0);
        return forbidden > 0 || averageLength < 180;
    }
    private static boolean containsInternalContinuity(String text) {
        if (text == null) return false;
        return text.contains("同上一段一致") || text.contains("同上一集一致") || text.contains("上一段结尾连续性参考") || text.contains("本段保持动作状态");
    }
    private static String completeSegmentContext(String text, EpisodeMaterial material) {
        if (text == null || text.isBlank()) return text;
        StringBuilder prefix = new StringBuilder();
        if (!text.contains("本段人物与装束") && !text.contains("本段出场人物及服装装束")) {
            prefix.append("本段人物与装束：").append(material.charactersWardrobe()).append("；");
        }
        if (!text.contains("本段环境与场面氛围") && !text.contains("本段环境")) {
            prefix.append("本段环境与场面氛围：").append(material.environment()).append("；");
        }
        if (!text.contains("构图") && !text.contains("景别") && !text.contains("摄影机")) {
            prefix.append("构图与摄影机：采用可读的主体构图和30°~60°斜角视角，运镜服务于当前动作和情绪推进；");
        }
        if (!text.contains("清晰度") && !text.contains("景深") && !text.contains("光线")) {
            prefix.append("画面质感：主体清晰，真实光线方向明确，色彩和景深与本集资料保持一致；");
        }
        if (!text.contains("表情") && !text.contains("微表情") && !text.contains("生理")) {
            prefix.append("表情动作与视线：写出可观察的表情生理变化、动作因果、受力和视线落点，不使用空泛情绪词；");
        }
        if (!text.contains("对白") && !text.contains("字幕") && !text.contains("声音")) {
            prefix.append("对白字幕与声音：有对白时使用中文双引号并加字幕，声音节奏服从动作和情绪；");
        }
        return stripInternalContinuity(prefix.append(text).toString());
    }
    private static String stripInternalContinuity(String text) {
        if (text == null) return "";
        return text.replaceAll("同上一段一致[。；;，,]?", "")
                .replaceAll("同上一集一致[。；;，,]?", "")
                .replaceAll("上一段结尾连续性参考：.*?(?=；本段|$)", "")
                .replaceAll("本段保持动作状态、视线落点、光源方向和180度动作轴线连续[。；;，,]?", "")
                .replaceAll("\\s+", " ").replaceAll("；{2,}", "；").trim();
    }
    private static String safe(String text) { return text == null || text.isBlank() ? "（上一集暂无正文）" : text; }
    private MyScriptRepository.Episode previousEpisode(MyScriptRepository.Episode episode) {
        return repository.listEpisodes(episode.projectId()).stream()
                .filter(item -> item.number() < episode.number())
                .max(java.util.Comparator.comparingInt(MyScriptRepository.Episode::number))
                .orElse(null);
    }
    private record PlannedSegment(String text, int durationSeconds) {}
    private record ReplicationPlan(EpisodeMaterial material, List<PlannedSegment> segments) {}
    public record EpisodeMaterial(String charactersWardrobe, String environment, String plot, String continuity) {
        String asText() { return "人物与装束：" + charactersWardrobe + "\n环境与氛围：" + environment + "\n主要剧情：" + plot + "\n连续性：" + continuity; }
    }
    public record ReplicationView(EpisodeMaterial episodeMaterial, List<SegmentView> segments) {}
    private EpisodeMaterial materialFor(MyScriptRepository.Episode episode) {
        return repository.findReplicationMaterial(episode.id()).map(this::parseMaterial).orElseGet(() -> fallbackMaterial(episode));
    }
    private ReplicationView replicationView(UUID episodeId, List<MyScriptRepository.Segment> segments) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        EpisodeMaterial material = materialFor(episode);
        if (repository.findReplicationMaterial(episodeId).isEmpty()) saveMaterial(episodeId, material);
        return new ReplicationView(material, segments.stream().map(this::segmentView).toList());
    }
    private void saveMaterial(UUID episodeId, EpisodeMaterial material) {
        try { repository.saveReplicationMaterial(episodeId, mapper.writeValueAsString(material)); } catch (Exception error) { throw new IllegalStateException("保存本集复刻资料失败", error); }
    }
    private EpisodeMaterial parseMaterial(String json) { try { return sanitizeMaterial(mapper.readValue(json, EpisodeMaterial.class)); } catch (Exception error) { return new EpisodeMaterial("本集人物装束按参考资产锁定", "本集环境按剧本设定锁定", "", "承接上一集结尾"); } }
    private static EpisodeMaterial sanitizeMaterial(EpisodeMaterial material) {
        if (material == null) return new EpisodeMaterial("本集人物装束按参考资产锁定", "本集环境按剧本设定锁定", "", "承接上一集结尾");
        return new EpisodeMaterial(stripInternalContinuity(material.charactersWardrobe()), stripInternalContinuity(material.environment()), stripInternalContinuity(material.plot()), stripInternalContinuity(material.continuity()));
    }
    private EpisodeMaterial fallbackMaterial(MyScriptRepository.Episode episode) { return new EpisodeMaterial("本集出场人物及服装装束按剧本设定和上传参考资产锁定，保持已建立的资产状态", "本集环境、空间锚点、光线与氛围按剧本设定锁定，保持已建立的空间和光源状态", episode.content(), "承接上一集结尾，保持人物资产、环境光源、色彩和180度轴线连续"); }

    private void writeNextEpisode(MyScriptRepository.Project project, List<MyScriptRepository.Episode> previous, MyScriptRepository.Episode episode, String apiKey) {
        long startedNanos = System.nanoTime();
        MyScriptRepository.Prompt prompt = null;
        try {
            updateEpisode(episode, "RUNNING", "正在调用千问续写", null, null);
            LOGGER.info("剧本续写后台任务开始 projectId={} episodeId={} episode={} previousEpisodes={}",
                    project.id(), episode.id(), episode.number(), previous.size());
            String last = previous.isEmpty() ? "无" : previous.get(previous.size() - 1).content();
            String system = prompts.episodeInstructions("R2", "抖音", "9:16")
                    + "\n你现在只创作第" + episode.number() + "集。必须承接已给剧本设定和上一集结尾，不要重复剧本设定；输出以【第" + episode.number() + "集】开始，给出完整可拍摄内容，按每段不超过15秒的节拍分段。";
            String user = "【剧本设定】\n" + project.settings() + "\n【上一集】\n" + last;
            prompt = newPrompt(episode.id(), "SYSTEM", "系统推演", null, system, user);
            repository.savePrompt(prompt);
            prompt = updatePrompt(prompt, "RUNNING", null, null);
            String content = callQwen(system, user, apiKey);
            content = content.replaceFirst("(?s)^.*?【第\\s*" + episode.number() + "\\s*集[^】]*】", "").trim();
            if (content.isBlank()) throw new IllegalStateException("千问未返回第" + episode.number() + "集正文");
            updateEpisode(episode, "SUCCESS", "续写完成", content, null);
            updatePrompt(prompt, "SUCCESS", content, null);
            LOGGER.info("剧本续写完成 projectId={} episodeId={} episode={} durationMs={} contentChars={}",
                    project.id(), episode.id(), episode.number(), elapsedMillis(startedNanos), content.length());
        } catch (Exception exception) {
            LOGGER.error("剧本续写失败 projectId={} episodeId={} episode={} durationMs={} reason={}",
                    project.id(), episode.id(), episode.number(), elapsedMillis(startedNanos), rootMessage(exception), exception);
            updateEpisode(episode, "FAILED", "续写失败，可再次点击再来一集", null, rootMessage(exception));
            if (prompt != null) updatePrompt(prompt, "FAILED", null, rootMessage(exception));
        }
    }

    private String callQwen(String system, String user, String apiKey) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        body.put("stream", false);
        if (properties.isThinkingEnabled()) body.put("enable_thinking", true);
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
    private EpisodeView episodeView(MyScriptRepository.Episode e) { return new EpisodeView(e.id(), e.projectId(), e.number(), e.title(), e.content(), e.status(), e.message(), e.error(), repository.listPrompts(e.id()).stream().map(this::promptView).toList(), e.createdAt(), e.updatedAt()); }
    private PromptView promptView(MyScriptRepository.Prompt p) { return new PromptView(p.id(), p.episodeId(), p.version(), p.sourceType(), p.sourceLabel(), p.idea(), p.promptText(), p.resultContent(), p.status(), p.error(), p.createdAt(), p.updatedAt()); }
    private MyScriptRepository.Prompt newPrompt(UUID episodeId, String sourceType, String sourceLabel, String idea, String system, String user) {
        int version = repository.listPrompts(episodeId).stream().mapToInt(MyScriptRepository.Prompt::version).max().orElse(0) + 1;
        Instant now = Instant.now();
        return new MyScriptRepository.Prompt(UUID.randomUUID(), episodeId, version, sourceType, sourceLabel, idea, "【系统提示词】\n" + system + "\n【用户提示词】\n" + user, null, "QUEUED", null, now, now);
    }
    private MyScriptRepository.Prompt updatePrompt(MyScriptRepository.Prompt original, String status, String result, String error) {
        MyScriptRepository.Prompt changed = new MyScriptRepository.Prompt(original.id(), original.episodeId(), original.version(), original.sourceType(), original.sourceLabel(), original.idea(), original.promptText(), result == null ? original.resultContent() : result, status, error, original.createdAt(), Instant.now());
        repository.savePrompt(changed); return changed;
    }
    private SegmentView segmentView(MyScriptRepository.Segment s) { return new SegmentView(s.id(), s.episodeId(), s.number(), s.content(), s.durationSeconds(), s.status(), s.comfyTaskId(), s.error(), s.createdAt(), s.updatedAt()); }
    private MyScriptRepository.Project requireProject(UUID id) { return repository.findProject(id).orElseThrow(() -> new IllegalArgumentException("剧本不存在")); }
    private MyScriptRepository.Episode requireEpisode(UUID id) { return repository.findEpisode(id).orElseThrow(() -> new IllegalArgumentException("剧集不存在")); }
    private MyScriptRepository.Segment requireSegment(UUID id) { return repository.findSegment(id).orElseThrow(() -> new IllegalArgumentException("复刻分段不存在")); }
    private void updateEpisode(MyScriptRepository.Episode original, String status, String message, String content, String error) { repository.saveEpisode(new MyScriptRepository.Episode(original.id(), original.projectId(), original.number(), original.title(), content == null ? original.content() : content, status, message, error, original.createdAt(), Instant.now())); }
    private static ParsedScript parseInitial(String result, String source) { Matcher title = TITLE_MARKER.matcher(result); String name = title.find() ? title.group(1).trim() : fallbackTitle(source); Matcher settings = SETTINGS_MARKER.matcher(result); String set = settings.find() ? settings.group(1).trim() : result.trim(); return new ParsedScript(name, set, ""); }
    private static String fallbackTitle(String source) { String cleaned = source == null ? "未命名剧本" : source.replaceAll("\\s+", " ").trim(); return cleaned.isBlank() ? "未命名剧本" : cleaned.substring(0, Math.min(28, cleaned.length())); }
    private static List<String> splitEpisode(String content) { List<String> units = new ArrayList<>(); for (String piece : content.split("(?=【(?:场景|段落|镜头|第?\\d+段)|\\n\\s*\\n)")) { String trimmed = piece.trim(); if (!trimmed.isBlank()) units.add(trimmed); } if (units.size() >= 2) return units.size() > MAX_REPLICATION_SEGMENTS ? units.subList(0, MAX_REPLICATION_SEGMENTS) : units; List<String> chunks = new ArrayList<>(); StringBuilder current = new StringBuilder(); for (String sentence : content.split("(?<=[。！？!?])")) { if (current.length() + sentence.length() > 420 && !current.isEmpty()) { chunks.add(current.toString().trim()); current.setLength(0); } current.append(sentence); } if (!current.isEmpty()) chunks.add(current.toString().trim()); return chunks.isEmpty() ? List.of(content) : chunks.size() > MAX_REPLICATION_SEGMENTS ? chunks.subList(0, MAX_REPLICATION_SEGMENTS) : chunks; }
    private static String extractText(JsonNode response) { if (response == null) return null; JsonNode node = response.at("/choices/0/message/content"); if (node.isTextual()) return node.asText(); node = response.at("/output/0/content/0/text"); if (node.isTextual()) return node.asText(); node = response.at("/output/text"); return node.isTextual() ? node.asText() : null; }
    private static long elapsedMillis(long startedNanos) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos); }
    private static String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.toString() : current.getMessage(); }
    private record ParsedScript(String title, String settings, String firstEpisode) {}
    public record ProjectView(UUID id, String title, String settings, List<EpisodeView> episodes, Instant createdAt, Instant updatedAt) {}
    public record EpisodeView(UUID id, UUID projectId, int number, String title, String content, String status, String message, String error, List<PromptView> prompts, Instant createdAt, Instant updatedAt) {}
    public record PromptView(UUID id, UUID episodeId, int version, String sourceType, String sourceLabel, String idea, String promptText, String resultContent, String status, String error, Instant createdAt, Instant updatedAt) {}
    public record SegmentView(UUID id, UUID episodeId, int number, String content, int durationSeconds, String status, UUID comfyTaskId, String error, Instant createdAt, Instant updatedAt) {}
    public record CharacterView(UUID id, UUID projectId, String characterName, String roleLevel, String anchor, String imageSourcesJson, int sortOrder, Instant createdAt, Instant updatedAt) {}
    public record CharacterRequest(String characterName, String roleLevel, String anchor, String imageSourcesJson) {}
    private CharacterView characterView(MyScriptRepository.CharacterAsset a) { return new CharacterView(a.id(), a.projectId(), a.characterName(), a.roleLevel(), a.anchor(), a.imageSourcesJson(), a.sortOrder(), a.createdAt(), a.updatedAt()); }
}
