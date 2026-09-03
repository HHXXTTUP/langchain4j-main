package dev.learning.fashionagent.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.comfyui.ComfyUiVideoGenerationService;
import dev.learning.fashionagent.comfyui.ComfyUiVideoView;
import dev.learning.fashionagent.config.GeminiProperties;
import dev.learning.fashionagent.config.GptImageProperties;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.service.GeminiTextClient;
import dev.learning.fashionagent.service.GptImageClient;
import dev.learning.fashionagent.service.AuditRedrawService;
import dev.learning.fashionagent.director.ShortDramaDirectorPromptLibrary;
import java.io.IOException;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;

@Service
public class MyScriptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyScriptService.class);
    private static final int MAX_REPLICATION_SEGMENT_SECONDS = 15;
    private static final int MAX_REPLICATION_SEGMENTS = 10;
    private static final Pattern EPISODE_MARKER = Pattern.compile("(?m)^【?第\\s*(\\d+)\\s*集[^】]*】?\\s*$");
    private static final Pattern TITLE_MARKER = Pattern.compile("(?m)^【剧本名称】\\s*(.+)$");
    private static final Pattern SETTINGS_MARKER = Pattern.compile("(?s)【剧本设定】\\s*(.*?)(?=【第\\s*1\\s*集|$)");
    private final MyScriptRepository repository;
    private final GeminiProperties geminiProperties;
    private final GptImageProperties gptImageProperties;
    private final RunningHubProperties runningHubProperties;
    private final GeminiTextClient geminiClient;
    private final GptImageClient gptImageClient;
    private final ShortDramaDirectorPromptLibrary prompts;
    private final ComfyUiVideoGenerationService comfy;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final Map<UUID, Object> replicationLocks = new ConcurrentHashMap<>();

    public MyScriptService(MyScriptRepository repository, GeminiProperties geminiProperties, GptImageProperties gptImageProperties,
                           RunningHubProperties runningHubProperties,
                           GeminiTextClient geminiClient, GptImageClient gptImageClient,
                           ShortDramaDirectorPromptLibrary prompts, ComfyUiVideoGenerationService comfy,
                           ObjectMapper mapper, @Qualifier("storyVideoExecutor") Executor executor) {
        this.repository = repository; this.geminiProperties = geminiProperties; this.gptImageProperties = gptImageProperties; this.runningHubProperties = runningHubProperties;
        this.geminiClient = geminiClient; this.gptImageClient = gptImageClient; this.prompts = prompts;
        this.comfy = comfy; this.mapper = mapper; this.executor = executor;
    }

    public void archiveInitial(UUID sourceJobId, String sourceText, String result, String tier, String platform, String ratio) {
        if (result == null || result.isBlank()) return;
        List<MyScriptRepository.Project> existing = repository.listProjects();
        if (existing.stream().anyMatch(project -> project.sourceJobId().equals(sourceJobId))) return;
        ParsedScript parsed = parseInitial(result, sourceText);
        Instant now = Instant.now();
        MyScriptRepository.Project project = new MyScriptRepository.Project(UUID.randomUUID(), sourceJobId, parsed.title(), parsed.settings(), now, now);
        repository.saveProject(project);
        syncArtifactsQuietly(project.id());
        LOGGER.info("短剧导演剧本设定已归档，等待用户开始剧情推演 projectId={} sourceJobId={} title={}", project.id(), sourceJobId, project.title());
    }

    public List<ProjectView> list() { return repository.listProjects().stream().map(this::view).toList(); }
    public ProjectView get(UUID id) { return view(requireProject(id)); }
    public EpisodeView episode(UUID id) { return episodeView(requireEpisode(id)); }
    public List<SegmentView> segments(UUID episodeId) { return repository.listSegments(episodeId).stream().map(this::segmentView).toList(); }
    public List<CharacterView> characters(UUID projectId) { return repository.listCharacterAssets(projectId).stream().map(this::characterView).toList(); }
    public List<EpisodeAssetView> episodeAssets(UUID episodeId) { requireEpisode(episodeId); return repository.listEpisodeAssets(episodeId).stream().map(this::episodeAssetView).toList(); }
    public CharacterView generateCharacter(UUID projectId, String characterName, String prompt) {
        MyScriptRepository.Project project = requireProject(projectId);
        if (characterName == null || characterName.isBlank()) throw new IllegalArgumentException("人物名称不能为空");
        String name = characterName.trim();
        String basePrompt = (prompt == null || prompt.isBlank())
                ? "根据完整剧本设定设计人物的稳定外貌、年龄感、发型、体型和基础服装；完整剧本设定如下：" + project.settings()
                : prompt.trim();
        basePrompt += "；只生成“" + name + "”这一名人物；人物名称“" + name
                + "”清晰写在图片下方；纯白背景，正面全身站立，影视级写实质感，不要其他文字、道具或环境。";
        try {
            Path output = scriptSettingsDirectory(project).resolve("基础人物图").resolve(safeFileName(name) + ".png").toAbsolutePath().normalize();
            gptImageClient.generate(basePrompt, output);
            String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(output));
            Instant now = Instant.now();
            String imagesJson = mapper.writeValueAsString(List.of(dataUrl));
            MyScriptRepository.CharacterAsset asset = new MyScriptRepository.CharacterAsset(UUID.nameUUIDFromBytes((projectId + ":" + name).getBytes()), projectId, name, "A", name, imagesJson, repository.listCharacterAssets(projectId).size(), now, now);
            repository.saveCharacterAsset(asset);
            syncArtifactsQuietly(projectId);
            return characterView(asset);
        } catch (Exception error) { throw new IllegalStateException("生成剧本人物图失败：" + rootMessage(error), error); }
    }

    /** Plans all characters once from the complete script setting, then renders each base asset. */
    public List<CharacterView> generateAllCharacters(UUID projectId) {
        MyScriptRepository.Project project = requireProject(projectId);
        String apiKey = geminiProperties.requiredApiKey();
        List<CharacterPrompt> plans = new ArrayList<>();
        try {
            String system = "你是短剧人物资产设计师。根据完整剧本设定提取所有有名字或明确身份的主要人物，结合短剧导演Skill生成基础人物图提示词。只返回JSON数组，不要Markdown：[{\"name\":\"人物名\",\"prompt\":\"纯白背景、正面全身、清晰可辨的人物外貌与基础服装设定，图片底部写人物名称，不要其他文字\"}]。不要输出群众、镜头、剧情段落或环境；人物名必须是剧本中真实出现的名称，不要使用“男主、女主、角色1”这类占位名。";
            String raw = extractText(geminiClient.call("我的剧本/基础人物资产规划", system,
                    "【完整剧本设定】\n" + project.settings(), apiKey));
            JsonNode node = mapper.readTree(raw.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim());
            if (node.isArray()) for (JsonNode item : node) {
                String name = item.path("name").asText("").trim();
                String prompt = item.path("prompt").asText("").trim();
                if (!name.isBlank() && !isPlaceholderCharacter(name)) plans.add(new CharacterPrompt(name, prompt));
            }
        } catch (Exception error) {
            LOGGER.warn("基础人物资产规划失败，使用剧本设定中的人物行回退 projectId={} reason={}", projectId, rootMessage(error));
        }
        if (plans.isEmpty()) for (String name : extractCharacterNames(project.settings())) plans.add(new CharacterPrompt(name, ""));
        if (plans.isEmpty()) throw new IllegalStateException("未能从剧本设定识别出人物");
        List<CharacterView> result = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (int index = 0; index < plans.size(); index++) {
            CharacterPrompt plan = plans.get(index);
            try {
                result.add(generateCharacter(projectId, plan.name(), plan.prompt()));
            } catch (Exception error) {
                failed.add(plan.name());
                // Keep a named placeholder so a failed character remains directly retryable in the UI.
                boolean hasImage = repository.listCharacterAssets(projectId).stream()
                        .filter(asset -> asset.characterName().equals(plan.name()))
                        .anyMatch(asset -> asset.imageSourcesJson() != null
                                && !asset.imageSourcesJson().isBlank()
                                && !"[]".equals(asset.imageSourcesJson().trim()));
                if (!hasImage) {
                    Instant now = Instant.now();
                    repository.saveCharacterAsset(new MyScriptRepository.CharacterAsset(
                            UUID.nameUUIDFromBytes((projectId + ":" + plan.name()).getBytes()), projectId,
                            plan.name(), "A", plan.name(), "[]", index, now, now));
                }
                LOGGER.error("批量生成基础人物图单个角色失败 projectId={} character={} reason={}", projectId, plan.name(), rootMessage(error), error);
            }
        }
        LOGGER.info("批量生成基础人物图完成 projectId={} successCount={} failedCharacters={}", projectId, result.size(), failed);
        if (result.isEmpty()) throw new IllegalStateException("基础人物图生成失败：所有人物请求均未成功");
        // Include named placeholders for failed characters so the caller can retry them immediately.
        return repository.listCharacterAssets(projectId).stream().map(this::characterView).toList();
    }

    public String generateEpisodeCharacter(UUID episodeId, String characterName, String wardrobePrompt) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        MyScriptRepository.Project project = requireProject(episode.projectId());
        MyScriptRepository.CharacterAsset asset = repository.listCharacterAssets(project.id()).stream()
                .filter(item -> item.characterName().equals(characterName == null ? "" : characterName.trim())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请先生成或上传该人物的基础图"));
        try {
            JsonNode sources = mapper.readTree(asset.imageSourcesJson());
            String first = sources.isArray() && !sources.isEmpty() ? sources.get(0).asText() : "";
            Path base = decodeImageSource(first, episodeId + "-" + safeFileName(asset.characterName()) + "-base");
            Path output = scriptEpisodeDirectory(project, episode.number()).resolve("复刻").resolve("人物资产").resolve(safeFileName(asset.characterName()) + ".png").toAbsolutePath().normalize();
            String prompt = AuditRedrawService.auditPromptFor(asset.characterName(), wardrobePrompt);
            gptImageClient.edit(List.of(base), prompt, output, gptImageProperties.requiredApiKey(), "2048x1152");
            String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(output));
            Instant now = Instant.now();
            repository.saveEpisodeAsset(new MyScriptRepository.EpisodeAsset(
                    UUID.nameUUIDFromBytes((episodeId + ":CHARACTER:" + asset.characterName()).getBytes()), episodeId,
                    "CHARACTER", asset.characterName(), prompt, mapper.writeValueAsString(List.of(image)), now, now));
            syncArtifactsQuietly(project.id());
            return image;
        } catch (Exception error) { throw new IllegalStateException("生成剧集人物图失败：" + rootMessage(error), error); }
    }

    public String generateEpisodeEnvironment(UUID episodeId, String prompt) {
        MyScriptRepository.Episode episode = requireEpisode(episodeId);
        MyScriptRepository.Project project = requireProject(episode.projectId());
        try {
            Path output = scriptEpisodeDirectory(project, episode.number()).resolve("复刻").resolve("环境").resolve("environment.png").toAbsolutePath().normalize();
            String environment = prompt == null || prompt.isBlank() ? "根据本集环境描述生成纯净场景参考图" : prompt.trim();
            String finalPrompt = environment + "；只生成空场景环境参考图，不出现任何人物、人体、脸部、手脚、服装、角色或生物，不添加文字。";
            gptImageClient.generate(finalPrompt, output);
            String image = "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(output));
            Instant now = Instant.now();
            repository.saveEpisodeAsset(new MyScriptRepository.EpisodeAsset(
                    UUID.nameUUIDFromBytes((episodeId + ":ENVIRONMENT:environment").getBytes()), episodeId,
                    "ENVIRONMENT", "environment", finalPrompt, mapper.writeValueAsString(List.of(image)), now, now));
            syncArtifactsQuietly(project.id());
            return image;
        } catch (Exception error) { throw new IllegalStateException("生成剧集环境图失败：" + rootMessage(error), error); }
    }

    private Path decodeImageSource(String source, String stem) throws Exception {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("人物尚未配置基础图");
        if (!source.startsWith("data:")) return Path.of(source);
        int comma = source.indexOf(','); if (comma < 0) throw new IllegalArgumentException("人物基础图格式无效");
        Path file = runningHubProperties.getGeneratedDirectory().toAbsolutePath().normalize()
                .resolve("gpt-images").resolve("input").resolve(stem + ".png");
        Files.createDirectories(file.getParent()); Files.write(file, Base64.getDecoder().decode(source.substring(comma + 1))); return file;
    }
    public void saveCharacters(UUID projectId, List<CharacterRequest> requests) {
        requireProject(projectId); if (requests == null) return; Instant now = Instant.now();
        for (int i = 0; i < requests.size(); i++) { CharacterRequest r = requests.get(i); if (r == null || r.characterName() == null || r.characterName().isBlank()) continue;
            repository.saveCharacterAsset(new MyScriptRepository.CharacterAsset(UUID.nameUUIDFromBytes((projectId + ":" + r.characterName().trim()).getBytes()), projectId, r.characterName().trim(), r.roleLevel(), r.anchor(), r.imageSourcesJson() == null ? "[]" : r.imageSourcesJson(), i, now, now)); }
        syncArtifactsQuietly(projectId);
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
        String apiKey = geminiProperties.requiredApiKey();
        executor.execute(() -> rewriteEpisodeInBackground(project, episode, idea.trim(), basePrompt, apiKey));
        return episodeView(queued);
    }

    private void rewriteEpisodeInBackground(MyScriptRepository.Project project, MyScriptRepository.Episode original, String idea, MyScriptRepository.Prompt basePrompt, String apiKey) {
        long startedNanos = System.nanoTime();
        MyScriptRepository.Prompt prompt = null;
        try {
            updateEpisode(original, "RUNNING", "正在结合创作想法调用 Gemini 重写", null, null);
            MyScriptRepository.Episode previous = previousEpisode(original);
            String previousText = previous == null ? "无上一集" : safe(previous.content());
            String system = prompts.episodeInstructions("R2", "抖音", "9:16")
                    + "\n现在重写第" + original.number() + "集。只输出这一集通俗易懂的故事正文，不要输出剧本设定、解释、分析、分镜、镜头、运镜、时长或Markdown。必须保留剧本设定中的人物关系、世界观、风格基调和已确定的连续性；根据用户重写想法调整冲突、节奏、动作、对白和结尾钩子。不要把视频制作术语写进正文，不使用文言文或重复段落；对白只用中文双引号标记。输出以【第" + original.number() + "集】开始。";
            String user = "【剧本设定】\n" + project.settings()
                    + "\n【上一集正文，用于衔接】\n" + previousText
                    + "\n【当前第" + original.number() + "集原稿】\n" + safe(original.content())
                    + (basePrompt == null ? "" : "\n【参考历史提示词版本" + basePrompt.version() + "】\n" + safe(basePrompt.promptText()))
                    + "\n【用户对本集的重写想法】\n" + idea;
            prompt = newPrompt(original.id(), "USER_REWRITE", basePrompt == null ? "用户重写" : "基于历史提示词重写", idea, system, user);
            repository.savePrompt(prompt);
            prompt = updatePrompt(prompt, "RUNNING", null, null);
            String content = extractText(geminiClient.call("我的剧本/重写", system, user, apiKey));
            content = stripEpisodeHeading(content, original.number());
            if (content.isBlank()) throw new IllegalStateException("Gemini 未返回重写后的第" + original.number() + "集正文");
            updateEpisode(original, "SUCCESS", "本集重写完成", content, null);
            updatePrompt(prompt, "SUCCESS", content, null);
            repository.deleteSegments(original.id());
            repository.deleteReplicationMaterial(original.id());
            syncArtifactsQuietly(project.id());
            LOGGER.info("剧本本集重写完成 projectId={} episodeId={} episode={} durationMs={} contentChars={}", project.id(), original.id(), original.number(), elapsedMillis(startedNanos), content.length());
        } catch (Exception exception) {
            LOGGER.error("剧本本集重写失败 projectId={} episodeId={} episode={} durationMs={} reason={}", project.id(), original.id(), original.number(), elapsedMillis(startedNanos), rootMessage(exception), exception);
            updateEpisode(original, "FAILED", "重写失败，原内容已保留，可再次提交重写", null, rootMessage(exception));
            if (prompt != null) updatePrompt(prompt, "FAILED", null, rootMessage(exception));
            syncArtifactsQuietly(project.id());
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
        repository.saveSegment(changed); syncArtifactsQuietly(original.episodeId()); return segmentView(changed);
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
        syncArtifactsQuietly(projectId);
        String apiKey = geminiProperties.requiredApiKey();
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
        if (!existing.isEmpty() && (existing.stream().allMatch(this::isLegacySegment) || isRepeatedLegacySet(existing) || isLowQualitySegmentSet(existing) || isUnstructuredCharacterMaterial(episodeId))
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
        syncArtifactsQuietly(episode.projectId());
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
        syncArtifactsQuietly(episode.projectId());
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
        syncArtifactsQuietly(project.id());
        return segmentView(running);
    }

    private String projectSettings(UUID id) { return repository.findProject(id).map(MyScriptRepository.Project::settings).orElse(""); }
    private static String replicationInstructions() {
        return """
                你是资深影视分镜和镜头语言分析师。只允许调用一次 Gemini，必须一次性返回当前集资料和全部段落，严禁为每个段落再次请求模型或输出第二个版本。输出严格为JSON对象，不要Markdown：
                {"episodeMaterial":{"charactersWardrobe":"本集实际出场人物、人物关系、当前服装装束和资产短锚点","environment":"本集实际场景、空间锚点、光线方向、环境压力、色彩、画质、景深和现场氛围","plot":"本集主要剧情、冲突推进、情绪曲线、关键道具和结尾钩子","continuity":"承接上一集的具体动作、视线、光源、轴线和情绪变化"},"segments":[{"text":"一段完整、独立、可直接交给视频模型的中文复刻描述","plotBeat":"本段独立且不可替代的剧情推进","characters":"本段实际出场人物、人物关系、当前服装装束和资产锚点","environment":"本段实际环境、空间锚点和场面氛围","camera":"构图、景别、视角、焦段感和摄影机运镜","visual":"清晰度、真实光线、色彩、景深","performance":"可观察的表情生理变化、动作因果、受力和视线落点","sound":"对白原文、说话人、语气、停顿和现场声音，不提字幕","handoff":"本段结尾如何把动作、视线、光源、轴线和情绪交给下一段","durationSeconds":15}]}
                按当前集真实剧情和镜头边界拆段，不要为了凑固定段数切碎同一场戏：同一时间、同一空间、同一机位和同一组连续动作尽量合并为一个段落，按不超过30秒的动作与对白容量写足；只有发生明确的场景、时间、机位或空间关系变化时才拆开，即使新段较短也允许。通常输出6至10段，但以因果和镜头完整为准，不能复制内容；durationSeconds按视频接口限制填1至15。
                segments[].text严格只包含两个自然段，不得有标题、字段名、冒号标签、分号清单或Markdown。第一自然段只建立一次本段镜头：直接写环境和空间锚点，再写主体构图、景别、视角、焦段感和摄影机运镜，控制在1至2句；这一段不要写人物资料、剧情动作或对白。第二自然段必须紧接第一段开始的动作，禁止再次写地点、环境氛围、构图、景别、焦段、镜头或运镜，也禁止用换一种说法重复第一段的环境。第二自然段按当前集真实剧情顺序写足同一镜头内的连续动作，至少展开5个有先后关系的可见动作或反应，例如接近、试探、受阻、改变策略和产生结果；把实际出场人物真实姓名、外貌、当集服装装束、动作因果、受力、表情生理变化、视线落点、光线如何作用于人物和道具、清晰度、景深、对白（用中文双引号标记）和现场声音融入正文，并写出动作结果；同一镜头下第二段建议260至420个汉字，对白量控制在30秒可说完，不能用空泛形容词凑字数。发生新场景或新机位时，第一段重新建立新的环境和构图，第二段仍只写该新镜头的剧情，不回写旧镜头。
                所有细节必须顺着剧情写，不能拆成“画面质感”“表情动作与视线”“对白与声音”“连续性衔接”等字段。不得在text中出现“本段剧情推进”“本段人物与装束”“本段环境与场面氛围”“构图与摄影机”“画面质感”“表情动作与视线”“对白与声音”“连续性衔接”“同上一段一致”“同上一集一致”等标签或占位语句，也不要写字幕、分镜编号、时间轴、规则解释或空泛形容词。段落分隔必须使用真正的空行，不得输出字面量“\\n”或“nn”。结构化字段plotBeat、characters、environment、camera、visual、performance、sound、handoff仅作隐藏元数据，严禁复制到text。每段必须基于当前集真实剧情，段落之间有因果推进且内容不可重复；人物外貌按上传参考资产1:1复刻，服装按本集资料保持，不自行改变。""";
    }
    private ReplicationPlan planReplication(MyScriptRepository.Episode episode) {
        try {
            String system = replicationInstructions();
            MyScriptRepository.Episode previous = previousEpisode(episode);
            String previousContext = previous == null ? "无上一集，本集建立新的连续性基准" :
                    "【上一集连续性资料】\n" + materialFor(previous).asText() + "\n【上一集结尾正文】\n" + safe(previous.content());
            String user = "【剧本设定】\n" + projectSettings(episode.projectId()) + "\n" + previousContext + "\n【当前集正文：" + episode.title() + "】\n" + episode.content();
            String raw = callGemini(system, user, geminiProperties.requiredApiKey());
            JsonNode array = mapper.readTree(raw.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim());
            JsonNode materialNode = array.path("episodeMaterial");
            JsonNode segmentsNode = array.path("segments");
            if (!segmentsNode.isArray() || segmentsNode.isEmpty()) throw new IllegalStateException("Gemini 复刻规划为空");
            EpisodeMaterial material = sanitizeMaterial(new EpisodeMaterial(materialNode.path("charactersWardrobe").asText("本集人物装束按参考资产锁定"), materialNode.path("environment").asText("本集环境按剧本设定锁定"), materialNode.path("plot").asText(episode.content()), materialNode.path("continuity").asText("承接上一集结尾")));
            List<PlannedSegment> result = new ArrayList<>();
            for (JsonNode item : segmentsNode) {
                String text = completeSegmentContext(cleanVisibleSegment(segmentText(item)), material, item);
                final String plannedText = text;
                if (!plannedText.isBlank() && result.stream().noneMatch(existingPart -> sameText(existingPart.text(), plannedText))) {
                    int duration = item.path("durationSeconds").asInt(MAX_REPLICATION_SEGMENT_SECONDS);
                    result.add(new PlannedSegment(plannedText, Math.max(1, Math.min(MAX_REPLICATION_SEGMENT_SECONDS, duration))));
                }
            }
            if (result.size() >= 2) { LOGGER.info("Gemini 复刻规划完成 episodeId={} segments={} materialChars={}", episode.id(), result.size(), material.asText().length()); return new ReplicationPlan(material, result.size() > MAX_REPLICATION_SEGMENTS ? result.subList(0, MAX_REPLICATION_SEGMENTS) : result); }
        } catch (Exception error) { LOGGER.warn("Gemini 复刻规划失败，使用本地兜底 episodeId={} reason={}", episode.id(), rootMessage(error)); }
        EpisodeMaterial material = fallbackMaterial(episode);
        List<String> parts = mergeFallbackParts(splitEpisode(episode.content()));
        List<PlannedSegment> fallback = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            fallback.add(new PlannedSegment(completeSegmentContext(cleanVisibleSegment(parts.get(index)), material, null), MAX_REPLICATION_SEGMENT_SECONDS));
        }
        return new ReplicationPlan(material, fallback);
    }
    private static String buildGenerationPrompt(String settings, MyScriptRepository.Episode episode, int number, String content, EpisodeMaterial material) {
        String handoff = number == 1 ? "建立本集开场空间" : "承接上一段最后一帧的动作、视线、光线和空间位置";
        return "【仅供视频模型使用的全局设定，不要在画面生成文字】\n" + settings + "\n【本集复刻资料】\n" + material.asText()
                + "\n【当前集实际剧情】\n" + content
                + "\n人物外貌按参考图1:1复刻，当前服装装束必须按本集复刻资料明确保持，不自行设计或改变；保持空间锚点、光源方向、色彩、镜头轴线和时间连续；中景/全景采用30°~60°斜角；" + handoff
                + "；所有对白使用中文双引号标记说话内容；禁止文字水印乱码、额外人物、人物资产漂移、跳轴和突然变更场景。";
    }
    private static String cleanVisibleSegment(String value) {
        if (value == null) return "";
        String cleaned = value.replace("\\n", "\n")
                // Some older responses serialized the paragraph separator as the literal text "nn".
                .replace("nn", "\n\n")
                .replaceAll("(?m)^\\s*[【\\[]?(?:剧本设定|全局不变量|负面约束|连续性承接|时间线与镜头|独立视频段落)[】\\]]?[^\\n]*\\n?", "")
                .replaceAll("(?m)^\\s*(?:段落|分镜|镜头)\\s*\\d+[：:、.]?\\s*", "")
                .replaceAll("(?:本段剧情推进|本段人物与装束|本段出场人物及服装装束|本段环境与场面氛围|本段环境|构图与摄影机|画面质感|表情动作与视线|对白字幕与声音|对白与声音|连续性衔接)[：:]\\s*", "")
                .replaceAll("[ \\t\\f\\r]+", " ")
                .replaceAll("\\n[ \\t]*\\n+", "\n\n").trim();
        return cleaned;
    }
    private static String segmentText(JsonNode item) {
        String text = item.path("text").asText(item.path("prompt").asText("")).trim();
        if (!text.isBlank()) return text;
        String opening = joinNatural(
                item.path("environment").asText(""),
                item.path("camera").asText(""));
        String story = joinNatural(
                item.path("plotBeat").asText(item.path("story").asText("")),
                item.path("characters").asText(item.path("characterContext").asText("")),
                item.path("visual").asText(""),
                item.path("performance").asText(""),
                item.path("sound").asText(""),
                item.path("handoff").asText(""));
        if (opening.isBlank()) return story;
        return story.isBlank() ? opening : opening + "\n\n" + story;
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
        // The two-block protocol needs a real camera setup plus a sufficiently detailed action beat.
        return forbidden > 0 || averageLength < 260;
    }
    private static boolean containsInternalContinuity(String text) {
        if (text == null) return false;
        return text.contains("同上一段一致") || text.contains("同上一集一致") || text.contains("上一段结尾连续性参考") || text.contains("本段保持动作状态");
    }
    private boolean isUnstructuredCharacterMaterial(UUID episodeId) {
        return repository.findReplicationMaterial(episodeId).map(json -> {
            try {
                EpisodeMaterial material = mapper.readValue(json, EpisodeMaterial.class);
                String value = material.charactersWardrobe();
                return value == null || value.isBlank() || !value.matches("(?s).*[^：:，,（(]{1,20}[：:].*");
            } catch (Exception ignored) { return true; }
        }).orElse(false);
    }
    private static String completeSegmentContext(String text, EpisodeMaterial material, JsonNode item) {
        String normalized = stripInternalContinuity(cleanVisibleSegment(text));
        if (normalized.isBlank()) return normalized;
        List<String> blocks = java.util.Arrays.stream(normalized.split("\\n\\s*\\n"))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        if (blocks.size() >= 2) {
            String first = blocks.get(0);
            if (!first.contains("镜头") && !first.contains("构图") && !first.contains("景别")
                    && !first.contains("摄影机") && !first.contains("运镜")) {
                String environment = item == null ? "" : item.path("environment").asText("");
                String camera = item == null ? "" : item.path("camera").asText("");
                if (environment.isBlank() && material != null) environment = material.environment();
                String opening = joinNatural(environment, camera);
                if (!opening.isBlank()) first = opening + "。 " + first;
            }
            int storyStart = 1;
            while (storyStart < blocks.size() - 1 && looksLikeRepeatedCameraOpening(blocks.get(storyStart))) storyStart++;
            String story = String.join(" ", blocks.subList(storyStart, blocks.size())).trim();
            story = stripLeadingRepeatedOpening(story, first);
            return first + "\n\n" + story;
        }
        String environment = item == null ? "" : item.path("environment").asText("");
        String camera = item == null ? "" : item.path("camera").asText("");
        if (environment.isBlank() && material != null) environment = material.environment();
        String opening = joinNatural(environment, camera);
        if (opening.isBlank()) opening = "在当前场景的连续空间中，镜头以中近景保持主体清晰，并沿当前动作方向平稳跟随。";
        return opening + "\n\n" + normalized;
    }
    private static boolean looksLikeRepeatedCameraOpening(String value) {
        if (value == null || value.isBlank() || value.contains("“") || value.contains("”")) return false;
        int hits = 0;
        for (String token : List.of("镜头", "构图", "景别", "焦段", "机位", "运镜", "平移", "推镜", "拉镜", "摇镜", "竖屏")) {
            if (value.contains(token)) hits++;
        }
        return hits >= 3;
    }
    private static String stripLeadingRepeatedOpening(String story, String firstBlock) {
        String remaining = story == null ? "" : story.trim();
        while (!remaining.isBlank()) {
            int end = -1;
            for (int index = 0; index < remaining.length(); index++) {
                char ch = remaining.charAt(index);
                if (ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?') { end = index + 1; break; }
            }
            if (end <= 0) break;
            String sentence = remaining.substring(0, end).trim();
            if (!looksLikeRepeatedCameraOpening(sentence) && !looksLikeRepeatedEnvironmentOpening(sentence, firstBlock)) break;
            remaining = remaining.substring(end).trim();
        }
        return remaining;
    }
    private static boolean looksLikeRepeatedEnvironmentOpening(String sentence, String firstBlock) {
        if (sentence == null || firstBlock == null || sentence.length() > 120 || sentence.contains("“") || sentence.contains("”")) return false;
        long overlap = 0;
        for (int index = 0; index + 1 < sentence.length(); index++) {
            String pair = sentence.substring(index, index + 2);
            if (pair.matches("[\\u4e00-\\u9fff]{2}") && firstBlock.contains(pair)) overlap++;
        }
        if (overlap < 3) return false;
        return !sentence.matches(".*(走|跑|握|抬|转身|回头|看向|望向|拿起|放下|劈|咳|说|问|冲|抓|推|拉|坐下|站起|奔|撞|打开|拔出|挥|扑|扶住|跪|倒下).*" );
    }
    private static String joinNatural(String... values) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String cleaned = value.trim().replaceAll("^[：:；;，,。\\s]+|[；;，,。\\s]+$", "");
            if (cleaned.isBlank()) continue;
            if (joined.length() > 0) joined.append("。 ");
            joined.append(cleaned);
        }
        return joined.toString();
    }
    private static String stripInternalContinuity(String text) {
        if (text == null) return "";
        return text.replaceAll("同上一段一致[。；;，,]?", "")
                .replaceAll("同上一集一致[。；;，,]?", "")
                .replaceAll("上一段结尾连续性参考：.*?(?=；本段|$)", "")
                .replaceAll("本段保持动作状态、视线落点、光源方向和180度动作轴线连续[。；;，,]?", "")
                .replaceAll("对白字幕与声音：", "对白与声音：")
                .replaceAll("加字幕", "")
                .replaceAll("字幕(?:制作)?(?:要求)?[：:，,。；;]?", "")
                .replaceAll("[ \\t\\f\\r]+", " ")
                .replaceAll("\\n[ \\t]*\\n+", "\n\n")
                .replace('；', '。').replace(';', '。')
                .replaceAll("。{2,}", "。").trim();
    }
    private static String safe(String text) { return text == null || text.isBlank() ? "（上一集暂无正文）" : text; }
    private static String safeFileName(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}一-龥_-]", "_");
        return cleaned.isBlank() ? "未命名" : cleaned;
    }
    private static boolean isPlaceholderCharacter(String value) { return value.matches("(?i)(男主|女主|角色|人物|女子|男子)[一二三四五六七八九十0-9]*"); }
    private static List<String> extractCharacterNames(String settings) {
        if (settings == null) return List.of();
        List<String> names = new ArrayList<>();
        Pattern pattern = Pattern.compile("^(?:人物)?[：:]?\\s*([^：:，,（(]{1,20})[：:，,（(]");
        for (String line : settings.split("\\R|；|;")) {
            String value = line.trim().replaceFirst("^[*#\\-\\s]+", "");
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                if (!name.isBlank() && !isPlaceholderCharacter(name) && names.stream().noneMatch(name::equals)) names.add(name);
            }
        }
        return names;
    }
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
        if (repository.findReplicationMaterial(episodeId).isEmpty()) {
            saveMaterial(episodeId, material);
            syncArtifactsQuietly(episode.projectId());
        }
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

    /** Mirrors the database-backed script aggregate into the account's generated directory. */
    private void syncArtifactsQuietly(UUID projectId) {
        try {
            MyScriptRepository.Project project = repository.findProject(projectId).orElse(null);
            if (project == null) return;
            Path root = scriptRoot(project);
            Path settingsDir = scriptSettingsDirectory(project);
            Files.createDirectories(settingsDir);
            writeUtf8(settingsDir.resolve("剧本设定.md"), "# " + project.title() + "\n\n" + safeText(project.settings()));
            for (MyScriptRepository.CharacterAsset asset : repository.listCharacterAssets(project.id())) {
                writeImageSources(asset.imageSourcesJson(), settingsDir.resolve("基础人物图"), safeFileName(asset.characterName()));
            }
            for (MyScriptRepository.Episode episode : repository.listEpisodes(project.id())) {
                Path episodeDir = scriptEpisodeDirectory(project, episode.number());
                Files.createDirectories(episodeDir);
                writeUtf8(episodeDir.resolve("剧情内容.md"), "# " + episode.title() + "\n\n" + safeText(episode.content()));
                for (MyScriptRepository.Prompt prompt : repository.listPrompts(episode.id())) {
                    String label = String.format("%02d-%s.md", prompt.version(), safeFileName(prompt.sourceLabel()));
                    writeUtf8(episodeDir.resolve("提示词").resolve(label), promptFileText(prompt));
                }
                for (MyScriptRepository.EpisodeAsset asset : repository.listEpisodeAssets(episode.id())) {
                    Path assetDir = "ENVIRONMENT".equalsIgnoreCase(asset.assetType())
                            ? episodeDir.resolve("复刻").resolve("环境")
                            : episodeDir.resolve("复刻").resolve("人物资产");
                    writeUtf8(assetDir.resolve(safeFileName(asset.assetName()) + "-提示词.md"), safeText(asset.prompt()));
                    writeImageSources(asset.imageSourcesJson(), assetDir, safeFileName(asset.assetName()));
                }
                List<MyScriptRepository.Segment> segments = repository.listSegments(episode.id());
                if (!segments.isEmpty() || repository.findReplicationMaterial(episode.id()).isPresent()) {
                    Path replicationDir = episodeDir.resolve("复刻");
                    String material = repository.findReplicationMaterial(episode.id()).map(this::materialFileText).orElse("");
                    StringBuilder replication = new StringBuilder("# ").append(episode.title()).append(" 复刻内容\n\n");
                    if (!material.isBlank()) replication.append(material).append("\n\n");
                    for (MyScriptRepository.Segment segment : segments) {
                        replication.append("## 段落").append(segment.number()).append("\n\n")
                                .append(safeText(segment.content())).append("\n\n");
                        writeUtf8(replicationDir.resolve("段落" + segment.number() + ".md"), safeText(segment.content()));
                    }
                    writeUtf8(replicationDir.resolve("复刻内容.md"), replication.toString());
                }
            }
            LOGGER.info("剧本文件资产已同步 projectId={} directory={}", project.id(), root);
        } catch (Exception error) {
            LOGGER.warn("剧本文件资产同步失败 projectId={} reason={}", projectId, rootMessage(error));
        }
    }

    private Path scriptRoot(MyScriptRepository.Project project) {
        return runningHubProperties.getGeneratedDirectory().toAbsolutePath().normalize().resolve(safeFileName(project.title()));
    }
    private Path scriptSettingsDirectory(MyScriptRepository.Project project) { return scriptRoot(project).resolve("剧本设定"); }
    private Path scriptEpisodeDirectory(MyScriptRepository.Project project, int number) { return scriptRoot(project).resolve("第" + number + "集"); }
    private static String safeText(String text) { return text == null ? "" : text; }
    private static String promptFileText(MyScriptRepository.Prompt prompt) {
        return "# 提示词版本 " + prompt.version() + "\n\n来源：" + safeText(prompt.sourceLabel())
                + "\n状态：" + safeText(prompt.status()) + "\n\n" + safeText(prompt.promptText())
                + "\n\n## 生成结果\n\n" + safeText(prompt.resultContent());
    }
    private String materialFileText(String json) {
        try { return mapper.readValue(json, EpisodeMaterial.class).asText(); }
        catch (Exception ignored) { return safeText(json); }
    }
    private static void writeUtf8(Path target, String value) throws IOException {
        Files.createDirectories(target.toAbsolutePath().normalize().getParent());
        Files.writeString(target, value == null ? "" : value, StandardCharsets.UTF_8);
    }
    private static void writeImageSources(String json, Path directory, String prefix) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            if (!node.isArray()) return;
            int index = 0;
            for (JsonNode sourceNode : node) {
                String source = sourceNode.asText("").trim();
                if (source.isBlank()) continue;
                index++;
                if (source.startsWith("data:")) {
                    int comma = source.indexOf(',');
                    if (comma < 0) continue;
                    String meta = source.substring(5, comma).toLowerCase();
                    String extension = meta.contains("jpeg") || meta.contains("jpg") ? ".jpg" : meta.contains("webp") ? ".webp" : ".png";
                    Files.createDirectories(directory);
                    Files.write(directory.resolve(prefix + (index == 1 ? "" : "-" + index) + extension), Base64.getDecoder().decode(source.substring(comma + 1)));
                } else if (source.startsWith("http://") || source.startsWith("https://")) {
                    writeUtf8(directory.resolve(prefix + (index == 1 ? "" : "-" + index) + ".url.txt"), source);
                } else if (source.startsWith("file:")) {
                    Path sourcePath = Path.of(java.net.URI.create(source));
                    if (Files.isRegularFile(sourcePath)) { Files.createDirectories(directory); Files.copy(sourcePath, directory.resolve(prefix + (index == 1 ? "" : "-" + index) + ".png"), StandardCopyOption.REPLACE_EXISTING); }
                } else {
                    Path sourcePath = Path.of(source);
                    if (Files.isRegularFile(sourcePath)) { Files.createDirectories(directory); Files.copy(sourcePath, directory.resolve(prefix + (index == 1 ? "" : "-" + index) + ".png"), StandardCopyOption.REPLACE_EXISTING); }
                }
            }
        } catch (Exception ignored) { }
    }

    private void writeNextEpisode(MyScriptRepository.Project project, List<MyScriptRepository.Episode> previous, MyScriptRepository.Episode episode, String apiKey) {
        long startedNanos = System.nanoTime();
        MyScriptRepository.Prompt prompt = null;
        try {
            updateEpisode(episode, "RUNNING", "正在调用 Gemini 续写", null, null);
            LOGGER.info("剧本续写后台任务开始 projectId={} episodeId={} episode={} previousEpisodes={}",
                    project.id(), episode.id(), episode.number(), previous.size());
            String last = previous.isEmpty() ? "无" : previous.get(previous.size() - 1).content();
            String system = prompts.episodeInstructions("R2", "抖音", "9:16")
                    + "\n你现在只创作第" + episode.number() + "集。必须承接已给剧本设定和上一集结尾，不要重复剧本设定；输出以【第" + episode.number() + "集】开始，使用通俗现代汉语给出完整连贯的故事正文，不要写分镜、镜头、运镜、时长或视频制作说明。对白只用中文双引号标记，不添加字幕要求。";
            String user = "【剧本设定】\n" + project.settings() + "\n【上一集】\n" + last;
            prompt = newPrompt(episode.id(), "SYSTEM", "系统推演", null, system, user);
            repository.savePrompt(prompt);
            prompt = updatePrompt(prompt, "RUNNING", null, null);
            String content = extractText(geminiClient.call("我的剧本/续写", system, user, apiKey));
            content = content.replaceFirst("(?s)^.*?【第\\s*" + episode.number() + "\\s*集[^】]*】", "").trim();
            if (content.isBlank()) throw new IllegalStateException("Gemini 未返回第" + episode.number() + "集正文");
            updateEpisode(episode, "SUCCESS", "续写完成", content, null);
            updatePrompt(prompt, "SUCCESS", content, null);
            syncArtifactsQuietly(project.id());
            LOGGER.info("剧本续写完成 projectId={} episodeId={} episode={} durationMs={} contentChars={}",
                    project.id(), episode.id(), episode.number(), elapsedMillis(startedNanos), content.length());
        } catch (Exception exception) {
            LOGGER.error("剧本续写失败 projectId={} episodeId={} episode={} durationMs={} reason={}",
                    project.id(), episode.id(), episode.number(), elapsedMillis(startedNanos), rootMessage(exception), exception);
            updateEpisode(episode, "FAILED", "续写失败，可再次点击再来一集", null, rootMessage(exception));
            if (prompt != null) updatePrompt(prompt, "FAILED", null, rootMessage(exception));
            syncArtifactsQuietly(project.id());
        }
    }

    private String callGemini(String system, String user, String apiKey) throws java.io.IOException {
        JsonNode response = geminiClient.call("剧本复刻/规划", system, user, apiKey);
        String text = extractText(response);
        if (text == null || text.isBlank()) throw new IllegalStateException("Gemini 未返回复刻规划");
        return text.trim();
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
    private static List<String> mergeFallbackParts(List<String> parts) {
        if (parts.size() <= MAX_REPLICATION_SEGMENTS) return parts;
        int target = Math.min(MAX_REPLICATION_SEGMENTS, Math.max(6, (int) Math.ceil(parts.size() / 2.0)));
        List<String> merged = new ArrayList<>();
        int index = 0;
        for (int bucket = 0; bucket < target && index < parts.size(); bucket++) {
            int remaining = parts.size() - index;
            int bucketsLeft = target - bucket;
            int take = Math.max(1, (int) Math.ceil(remaining / (double) bucketsLeft));
            merged.add(String.join(" ", parts.subList(index, Math.min(parts.size(), index + take))));
            index += take;
        }
        return merged;
    }
    private static String extractText(JsonNode response) { if (response == null) return null; JsonNode node = response.at("/candidates/0/content/parts/0/text"); if (node.isTextual()) return node.asText(); node = response.at("/choices/0/message/content"); if (node.isTextual()) return node.asText(); node = response.at("/output/0/content/0/text"); if (node.isTextual()) return node.asText(); node = response.at("/output/text"); return node.isTextual() ? node.asText() : null; }
    private static long elapsedMillis(long startedNanos) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos); }
    private static String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.toString() : current.getMessage(); }
    private record ParsedScript(String title, String settings, String firstEpisode) {}
    public record ProjectView(UUID id, String title, String settings, List<EpisodeView> episodes, Instant createdAt, Instant updatedAt) {}
    public record EpisodeView(UUID id, UUID projectId, int number, String title, String content, String status, String message, String error, List<PromptView> prompts, Instant createdAt, Instant updatedAt) {}
    public record PromptView(UUID id, UUID episodeId, int version, String sourceType, String sourceLabel, String idea, String promptText, String resultContent, String status, String error, Instant createdAt, Instant updatedAt) {}
    public record SegmentView(UUID id, UUID episodeId, int number, String content, int durationSeconds, String status, UUID comfyTaskId, String error, Instant createdAt, Instant updatedAt) {}
    public record CharacterView(UUID id, UUID projectId, String characterName, String roleLevel, String anchor, String imageSourcesJson, int sortOrder, Instant createdAt, Instant updatedAt) {}
    public record EpisodeAssetView(UUID id, UUID episodeId, String assetType, String assetName, String prompt, String imageSourcesJson, Instant createdAt, Instant updatedAt) {}
    public record CharacterRequest(String characterName, String roleLevel, String anchor, String imageSourcesJson) {}
    private record CharacterPrompt(String name, String prompt) {}
    private CharacterView characterView(MyScriptRepository.CharacterAsset a) { return new CharacterView(a.id(), a.projectId(), a.characterName(), a.roleLevel(), a.anchor(), a.imageSourcesJson(), a.sortOrder(), a.createdAt(), a.updatedAt()); }
    private EpisodeAssetView episodeAssetView(MyScriptRepository.EpisodeAsset a) { return new EpisodeAssetView(a.id(), a.episodeId(), a.assetType(), a.assetName(), a.prompt(), a.imageSourcesJson(), a.createdAt(), a.updatedAt()); }
}
