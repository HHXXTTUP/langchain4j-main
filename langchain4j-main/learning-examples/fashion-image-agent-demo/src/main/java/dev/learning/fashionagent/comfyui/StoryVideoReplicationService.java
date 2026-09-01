package dev.learning.fashionagent.comfyui;


import dev.learning.fashionagent.ai.FashionAiCallExecutor;
import dev.learning.fashionagent.ai.FashionAiConfiguration;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.VisionImageEncoder;
import dev.learning.fashionagent.config.StoryVideoProperties;
import dev.learning.fashionagent.video.VideoMediaProcessor;
import dev.learning.fashionagent.video.VideoMediaProcessor.VideoProbe;
import dev.learning.fashionagent.video.SnapAnyVideoImportService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.AiServices;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoryVideoReplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryVideoReplicationService.class);
    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();
    private final VideoMediaProcessor media;
    private final ComfyUiVideoGenerationService video;
    private final Executor executor;
    private final FashionAiCallExecutor aiCalls;
    private final FashionAiProperties aiProperties;
    private final Map<String, StoryVideoAnalysisAgent> agents = new ConcurrentHashMap<>();
    private final VisionImageEncoder encoder;
    private final Path root;
    private final StoryVideoProperties properties;
    private final SnapAnyVideoImportService snapAny;

    public StoryVideoReplicationService(
            VideoMediaProcessor media,
            ComfyUiVideoGenerationService video,
            FashionAiProperties aiProperties,
            VisionImageEncoder encoder,
            StoryVideoProperties properties,
            SnapAnyVideoImportService snapAny,
            @Qualifier("storyVideoExecutor") Executor executor) {
        this.media = media;
        this.video = video;
        this.executor = executor;
        this.aiCalls = new FashionAiCallExecutor(aiProperties);
        this.aiProperties = aiProperties;
        this.encoder = encoder;
        this.properties = properties;
        this.snapAny = snapAny;
        this.root = Path.of("generated", "story-video-replications").toAbsolutePath().normalize();
    }

    public StoryVideoReplicationView resolveUrl(String address) {
        if (address == null || address.isBlank()) throw new IllegalArgumentException("请输入视频地址");
        UUID id = UUID.randomUUID();
        Path work = root.resolve(id.toString());
        try { Files.createDirectories(work); }
        catch (IOException e) { throw new IllegalStateException("无法创建视频复刻任务目录", e); }
        Task task = new Task(id, "地址视频", null, work);
        task.status = "DOWNLOADING";
        task.message = "正在通过 SnapAny 解析并下载视频到 " + properties.getReplicationDirectory();
        tasks.put(id, task);
        persist(task);
        executor.execute(() -> resolveUrlAsync(task, address.trim()));
        return task.view();
    }

    public StoryVideoReplicationView startAnalysis(UUID id) {
        Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("故事视频任务不存在: " + id);
        if (!"DOWNLOADED".equals(task.status)) throw new IllegalStateException("视频尚未下载完成");
        task.status = "QUEUED";
        task.message = "视频已进入分析队列";
        persist(task);
        executor.execute(() -> analyzeAsync(task));
        return task.view();
    }

    private void resolveUrlAsync(Task task, String address) {
        try {
            Path downloaded = snapAny.downloadFirst(address, properties.getReplicationDirectory());
            task.source = downloaded;
            task.sourceFileName = downloaded.getFileName().toString();
            task.status = "DOWNLOADED";
            task.message = "视频下载完成，点击分析视频";
            persist(task);
        } catch (Exception e) {
            LOGGER.error("影视复刻地址解析失败 id={}", task.id, e);
            task.status = "FAILED";
            task.error = rootMessage(e);
            task.message = "视频地址解析或下载失败";
            persist(task);
        }
    }

    public StoryVideoReplicationView analyze(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("请上传视频文件");
        String originalName = upload.getOriginalFilename() == null ? "source.mp4" : upload.getOriginalFilename();
        String lower = originalName.toLowerCase();
        if (!(lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".webm"))) {
            throw new IllegalArgumentException("仅支持 MP4、MOV、MKV 或 WEBM 视频");
        }
        UUID id = UUID.randomUUID();
        Path work = root.resolve(id.toString());
        try {
            Files.createDirectories(work);
            Path source = work.resolve("source" + extension(originalName));
            upload.transferTo(source);
            return enqueueAnalysis(id, originalName, source, work);
        } catch (IOException e) {
            deleteDirectory(work);
            throw new IllegalStateException("无法保存上传视频", e);
        }
    }

    public StoryVideoReplicationView analyzeDownloaded(Path source) {
        if (source == null || !Files.isRegularFile(source)) throw new IllegalArgumentException("下载的视频文件不存在");
        String originalName = source.getFileName().toString();
        UUID id = UUID.randomUUID();
        Path work = root.resolve(id.toString());
        try {
            Files.createDirectories(work);
            Path stored = work.resolve("source" + extension(originalName));
            Files.copy(source, stored, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return enqueueAnalysis(id, originalName, stored, work);
        } catch (IOException e) {
            deleteDirectory(work);
            throw new IllegalStateException("无法保存已下载视频", e);
        }
    }

    private StoryVideoReplicationView enqueueAnalysis(UUID id, String originalName, Path source, Path work) {
        Task task = new Task(id, originalName, source, work);
        tasks.put(id, task);
        persist(task);
        executor.execute(() -> analyzeAsync(task));
        return task.view();
    }

    public List<StoryVideoReplicationView> list() {
        return tasks.values().stream().map(Task::view)
                .sorted(Comparator.comparing(StoryVideoReplicationView::createdAt).reversed()).toList();
    }

    public StoryVideoReplicationView get(UUID id) {
        Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("故事视频任务不存在: " + id);
        return task.view();
    }

    public StoryVideoReplicationView execute(UUID id, List<ShotRequest> requests) {
        Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("故事视频任务不存在: " + id);
        if (task.plan == null || task.plan.shots().isEmpty()) throw new IllegalStateException("请先完成视频分析");
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("请先确认镜头计划");
        task.status = "QUEUED";
        task.message = "已确认镜头计划，等待按顺序生成";
        persist(task);
        executor.execute(() -> executeAsync(task, requests));
        return task.view();
    }

    /** Generate one shot only. The next shot is intentionally not started automatically. */
    public StoryVideoReplicationView generateShot(UUID id, int sequence, ShotRequest request) {
        Task task = requireTask(id);
        if (task.plan == null || task.plan.shots().stream().noneMatch(s -> s.sequence() == sequence)) {
            throw new IllegalArgumentException("镜头不存在: " + sequence);
        }
        if (request == null) throw new IllegalArgumentException("shot request is required");
        ShotExecution execution = task.shots.computeIfAbsent(sequence, key -> new ShotExecution(sequence));
        synchronized (execution) {
            if ("GENERATING".equals(execution.status)) return task.view();
            execution.request = request;
            execution.status = "GENERATING";
            execution.error = null;
            execution.message = "正在生成本镜头";
            task.error = null;
        }
        task.status = "GENERATING";
        task.message = "正在生成镜头 " + sequence;
        persist(task);
        executor.execute(() -> generateShotAsync(task, sequence, request));
        return task.view();
    }

    public StoryVideoReplicationView recognizeFirstFrame(UUID id, int sequence) {
        Task task = requireTask(id);
        if (sequence <= 1) throw new IllegalArgumentException("第一个镜头不需要识别首帧");
        ShotExecution previous = task.shots.get(sequence - 1);
        if (previous == null || !"SUCCESS".equals(previous.status) || previous.lastFrame == null || !Files.isRegularFile(previous.lastFrame)) {
            throw new IllegalStateException("请先生成上一个镜头并提取最后一帧");
        }
        ShotExecution current = task.shots.computeIfAbsent(sequence, key -> new ShotExecution(sequence));
        current.firstFrame = previous.lastFrame;
        current.firstFrameRecognized = true;
        current.message = "已识别并绑定上一个镜头的最后一帧";
        persist(task);
        return task.view();
    }

    public StoryVideoReplicationView assemble(UUID id) {
        Task task = requireTask(id);
        if (task.plan == null) throw new IllegalStateException("请先完成镜头分析");
        List<Path> outputs = new ArrayList<>();
        for (StoryVideoPlan.Shot shot : task.plan.shots()) {
            ShotExecution execution = task.shots.get(shot.sequence());
            if (execution == null || !"SUCCESS".equals(execution.status) || execution.video == null || !Files.isRegularFile(execution.video)) {
                throw new IllegalStateException("镜头 " + shot.sequence() + " 尚未生成完成");
            }
            outputs.add(execution.video);
        }
        task.status = "MERGING";
        task.message = "正在组装全部镜头";
        persist(task);
        executor.execute(() -> {
            try {
                task.finalVideo = task.work.resolve("final-video.mp4");
                media.concatSegments(outputs, task.finalVideo, task.work.resolve("merge.log"));
                Path finalLastFrame = task.work.resolve("final-video-last.jpg");
                media.extractLastFrame(task.finalVideo, finalLastFrame, task.work.resolve("final-video-last.log"));
                Path exportDirectory = properties.getReplicationDirectory().toAbsolutePath().normalize();
                Files.createDirectories(exportDirectory);
                Files.copy(finalLastFrame, exportDirectory.resolve(task.id + "-final-last.jpg"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                task.status = "SUCCESS";
                task.message = "故事视频组装完成";
            } catch (Exception e) {
                task.status = "FAILED";
                task.error = rootMessage(e);
                task.message = "故事视频组装失败";
            }
            persist(task);
        });
        return task.view();
    }

    public Path shotVideo(UUID id, int sequence) {
        ShotExecution execution = requireTask(id).shots.get(sequence);
        if (execution == null || execution.video == null || !Files.isRegularFile(execution.video)) throw new IllegalStateException("镜头视频尚未生成");
        return execution.video;
    }

    public Path firstFrame(UUID id, int sequence) {
        ShotExecution execution = requireTask(id).shots.get(sequence);
        if (execution == null || execution.firstFrame == null || !Files.isRegularFile(execution.firstFrame)) throw new IllegalStateException("首帧尚未识别");
        return execution.firstFrame;
    }

    private Task requireTask(UUID id) {
        Task task = tasks.get(id);
        if (task == null) throw new IllegalArgumentException("故事视频任务不存在: " + id);
        return task;
    }

    private void generateShotAsync(Task task, int sequence, ShotRequest request) {
        ShotExecution execution = task.shots.get(sequence);
        try {
            StoryVideoPlan.Shot planned = task.plan.shots().stream().filter(s -> s.sequence() == sequence).findFirst().orElseThrow();
            String type = sequence == 1 ? "TEXT_TO_VIDEO_IMAGE" : "FIRST_LAST_FRAME";
            String finalPrompt = completePrompt(request.prompt(), planned);
            ComfyUiVideoView created;
            if ("FIRST_LAST_FRAME".equals(type)) {
                String first = request.firstFrame();
                if ((first == null || first.isBlank()) && execution.firstFrame != null) first = dataUrl(execution.firstFrame);
                if (first == null || first.isBlank()) throw new IllegalStateException("请先点击识别首帧");
                String last = request.lastFrame();
                if (last == null || last.isBlank()) last = first;
                created = video.createFirstLast(finalPrompt, request.duration(), request.resolution(), first, last);
            } else {
                List<String> images = request.images() == null ? List.of() : request.images();
                if (images.isEmpty()) throw new IllegalArgumentException("第一个镜头至少需要一张人物图片");
                created = video.create(finalPrompt, request.duration(), request.resolution(), images);
            }
            execution.remoteTaskId = created.id().toString();
            ComfyUiVideoView completed = await(created.id());
            Path output = video.finalVideo(completed.id());
            Path copy = task.work.resolve("shot-" + String.format("%02d", sequence) + ".mp4");
            Files.copy(output, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path frame = task.work.resolve("shot-" + String.format("%02d", sequence) + "-last.jpg");
            media.extractLastFrame(copy, frame, task.work.resolve("shot-" + sequence + "-last.log"));
            Path exportDirectory = properties.getReplicationDirectory().toAbsolutePath().normalize();
            Files.createDirectories(exportDirectory);
            Path exportedFrame = exportDirectory.resolve(task.id + "-shot-" + String.format("%02d", sequence) + "-last.jpg");
            Files.copy(frame, exportedFrame, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            execution.video = copy;
            execution.lastFrame = frame;
            execution.status = "SUCCESS";
            execution.message = "镜头生成完成";
            task.status = "READY";
            task.message = "镜头 " + sequence + " 生成完成，可继续生成其他镜头或组装";
        } catch (Exception e) {
            execution.status = "FAILED";
            execution.error = rootMessage(e);
            execution.message = "镜头生成失败";
            task.status = "READY";
            task.error = execution.error;
        }
        persist(task);
    }

    public Path finalVideo(UUID id) {
        Task task = tasks.get(id);
        if (task == null || task.finalVideo == null || !Files.isRegularFile(task.finalVideo)) {
            throw new IllegalStateException("故事视频尚未生成完成");
        }
        return task.finalVideo;
    }

    private void analyzeAsync(Task task) {
        try {
            task.status = "ANALYZING";
            task.message = "正在读取视频元数据并抽取关键帧";
            persist(task);
            media.requireAvailable();
            VideoProbe probe = media.probe(task.source);
            task.duration = probe.durationSeconds();
            List<ImageContent> frames = new ArrayList<>();
            int count = Math.max(3, Math.min(8, (int) Math.ceil(probe.durationSeconds() / 4D)));
            for (int i = 0; i < count; i++) {
                double at = Math.min(Math.max(0, probe.durationSeconds() - .1), probe.durationSeconds() * i / count);
                Path frame = task.work.resolve("keyframe-" + String.format("%02d", i + 1) + ".jpg");
                media.extractFrameAt(task.source, at, frame, task.work.resolve("keyframe-" + (i + 1) + ".log"));
                frames.add(encoder.encode(frame));
            }
            task.status = "ANALYZING_SCRIPT";
            task.message = "关键帧已完成，正在让 GLM 分析对白、语气和镜头";
            persist(task);
            String speech = recognizeSpeech(task.source, task.work);
            String evidence = "视频时长: " + probe.durationSeconds() + " 秒\n"
                    + "分辨率: " + probe.width() + "x" + probe.height() + "\n"
                    + "是否含音频: " + probe.hasAudio() + "\n"
                    + "语音转写与语气线索: " + speech;
            StoryVideoAnalysisAgent.Analysis analysis = aiCalls.execute("视频剧情与镜头分析",
                    () -> analysisAgent().analyze(evidence, frames));
            task.speechSummary = text(analysis.speechSummary());
            task.plan = normalize(analysis, probe.durationSeconds());
            task.status = "READY";
            task.message = "分析完成，请检查镜头计划并为需要的镜头选择人物图片";
            persist(task);
        } catch (Exception e) {
            LOGGER.error("故事视频分析失败 id={}", task.id, e);
            task.status = "FAILED";
            task.error = rootMessage(e);
            task.message = "视频分析失败";
            persist(task);
        }
    }

    private void executeAsync(Task task, List<ShotRequest> requests) {
        try {
            task.status = "GENERATING";
            List<Path> outputs = new ArrayList<>();
            String previousLastFrame = null;
            Path sourceFirstFrame = task.work.resolve("source-first-frame.jpg");
            media.extractFrameAt(task.source, 0, sourceFirstFrame, task.work.resolve("source-first-frame.log"));
            String sourceFirstFrameData = dataUrl(sourceFirstFrame);
            for (StoryVideoPlan.Shot planned : task.plan.shots()) {
                ShotRequest request = requests.stream().filter(item -> item.sequence() == planned.sequence()).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("缺少镜头 " + planned.sequence() + " 的确认数据"));
                String finalPrompt = completePrompt(request.prompt(), planned);
                task.message = "正在生成第 " + planned.sequence() + " / " + task.plan.shots().size() + " 个镜头";
                persist(task);
                ComfyUiVideoView created;
                // The first shot is normal image-to-video; every later shot must preserve continuity through first/last frames.
                if (planned.sequence() > 1) {
                    String first = request.firstFrame() == null || request.firstFrame().isBlank()
                            ? (previousLastFrame == null
                                ? (request.images() != null && !request.images().isEmpty() ? request.images().get(0) : sourceFirstFrameData)
                                : previousLastFrame)
                            : request.firstFrame();
                    String last = request.lastFrame() == null || request.lastFrame().isBlank() ? first : request.lastFrame();
                    if (first == null || last == null) throw new IllegalArgumentException("镜头 " + planned.sequence() + " 缺少首尾帧图片");
                    created = video.createFirstLast(finalPrompt, request.duration(), request.resolution(), first, last);
                } else {
                    List<String> images = new ArrayList<>();
                    images.add(previousLastFrame == null ? sourceFirstFrameData : previousLastFrame);
                    if (request.images() != null) images.addAll(request.images());
                    created = video.create(finalPrompt, request.duration(), request.resolution(), images);
                }
                ComfyUiVideoView completed = await(created.id());
                Path output = video.finalVideo(completed.id());
                Path copy = task.work.resolve("shot-" + String.format("%02d", planned.sequence()) + ".mp4");
                Files.copy(output, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                outputs.add(copy);
                Path frame = task.work.resolve("shot-" + String.format("%02d", planned.sequence()) + "-last.jpg");
                media.extractLastFrame(copy, frame, task.work.resolve("shot-" + planned.sequence() + "-last.log"));
                Path exportDirectory = properties.getReplicationDirectory().toAbsolutePath().normalize();
                Files.createDirectories(exportDirectory);
                Files.copy(frame, exportDirectory.resolve(task.id + "-shot-" + String.format("%02d", planned.sequence()) + "-last.jpg"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                previousLastFrame = dataUrl(frame);
            }
            task.status = "MERGING";
            task.message = "所有镜头已完成，正在合成为最终视频";
            persist(task);
            task.finalVideo = task.work.resolve("final-video.mp4");
            media.concatSegments(outputs, task.finalVideo, task.work.resolve("merge.log"));
            task.status = "SUCCESS";
            task.message = "故事视频生成完成";
            persist(task);
        } catch (Exception e) {
            LOGGER.error("故事视频生成失败 id={}", task.id, e);
            task.status = "FAILED";
            task.error = rootMessage(e);
            task.message = "故事视频生成失败";
            persist(task);
        }
    }

    private ComfyUiVideoView await(UUID id) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(45));
        while (Instant.now().isBefore(deadline)) {
            ComfyUiVideoView view = video.get(id);
            if (view.status() == ComfyUiVideoStatus.SUCCESS) return view;
            if (view.status() == ComfyUiVideoStatus.FAILED) throw new IllegalStateException(view.error() == null ? view.message() : view.error());
            Thread.sleep(2000L);
        }
        throw new IllegalStateException("镜头生成超过 45 分钟");
    }

    private StoryVideoAnalysisAgent analysisAgent() {
        if (!aiProperties.isModelConfigured()) throw new IllegalStateException("视频分析需要在账号配置中填写智谱 GLM API Key");
        String key = aiProperties.getBaseUrl() + "|" + aiProperties.getModelName() + "|" + aiProperties.getApiKey();
        return agents.computeIfAbsent(key, ignored -> AiServices.builder(StoryVideoAnalysisAgent.class)
                .chatModel(FashionAiConfiguration.createModel(aiProperties)).build());
    }

    private StoryVideoPlan normalize(StoryVideoAnalysisAgent.Analysis analysis, double totalDuration) {
        List<StoryVideoPlan.Shot> shots = new ArrayList<>();
        int sequence = 1;
        List<StoryVideoAnalysisAgent.Shot> analyzedShots = mergeShots(analysis.shots(), totalDuration);
        for (StoryVideoAnalysisAgent.Shot shot : analyzedShots) {
            if (shot == null || shot.prompt() == null || shot.prompt().isBlank()) continue;
            int duration = Math.max(5, Math.min(10, shot.duration()));
            String type = "FIRST_LAST_FRAME".equalsIgnoreCase(shot.interfaceType()) || sequence > 1 ? "FIRST_LAST_FRAME" : "TEXT_TO_VIDEO_IMAGE";
            String first = sequence == 1 ? (blank(shot.firstFrameSource()) ? "USER_IMAGE" : shot.firstFrameSource()) : "PREVIOUS_LAST_FRAME";
            String last = blank(shot.lastFrameSource()) ? "GENERATED_LAST_FRAME" : shot.lastFrameSource();
            List<String> sourceCharacters = shot.characters() == null ? List.of() : shot.characters().stream().filter(v -> v != null && !v.isBlank()).toList();
            int imageStart = sequence == 1 ? 1 : 2;
            List<String> characters = new ArrayList<>();
            for (int i = 0; i < sourceCharacters.size(); i++) characters.add("图" + (imageStart + i) + "人物");
            List<StoryVideoPlan.DialogueLine> dialogueLines = shot.dialogueLines() == null ? List.of() : shot.dialogueLines().stream()
                    .filter(v -> v != null && v.text() != null && !v.text().isBlank())
                    .map(v -> new StoryVideoPlan.DialogueLine(text(v.speaker()), v.text().trim(), text(v.tone()))).toList();
            String dialogue = text(shot.dialogue());
            if (dialogue.isBlank() && !dialogueLines.isEmpty()) {
                dialogue = dialogueLines.stream().map(v -> v.speaker() + "：" + v.text() + (v.tone().isBlank() ? "" : "（" + v.tone() + "）"))
                        .reduce((a, b) -> a + "；" + b).orElse("");
            }
            String frameInstruction = sequence > 1
                    ? "\nFIRST_LAST_FRAME 规则：图1是上一镜头视频最后一帧，代表完整环境、构图、光线和空间连续性，不是人物参考图；人物参考图从图2开始。必须保持图1的环境和镜头关系，只描述这一镜头内完整发生的动作、表情、对白、节奏、镜头运动和结束状态。"
                    : "\n普通图生视频规则：人物参考图按图1、图2、图3顺序使用；请完整描述本镜头从开始到结束的连续动作和镜头语言。";
            String prompt = "人物仅按参考图编号识别，不描述人物长相、服装或外貌。" + frameInstruction + "\n" + shot.prompt().trim() + (text(shot.environment()).isBlank() ? "" : "\n环境细节：" + text(shot.environment()))
                    + (characters.isEmpty() ? "" : "\n人物参考图映射：" + String.join("；", characters))
                    + (dialogue.isBlank() ? "" : "\n对白与语气：" + dialogue);
            shots.add(new StoryVideoPlan.Shot(sequence++, duration, type, prompt, text(shot.environment()), characters, dialogueLines, first, last,
                    shot.characterImageRequired() || !characters.isEmpty(), text(shot.characterImageHint()), dialogue));
        }
        if (shots.isEmpty()) throw new IllegalStateException("GLM 未返回有效镜头");
        return new StoryVideoPlan(shots, text(analysis.analysisNotes()));
    }

    /** Keep each generated segment substantial: a 28-second source becomes three 9-10 second shots. */
    private static List<StoryVideoAnalysisAgent.Shot> mergeShots(List<StoryVideoAnalysisAgent.Shot> source, double totalDuration) {
        List<StoryVideoAnalysisAgent.Shot> valid = source == null ? List.of() : source.stream().filter(s -> s != null && s.prompt() != null && !s.prompt().isBlank()).toList();
        if (valid.isEmpty()) return List.of();
        int desired = Math.max(1, (int) Math.ceil(Math.max(1D, totalDuration) / 10D));
        if (valid.size() <= desired) return valid;
        List<StoryVideoAnalysisAgent.Shot> merged = new ArrayList<>();
        for (int bucket = 0; bucket < desired; bucket++) {
            int from = bucket * valid.size() / desired;
            int to = (bucket + 1) * valid.size() / desired;
            List<StoryVideoAnalysisAgent.Shot> group = valid.subList(from, Math.max(from + 1, to));
            StoryVideoAnalysisAgent.Shot first = group.get(0);
            String prompt = group.stream().map(StoryVideoAnalysisAgent.Shot::prompt).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + "\n连续动作：" + b).orElse("");
            String environment = group.stream().map(StoryVideoAnalysisAgent.Shot::environment).filter(v -> v != null && !v.isBlank()).distinct().reduce((a, b) -> a + "；" + b).orElse("");
            List<String> characters = group.stream().flatMap(s -> s.characters() == null ? java.util.stream.Stream.<String>empty() : s.characters().stream()).filter(v -> v != null && !v.isBlank()).distinct().toList();
            List<StoryVideoAnalysisAgent.DialogueLine> dialogue = group.stream().flatMap(s -> s.dialogueLines() == null ? java.util.stream.Stream.<StoryVideoAnalysisAgent.DialogueLine>empty() : s.dialogueLines().stream()).toList();
            String dialogueText = group.stream().map(StoryVideoAnalysisAgent.Shot::dialogue).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + "；" + b).orElse("");
            String lastFrame = group.get(group.size() - 1).lastFrameSource();
            merged.add(new StoryVideoAnalysisAgent.Shot(bucket + 1, group.stream().mapToInt(StoryVideoAnalysisAgent.Shot::duration).sum(),
                    bucket == 0 ? "TEXT_TO_VIDEO_IMAGE" : "FIRST_LAST_FRAME", prompt, environment, characters, dialogue,
                    first.firstFrameSource(), lastFrame, group.stream().anyMatch(StoryVideoAnalysisAgent.Shot::characterImageRequired),
                    group.stream().map(StoryVideoAnalysisAgent.Shot::characterImageHint).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + "；" + b).orElse(""), dialogueText));
        }
        return merged;
    }

    private String recognizeSpeech(Path source, Path work) {
        String command = properties.getWhisperCommand();
        if (command == null || command.isBlank()) return "未配置 WHISPER_COMMAND，暂未获得逐字转写；请根据视频画面和音频信息人工补充对白。";
        try {
            Process process = new ProcessBuilder(command, source.toString(), "--model", "small", "--output_format", "txt", "--output_dir", work.toString())
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.MINUTES)) { process.destroyForcibly(); return "语音识别超时"; }
            Path txt = work.resolve(source.getFileName().toString().replaceFirst("\\.[^.]+$", ".txt"));
            return Files.isRegularFile(txt) ? Files.readString(txt, StandardCharsets.UTF_8) : "语音识别未返回文本";
        } catch (Exception e) { return "语音识别不可用: " + e.getMessage(); }
    }

    private String dataUrl(Path image) throws IOException {
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(image));
    }

    private static String completePrompt(String prompt, StoryVideoPlan.Shot planned) {
        String result = text(prompt);
        if (result.isBlank()) result = planned.prompt();
        if (!planned.environment().isBlank() && !result.contains("环境细节：")) result += "\n环境细节：" + planned.environment();
        if (!planned.characters().isEmpty() && !result.contains("人物参考图映射：")) result += "\n人物参考图映射：" + String.join("；", planned.characters());
        if (!planned.dialogue().isBlank() && !result.contains("对白与语气：")) result += "\n对白与语气：" + planned.dialogue();
        return result.trim();
    }
    private void persist(Task task) { task.updatedAt = Instant.now(); }
    private static String extension(String name) { int i = name.lastIndexOf('.'); return i < 0 ? ".mp4" : name.substring(i).toLowerCase(); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String rootMessage(Throwable e) { Throwable c = e; while (c.getCause() != null) c = c.getCause(); return c.getMessage() == null ? c.toString() : c.getMessage(); }
    private static void deleteDirectory(Path path) { try { if (Files.exists(path)) Files.walk(path).sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); } catch (Exception ignored) {} }

    public record ShotRequest(int sequence, int duration, String interfaceType, String prompt, String resolution,
                              String firstFrame, String lastFrame, List<String> images) {}

    private static final class ShotExecution {
        private final int sequence;
        private volatile String interfaceType;
        private volatile String status = "WAITING";
        private volatile String message = "等待生成";
        private volatile String remoteTaskId;
        private volatile Path video;
        private volatile Path firstFrame;
        private volatile Path lastFrame;
        private volatile boolean firstFrameRecognized;
        private volatile String error;
        private volatile ShotRequest request;
        private ShotExecution(int sequence) { this.sequence = sequence; }
    }

    private static final class Task {
        private final UUID id; private volatile String sourceFileName; private volatile Path source; private final Path work;
        private final Instant createdAt = Instant.now(); private volatile Instant updatedAt = createdAt;
        private volatile double duration; private volatile String speechSummary = ""; private volatile StoryVideoPlan plan;
        private volatile String status = "QUEUED"; private volatile String message = "已接收视频"; private volatile String error; private volatile Path finalVideo;
        private final Map<Integer, ShotExecution> shots = new ConcurrentHashMap<>();
        private Task(UUID id, String sourceFileName, Path source, Path work) { this.id=id; this.sourceFileName=sourceFileName; this.source=source; this.work=work; }
        private StoryVideoReplicationView view() { return new StoryVideoReplicationView(id, sourceFileName, duration, speechSummary, plan, status, message,
                shotViews(),
                finalVideo == null ? null : "/api/story-video-replications/" + id + "/final", finalVideo == null ? null : finalVideo.getFileName().toString(), error, createdAt, updatedAt); }
        private List<StoryVideoReplicationView.ShotExecutionView> shotViews() {
            if (plan == null) return List.of();
            return plan.shots().stream().map(shot -> {
                ShotExecution e = shots.get(shot.sequence());
                String type = shot.sequence() == 1 ? "TEXT_TO_VIDEO_IMAGE" : "FIRST_LAST_FRAME";
                if (e == null) return new StoryVideoReplicationView.ShotExecutionView(shot.sequence(), type, "WAITING", "等待生成", null, null, null, false, null);
                return new StoryVideoReplicationView.ShotExecutionView(shot.sequence(), type, e.status, e.message, e.remoteTaskId,
                        e.video == null ? null : "/api/story-video-replications/" + id + "/shots/" + shot.sequence() + "/video",
                        e.firstFrame == null ? null : "/api/story-video-replications/" + id + "/shots/" + shot.sequence() + "/first-frame",
                        e.firstFrameRecognized, e.error);
            }).toList();
        }
    }
}
