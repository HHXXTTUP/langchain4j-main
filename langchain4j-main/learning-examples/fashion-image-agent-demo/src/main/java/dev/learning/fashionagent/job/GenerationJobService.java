package dev.learning.fashionagent.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.ai.PortraitQualityReport;
import dev.learning.fashionagent.pipeline.FashionAgentPipeline;
import dev.learning.fashionagent.pipeline.OutfitAttempt;
import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineResult;
import dev.learning.fashionagent.pipeline.PipelineStage;
import dev.learning.fashionagent.pipeline.PortraitAttempt;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import dev.learning.fashionagent.rag.FashionKnowledgeContext;
import dev.learning.fashionagent.learning.ExperienceLearningResult;
import dev.learning.fashionagent.learning.ClothingSemanticSelector;
import dev.learning.fashionagent.learning.FashionExperienceLearningService;
import dev.learning.fashionagent.service.GeneratedTaskFileDeletionService;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class GenerationJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationJobService.class);

    private final Map<UUID, GenerationJob> jobs = new ConcurrentHashMap<>();
    private final Map<UUID, FutureTask<Void>> executions = new ConcurrentHashMap<>();
    private final FashionAgentPipeline pipeline;
    private final Executor executor;
    private final JobHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final FashionExperienceLearningService experienceLearningService;
    private final GeneratedTaskFileDeletionService fileDeletionService;

    @Autowired
    public GenerationJobService(
            FashionAgentPipeline pipeline,
            @Qualifier("fashionPipelineExecutor") Executor executor,
            JobHistoryRepository historyRepository,
            ObjectMapper objectMapper,
            FashionExperienceLearningService experienceLearningService,
            GeneratedTaskFileDeletionService fileDeletionService) {
        this.pipeline = pipeline;
        this.executor = executor;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.experienceLearningService = experienceLearningService;
        this.fileDeletionService = fileDeletionService;
    }

    GenerationJobService(
            FashionAgentPipeline pipeline,
            Executor executor,
            JobHistoryRepository historyRepository,
            ObjectMapper objectMapper) {
        this.pipeline = pipeline;
        this.executor = executor;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.experienceLearningService = null;
        this.fileDeletionService = null;
    }

    GenerationJobService(
            FashionAgentPipeline pipeline,
            Executor executor,
            JobHistoryRepository historyRepository,
            ObjectMapper objectMapper,
            GeneratedTaskFileDeletionService fileDeletionService) {
        this.pipeline = pipeline;
        this.executor = executor;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.experienceLearningService = null;
        this.fileDeletionService = fileDeletionService;
    }

    GenerationJobService(FashionAgentPipeline pipeline, Executor executor) {
        this(pipeline, executor, JobHistoryRepository.noop(), new ObjectMapper().findAndRegisterModules());
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInterruptedJobs(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext) {
            recoverInterruptedJobs();
        }
    }

    void recoverInterruptedJobs() {
        int recovered = historyRepository.recoverInterruptedJobs();
        if (recovered > 0) {
            LOGGER.warn("Recovered {} unfinished fashion generation job(s) left by a previous application process", recovered);
        }
    }

    public UUID create(String prompt) {
        return create(prompt, PortraitGenerationMode.STANDARD);
    }

    public UUID create(String prompt, PortraitGenerationMode requestedMode) {
        String normalizedPrompt = validatePrompt(prompt);
        PortraitGenerationMode mode = PortraitGenerationMode.defaultIfNull(requestedMode);
        return enqueue(normalizedPrompt, mode);
    }

    public List<UUID> createBatch(List<String> prompts, PortraitGenerationMode requestedMode) {
        if (prompts == null || prompts.isEmpty()) {
            throw new IllegalArgumentException("描述词不能为空");
        }
        if (prompts.size() > GenerationPromptBatchParser.MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "一次最多批量创建 " + GenerationPromptBatchParser.MAX_BATCH_SIZE + " 条图片任务");
        }
        List<String> normalizedPrompts = prompts.stream()
                .map(GenerationJobService::validatePrompt)
                .toList();
        PortraitGenerationMode mode = PortraitGenerationMode.defaultIfNull(requestedMode);
        return normalizedPrompts.stream()
                .map(prompt -> enqueue(prompt, mode))
                .toList();
    }

    private UUID enqueue(String normalizedPrompt, PortraitGenerationMode mode) {
        GenerationJob job = new GenerationJob(UUID.randomUUID(), normalizedPrompt, mode);
        jobs.put(job.id, job);
        persist(job);
        recordEvent(job, "JOB_CREATED", "任务已进入队列", Map.of(
                "prompt", normalizedPrompt,
                "portraitGenerationMode", mode.name()));
        FutureTask<Void> task = new FutureTask<>(() -> {
            execute(job);
            return null;
        });
        executions.put(job.id, task);
        executor.execute(task);
        return job.id;
    }

    public JobView get(UUID id) {
        GenerationJob job = jobs.get(id);
        if (job != null) {
            return job.view();
        }
        return historyRepository.findJob(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    public List<JobView> list() {
        Map<UUID, JobView> combined = new ConcurrentHashMap<>();
        historyRepository.listJobs().forEach(job -> combined.put(job.id(), job));
        jobs.values().stream().map(GenerationJob::view).forEach(job -> combined.put(job.id(), job));
        return combined.values().stream()
                .sorted(Comparator.comparing(JobView::createdAt).reversed())
                .toList();
    }

    public void validateDeletion(UUID id) {
        JobStatus status = get(id).status();
        if (status == JobStatus.QUEUED || status == JobStatus.RUNNING) {
            throw new IllegalStateException("任务仍在排队或运行，请先停止任务再删除");
        }
    }

    public void delete(UUID id) {
        validateDeletion(id);
        if (fileDeletionService == null) {
            throw new IllegalStateException("任务文件删除服务未配置");
        }
        fileDeletionService.deleteImageJobDirectory(id);
        jobs.remove(id);
        executions.remove(id);
        historyRepository.delete(id);
        LOGGER.info("Fashion generation job {} and its generated files were deleted", id);
    }

    public List<JobStepView> events(UUID id) {
        GenerationJob job = jobs.get(id);
        if (job != null) {
            return job.events();
        }
        if (historyRepository.findJob(id).isEmpty()) {
            throw new JobNotFoundException(id);
        }
        return historyRepository.listEvents(id);
    }

    public boolean isHistoryDatabaseReady() {
        return historyRepository.available();
    }

    public JobView cancel(UUID id) {
        GenerationJob job = jobs.get(id);
        if (job == null) {
            if (historyRepository.findJob(id).isPresent()) {
                throw new IllegalStateException("历史任务已经结束，不能停止");
            }
            throw new JobNotFoundException(id);
        }
        if (!job.cancel()) {
            throw new IllegalStateException("任务已经结束，不能停止");
        }
        FutureTask<Void> task = executions.get(id);
        if (task != null) {
            task.cancel(true);
            executions.remove(id, task);
        }
        persist(job);
        recordEvent(job, "JOB_CANCELLED", "用户手动停止任务", null);
        LOGGER.info("Fashion generation job {} cancelled by user", id);
        return job.view();
    }

    public UUID restart(UUID id) {
        JobView source = get(id);
        if (source.status() == JobStatus.RUNNING || source.status() == JobStatus.QUEUED) {
            throw new IllegalStateException("任务仍在运行，请先停止后再重新启动");
        }
        UUID restartedJobId = create(source.prompt(), source.portraitGenerationMode());
        GenerationJob inMemorySource = jobs.get(id);
        if (inMemorySource != null) {
            recordEvent(
                    inMemorySource,
                    "JOB_RESTARTED",
                    "已基于当前任务创建新的执行任务",
                    Map.of("restartedJobId", restartedJobId.toString()));
        } else {
            historyRepository.appendEvent(
                    id,
                    new JobStepView(
                            null,
                            "JOB_RESTARTED",
                            source.stage(),
                            "已基于当前任务创建新的执行任务",
                            toJson(Map.of("restartedJobId", restartedJobId.toString())),
                            Instant.now()));
        }
        LOGGER.info("Fashion generation job {} restarted as {}", id, restartedJobId);
        return restartedJobId;
    }

    public Path clothingImage(UUID id) {
        GenerationJob job = jobs.get(id);
        Path path = job == null
                ? historyRepository.findClothingImage(id).orElse(null)
                : job.clothingPath();
        if (path == null) {
            throw new IllegalStateException("该任务尚未选择服装图片");
        }
        return path;
    }

    public Path originalImage(UUID id) {
        GenerationJob job = jobs.get(id);
        Path path = job == null
                ? historyRepository.findOriginalImage(id).orElse(null)
                : job.originalImagePath();
        if (path == null) {
            throw new IllegalStateException("该任务尚未下载人物底图");
        }
        return path;
    }

    public Path finalImage(UUID id) {
        GenerationJob job = jobs.get(id);
        Path path = job == null
                ? historyRepository.findFinalImage(id).orElse(null)
                : job.finalImagePath();
        if (path == null) {
            throw new IllegalStateException("该任务尚未生成换装图片");
        }
        return path;
    }

    public Path attemptImage(UUID id, int attemptNumber) {
        GenerationJob job = jobs.get(id);
        Path path = job == null
                ? historyRepository.findOutfitAttemptImage(id, attemptNumber).orElse(null)
                : job.attemptPath(attemptNumber);
        if (path == null) {
            throw new IllegalStateException("该任务不存在第 " + attemptNumber + " 次换装图片");
        }
        return path;
    }

    public Path portraitAttemptImage(UUID id, int attemptNumber) {
        GenerationJob job = jobs.get(id);
        Path path = job == null
                ? historyRepository.findPortraitAttemptImage(id, attemptNumber).orElse(null)
                : job.portraitAttemptPath(attemptNumber);
        if (path == null) {
            throw new IllegalStateException("该任务不存在第 " + attemptNumber + " 张人物候选图");
        }
        return path;
    }

    private void execute(GenerationJob job) {
        job.start();
        persist(job);
        recordEvent(job, "JOB_STARTED", "任务开始执行", null);
        PipelineObserver observer = new PipelineObserver() {
            @Override
            public void stage(PipelineStage stage, String message) {
                job.checkCancellation();
                LOGGER.info("Fashion generation job {} stage={} message={}", job.id, stage, message);
                job.transition(stage, message);
                persist(job);
                recordEvent(job, "STAGE", message, null);
            }

            @Override
            public void originalImage(Path imagePath) {
                job.checkCancellation();
                job.originalImage(imagePath);
                persist(job);
                recordEvent(job, "ORIGINAL_IMAGE", "人物底图已保存", Map.of("path", imagePath.toString()));
            }

            @Override
            public void portraitPrompt(PortraitPromptSpec promptSpec) {
                job.checkCancellation();
                job.portraitPrompt(promptSpec);
                persist(job);
                recordEvent(job, "PORTRAIT_PROMPT", "人物提示词扩写完成", promptSpec);
            }

            @Override
            public void portraitAttempt(PortraitAttempt attempt) {
                job.checkCancellation();
                job.portraitAttempt(attempt);
                historyRepository.savePortraitAttempt(job.id, attempt);
                persist(job);
                recordEvent(job, "PORTRAIT_ATTEMPT", "人物候选图第 " + attempt.attemptNumber() + " 次结果", attempt);
            }

            @Override
            public void clothingImage(Path imagePath) {
                job.checkCancellation();
                job.clothingImage(imagePath);
                persist(job);
                recordEvent(job, "CLOTHING_SELECTED", "本地服装图片已选择", Map.of("path", imagePath.toString()));
            }

            @Override
            public void clothingSelection(ClothingSemanticSelector.Selection selection) {
                job.checkCancellation();
                job.clothingSelection(selection);
                persist(job);
                String message = selection.semantic()
                        ? "服装“" + selection.clothingName() + "”已选择，匹配度 "
                                + String.format("%.1f%%", selection.matchPercentage())
                        : "本地服装图片已随机选择，未计算语义匹配度";
                recordEvent(job, "CLOTHING_SELECTED", message, selection);
            }

            @Override
            public void fashionAnalysis(FashionReferenceSpec analysis) {
                job.checkCancellation();
                job.fashionAnalysis(analysis);
                persist(job);
                recordEvent(job, "FASHION_ANALYSIS", "服装视觉理解完成", analysis);
            }

            @Override
            public void fashionKnowledge(FashionKnowledgeContext context) {
                job.checkCancellation();
                recordEvent(job, "RAG_RETRIEVAL", context.message(), context);
            }

            @Override
            public void outfitAttempt(OutfitAttempt attempt) {
                job.checkCancellation();
                job.outfitAttempt(attempt);
                historyRepository.saveOutfitAttempt(job.id, attempt);
                persist(job);
                recordEvent(job, "OUTFIT_ATTEMPT", "换装候选图第 " + attempt.attemptNumber() + " 次结果", attempt);
            }

            @Override
            public void finalImage(Path imagePath) {
                job.checkCancellation();
                job.finalImage(imagePath);
                persist(job);
                recordEvent(job, "FINAL_IMAGE", "最终换装图片已归档", Map.of("path", imagePath.toString()));
            }
        };

        try {
            PipelineResult result = job.portraitGenerationMode == PortraitGenerationMode.ENHANCED
                    ? pipeline.run(job.id, job.prompt, job.portraitGenerationMode, observer)
                    : pipeline.run(job.id, job.prompt, observer);
            job.checkCancellation();
            if (experienceLearningService != null) {
                job.transition(PipelineStage.RAG_LEARNING_EXPERIENCE, "正在提取已验证策略和遗漏修复规则，更新 RAG 知识库");
                persist(job);
                recordEvent(job, "KNOWLEDGE_LEARNING_STARTED", "开始提取换装策略和遗漏修复规则", null);
                try {
                    ExperienceLearningResult learningResult = experienceLearningService.learn(
                            job.id, job.prompt, result);
                    recordEvent(job, "KNOWLEDGE_LEARNING_" + learningResult.status(),
                            learningResult.message(), learningResult);
                } catch (RuntimeException learningFailure) {
                    LOGGER.error("Fashion generation job {} experience learning failed", job.id, learningFailure);
                    recordEvent(job, "KNOWLEDGE_LEARNING_FAILED",
                            "业务任务已完成，但经验更新失败：" + rootMessage(learningFailure),
                            Map.of("error", rootMessage(learningFailure)));
                }
            }
            job.checkCancellation();
            job.complete(result);
            persist(job);
            recordEvent(job, "JOB_COMPLETED", "人物生成、服装理解、RAG 检索和换装质检链路已全部完成", result);
        } catch (Throwable throwable) {
            if (job.isCancellationRequested() || throwable instanceof CancellationException
                    || Thread.currentThread().isInterrupted()) {
                job.cancel();
                persist(job);
                LOGGER.info("Fashion generation job {} stopped after cancellation signal", job.id);
            } else {
                LOGGER.error("Fashion generation job {} failed", job.id, throwable);
                job.fail(throwable);
                persist(job);
                recordEvent(job, "JOB_FAILED", job.errorMessage(), Map.of("details", job.errorDetails()));
            }
        } finally {
            executions.remove(job.id);
        }
    }

    private void persist(GenerationJob job) {
        historyRepository.saveJob(job.persistenceSnapshot());
    }

    private void recordEvent(GenerationJob job, String eventType, String message, Object result) {
        JobStepView event = job.addEvent(eventType, message, result == null ? null : toJson(result));
        historyRepository.appendEvent(job.id, event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("任务步骤结果 JSON 序列化失败 type={}", value.getClass().getSimpleName(), exception);
            return "{\"serializationError\":\"任务步骤结果无法序列化\"}";
        }
    }

    private static String validatePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("描述词不能为空");
        }
        String normalized = prompt.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("描述词不能超过 2000 个字符");
        }
        return normalized;
    }

    private static String rootMessage(Throwable throwable) {
        String message = null;
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return message == null ? "图片生成失败" : message;
    }

    private static final class GenerationJob {
        private final UUID id;
        private final String prompt;
        private final PortraitGenerationMode portraitGenerationMode;
        private final Instant createdAt = Instant.now();
        private JobStatus status = JobStatus.QUEUED;
        private PipelineStage stage = PipelineStage.ACCEPTED;
        private String message = "任务已进入队列";
        private Path originalImage;
        private Path clothingImage;
        private String clothingMatchName;
        private Double clothingMatchPercentage;
        private String clothingMatchRule;
        private Path finalImage;
        private PortraitPromptSpec portraitPrompt;
        private final List<PortraitAttempt> portraitAttempts = new ArrayList<>();
        private PortraitQualityReport finalPortraitQualityReport;
        private FashionReferenceSpec fashionAnalysis;
        private final List<OutfitAttempt> attempts = new ArrayList<>();
        private OutfitQualityReport finalQualityReport;
        private String reply;
        private String error;
        private String errorDetails;
        private Instant updatedAt = createdAt;
        private final List<JobStepView> events = new ArrayList<>();
        private long nextEventId = 1;

        private GenerationJob(UUID id, String prompt, PortraitGenerationMode portraitGenerationMode) {
            this.id = id;
            this.prompt = prompt;
            this.portraitGenerationMode = PortraitGenerationMode.defaultIfNull(portraitGenerationMode);
        }

        synchronized void start() {
            if (status == JobStatus.CANCELLED) {
                return;
            }
            status = JobStatus.RUNNING;
            updatedAt = Instant.now();
        }

        synchronized boolean cancel() {
            if (isTerminal()) {
                return false;
            }
            status = JobStatus.CANCELLED;
            stage = PipelineStage.CANCELLED;
            message = "任务已手动停止";
            updatedAt = Instant.now();
            return true;
        }

        synchronized boolean isCancellationRequested() {
            return status == JobStatus.CANCELLED;
        }

        synchronized void checkCancellation() {
            if (status == JobStatus.CANCELLED || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("任务已停止");
            }
        }

        synchronized void transition(PipelineStage stage, String message) {
            this.stage = stage;
            this.message = message;
            this.updatedAt = Instant.now();
        }

        synchronized void originalImage(Path imagePath) {
            this.originalImage = imagePath;
            this.updatedAt = Instant.now();
        }

        synchronized void portraitPrompt(PortraitPromptSpec promptSpec) {
            this.portraitPrompt = promptSpec;
            this.updatedAt = Instant.now();
        }

        synchronized void portraitAttempt(PortraitAttempt attempt) {
            portraitAttempts.removeIf(existing -> existing.attemptNumber() == attempt.attemptNumber());
            portraitAttempts.add(attempt);
            portraitAttempts.sort(Comparator.comparingInt(PortraitAttempt::attemptNumber));
            this.updatedAt = Instant.now();
        }

        synchronized void clothingImage(Path imagePath) {
            this.clothingImage = imagePath;
            this.updatedAt = Instant.now();
        }

        synchronized void clothingSelection(ClothingSemanticSelector.Selection selection) {
            this.clothingImage = selection.image();
            this.clothingMatchName = selection.clothingName();
            this.clothingMatchPercentage = selection.semantic() ? selection.matchPercentage() : null;
            this.clothingMatchRule = selection.rule();
            this.updatedAt = Instant.now();
        }

        synchronized void finalImage(Path imagePath) {
            this.finalImage = imagePath;
            this.updatedAt = Instant.now();
        }

        synchronized void fashionAnalysis(FashionReferenceSpec analysis) {
            this.fashionAnalysis = analysis;
            this.updatedAt = Instant.now();
        }

        synchronized void outfitAttempt(OutfitAttempt attempt) {
            attempts.removeIf(existing -> existing.attemptNumber() == attempt.attemptNumber());
            attempts.add(attempt);
            attempts.sort(Comparator.comparingInt(OutfitAttempt::attemptNumber));
            this.updatedAt = Instant.now();
        }

        synchronized void complete(PipelineResult result) {
            if (status == JobStatus.CANCELLED) {
                return;
            }
            status = JobStatus.SUCCESS;
            stage = PipelineStage.COMPLETED;
            message = "人物生成、服装理解、RAG 检索和换装质检链路已全部完成";
            originalImage = result.originalImage();
            clothingImage = result.clothingImage();
            finalImage = result.finalImage();
            portraitPrompt = result.portraitPrompt();
            portraitAttempts.clear();
            portraitAttempts.addAll(result.portraitAttempts());
            finalPortraitQualityReport = result.finalPortraitQualityReport();
            fashionAnalysis = result.fashionAnalysis();
            attempts.clear();
            attempts.addAll(result.attempts());
            finalQualityReport = result.finalQualityReport();
            reply = result.reply();
            updatedAt = Instant.now();
        }

        synchronized void fail(Throwable exception) {
            if (status == JobStatus.CANCELLED) {
                return;
            }
            PipelineStage failedStage = stage;
            status = JobStatus.FAILED;
            stage = PipelineStage.FAILED;
            message = "生成流程失败";
            error = rootMessage(exception);
            updatedAt = Instant.now();
            errorDetails = diagnosticLog(id, failedStage, updatedAt, exception);
        }

        synchronized String errorMessage() {
            return error;
        }

        synchronized String errorDetails() {
            return errorDetails;
        }

        synchronized JobStepView addEvent(String eventType, String eventMessage, String resultJson) {
            JobStepView event = new JobStepView(
                    nextEventId++, eventType, stage, eventMessage, resultJson, Instant.now());
            events.add(event);
            return event;
        }

        synchronized List<JobStepView> events() {
            return List.copyOf(events);
        }

        synchronized JobPersistenceSnapshot persistenceSnapshot() {
            return new JobPersistenceSnapshot(view(), originalImage, clothingImage, finalImage);
        }

        private boolean isTerminal() {
            return status == JobStatus.SUCCESS || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
        }

        synchronized Path clothingPath() {
            return clothingImage;
        }

        synchronized Path originalImagePath() {
            return originalImage;
        }

        synchronized Path finalImagePath() {
            return finalImage;
        }

        synchronized Path attemptPath(int attemptNumber) {
            return attempts.stream()
                    .filter(attempt -> attempt.attemptNumber() == attemptNumber)
                    .map(OutfitAttempt::image)
                    .findFirst()
                    .orElse(null);
        }

        synchronized Path portraitAttemptPath(int attemptNumber) {
            return portraitAttempts.stream()
                    .filter(attempt -> attempt.attemptNumber() == attemptNumber)
                    .map(PortraitAttempt::image)
                    .findFirst()
                    .orElse(null);
        }

        synchronized JobView view() {
            String originalUrl = originalImage == null ? null : "/api/generations/" + id + "/original";
            String clothingUrl = clothingImage == null ? null : "/api/generations/" + id + "/clothing";
            String finalUrl = finalImage == null ? null : "/api/generations/" + id + "/final";
            List<OutfitAttemptView> attemptViews = attempts.stream()
                    .map(attempt -> new OutfitAttemptView(
                            attempt.attemptNumber(),
                            "/api/generations/" + id + "/attempts/" + attempt.attemptNumber() + "/image",
                            attempt.prompt(),
                            attempt.qualityReport(),
                            attempt.selected()))
                    .toList();
            List<PortraitAttemptView> portraitAttemptViews = portraitAttempts.stream()
                    .map(attempt -> new PortraitAttemptView(
                            attempt.attemptNumber(),
                            "/api/generations/" + id + "/portrait-attempts/"
                                    + attempt.attemptNumber() + "/image",
                            attempt.prompt(),
                            attempt.qualityReport(),
                            attempt.selected()))
                    .toList();
            return new JobView(
                    id,
                    status,
                    stage,
                    message,
                    prompt,
                    portraitGenerationMode,
                    originalUrl,
                    clothingUrl,
                    clothingImage == null ? null : clothingImage.getFileName().toString(),
                    clothingMatchName,
                    clothingMatchPercentage,
                    clothingMatchRule,
                    finalUrl,
                    portraitPrompt,
                    portraitAttemptViews,
                    finalPortraitQualityReport,
                    fashionAnalysis,
                    attemptViews,
                    finalQualityReport,
                    reply,
                    error,
                    errorDetails,
                    createdAt,
                    updatedAt);
        }
    }

    private static String diagnosticLog(
            UUID jobId, PipelineStage failedStage, Instant failedAt, Throwable exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        return """
                Fashion Image Agent 调试日志
                时间: %s
                任务 ID: %s
                失败阶段: %s
                错误摘要: %s

                完整异常堆栈:
                %s""".formatted(failedAt, jobId, failedStage, rootMessage(exception), stackTrace);
    }
}
