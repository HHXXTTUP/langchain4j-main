package dev.learning.fashionagent.video;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.job.GenerationJobService;
import dev.learning.fashionagent.job.JobStatus;
import dev.learning.fashionagent.job.JobView;
import dev.learning.fashionagent.service.GeneratedTaskFileDeletionService;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class VideoGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoGenerationService.class);

    private final Map<UUID, VideoJob> jobs = new ConcurrentHashMap<>();
    private final GenerationJobService imageJobs;
    private final VideoCatalogService catalog;
    private final VideoMediaProcessor mediaProcessor;
    private final VideoMotionTransferService motionTransfer;
    private final VideoGenerationRepository repository;
    private final RunningHubProperties properties;
    private final Executor queueExecutor;
    private final Executor segmentExecutor;
    private final GeneratedTaskFileDeletionService fileDeletionService;
    private final VideoResultPackageService resultPackageService;

    @Autowired
    public VideoGenerationService(
            GenerationJobService imageJobs,
            VideoCatalogService catalog,
            VideoMediaProcessor mediaProcessor,
            VideoMotionTransferService motionTransfer,
            VideoGenerationRepository repository,
            RunningHubProperties properties,
            @Qualifier("videoPipelineExecutor") Executor queueExecutor,
            @Qualifier("videoSegmentExecutor") Executor segmentExecutor,
            GeneratedTaskFileDeletionService fileDeletionService,
            VideoResultPackageService resultPackageService) {
        this.imageJobs = imageJobs;
        this.catalog = catalog;
        this.mediaProcessor = mediaProcessor;
        this.motionTransfer = motionTransfer;
        this.repository = repository;
        this.properties = properties;
        this.queueExecutor = queueExecutor;
        this.segmentExecutor = segmentExecutor;
        this.fileDeletionService = fileDeletionService;
        this.resultPackageService = resultPackageService;
    }

    VideoGenerationService(
            GenerationJobService imageJobs,
            VideoCatalogService catalog,
            VideoMediaProcessor mediaProcessor,
            VideoMotionTransferService motionTransfer,
            VideoGenerationRepository repository,
            RunningHubProperties properties,
            Executor queueExecutor,
            Executor segmentExecutor) {
        this.imageJobs = imageJobs;
        this.catalog = catalog;
        this.mediaProcessor = mediaProcessor;
        this.motionTransfer = motionTransfer;
        this.repository = repository;
        this.properties = properties;
        this.queueExecutor = queueExecutor;
        this.segmentExecutor = segmentExecutor;
        this.fileDeletionService = null;
        this.resultPackageService = null;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverInterrupted(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext) {
            int count = repository.recoverInterrupted();
            if (count > 0) {
                LOGGER.warn("Recovered {} interrupted video generation task(s)", count);
            }
        }
    }

    public synchronized VideoGenerationView create(UUID sourceJobId) {
        validateCommonRequirements();
        validateSource(sourceJobId, activeSourceJobIds());
        return enqueue(sourceJobId);
    }

    public synchronized List<VideoGenerationView> createBatch(List<UUID> sourceJobIds) {
        if (sourceJobIds == null) {
            throw new IllegalArgumentException("请选择需要生成视频的图片任务");
        }
        List<UUID> uniqueIds = new LinkedHashSet<>(sourceJobIds).stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("请选择需要生成视频的图片任务");
        }
        if (uniqueIds.size() > 20) {
            throw new IllegalArgumentException("单次最多批量创建 20 个视频任务");
        }
        validateCommonRequirements();
        Set<UUID> activeSourceIds = activeSourceJobIds();
        uniqueIds.forEach(sourceJobId -> validateSource(sourceJobId, activeSourceIds));
        return uniqueIds.stream().map(this::enqueue).toList();
    }

    public synchronized VideoGenerationView retryDownload(UUID id) {
        VideoJob job = jobs.get(id);
        if (job == null) {
            job = repository.find(id)
                    .map(VideoJob::fromView)
                    .orElseThrow(() -> new IllegalArgumentException("视频任务不存在：" + id));
            jobs.put(id, job);
        }
        if (!job.downloadRetryable()) {
            throw new IllegalStateException("该视频任务没有可重新下载的 RunningHub 结果");
        }
        job.prepareDownloadRetry();
        persist(job);
        VideoJob retryJob = job;
        queueExecutor.execute(() -> executeDownloadRetry(retryJob));
        return job.view();
    }

    private void validateCommonRequirements() {
        mediaProcessor.requireAvailable();
        if (catalog.list().isEmpty()) {
            throw new IllegalStateException("video_ai 目录中没有可用视频");
        }
    }

    private void validateSource(UUID sourceJobId, Set<UUID> activeSourceIds) {
        JobView source = imageJobs.get(sourceJobId);
        if (source.status() != JobStatus.SUCCESS) {
            throw new IllegalStateException("只有已完成的图片任务才能生成视频");
        }
        imageJobs.originalImage(sourceJobId);
        imageJobs.finalImage(sourceJobId);
        if (activeSourceIds.contains(sourceJobId)) {
            throw new IllegalStateException("该图片任务已有视频任务正在排队或执行");
        }
    }

    private Set<UUID> activeSourceJobIds() {
        return list().stream()
                .filter(view -> view.status() != VideoGenerationStatus.SUCCESS
                        && view.status() != VideoGenerationStatus.FAILED)
                .map(VideoGenerationView::sourceJobId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private VideoGenerationView enqueue(UUID sourceJobId) {
        VideoJob job = new VideoJob(UUID.randomUUID(), sourceJobId);
        jobs.put(job.id, job);
        persist(job);
        queueExecutor.execute(() -> execute(job));
        return job.view();
    }

    public VideoGenerationView get(UUID id) {
        VideoJob job = jobs.get(id);
        if (job != null) {
            return job.view();
        }
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("视频任务不存在：" + id));
    }

    public List<VideoGenerationView> list() {
        Map<UUID, VideoGenerationView> combined = new ConcurrentHashMap<>();
        repository.list().forEach(view -> combined.put(view.id(), view));
        jobs.values().stream().map(VideoJob::view).forEach(view -> combined.put(view.id(), view));
        return combined.values().stream()
                .sorted(Comparator.comparing(VideoGenerationView::createdAt).reversed())
                .toList();
    }

    public void validateDeletion(UUID id) {
        VideoGenerationStatus status = get(id).status();
        if (status != VideoGenerationStatus.SUCCESS && status != VideoGenerationStatus.FAILED) {
            throw new IllegalStateException("视频任务仍在排队或运行，暂时不能删除");
        }
    }

    public void delete(UUID id) {
        validateDeletion(id);
        deleteTerminalJob(id);
    }

    public void validateDeletionBySourceJob(UUID sourceJobId) {
        relatedJobs(sourceJobId).forEach(view -> validateDeletion(view.id()));
    }

    public void deleteBySourceJob(UUID sourceJobId) {
        List<VideoGenerationView> related = relatedJobs(sourceJobId);
        related.forEach(view -> validateDeletion(view.id()));
        related.forEach(view -> deleteTerminalJob(view.id()));
    }

    private List<VideoGenerationView> relatedJobs(UUID sourceJobId) {
        Map<UUID, VideoGenerationView> combined = new ConcurrentHashMap<>();
        repository.findBySourceJobId(sourceJobId).forEach(view -> combined.put(view.id(), view));
        jobs.values().stream()
                .map(VideoJob::view)
                .filter(view -> sourceJobId.equals(view.sourceJobId()))
                .forEach(view -> combined.put(view.id(), view));
        return List.copyOf(combined.values());
    }

    private void deleteTerminalJob(UUID id) {
        if (fileDeletionService == null) {
            throw new IllegalStateException("任务文件删除服务未配置");
        }
        fileDeletionService.deleteVideoJobDirectory(id);
        jobs.remove(id);
        repository.delete(id);
        LOGGER.info("Video generation job {} and its generated files were deleted", id);
    }

    public Path finalVideo(UUID id) {
        VideoJob job = jobs.get(id);
        Path result = job == null ? repository.finalVideo(id).orElse(null) : job.finalVideo();
        if (result == null || !Files.isRegularFile(result)) {
            throw new IllegalStateException("该视频任务尚未生成最终视频");
        }
        return result;
    }

    public Path openFolder(UUID id) {
        Path video = finalVideo(id).toAbsolutePath().normalize();
        Path folder = video.getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IllegalStateException("最终视频目录不存在：" + video);
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile());
            } else if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", folder.toString()).start();
            } else {
                throw new IllegalStateException("当前运行环境不支持打开本地目录");
            }
            return folder;
        } catch (IOException exception) {
            throw new IllegalStateException("打开最终视频目录失败：" + folder, exception);
        }
    }

    private void execute(VideoJob job) {
        try {
            Path sourceVideo = catalog.selectBalancedVideo();
            Path workDirectory = properties.getGeneratedDirectory()
                    .toAbsolutePath().normalize()
                    .resolve("video-jobs")
                    .resolve(job.id.toString());
            job.transition(VideoGenerationStatus.SPLITTING, "正在等分本地参考视频", sourceVideo);
            persist(job);
            VideoMediaProcessor.SplitResult split = mediaProcessor.split(sourceVideo, workDirectory);

            Path originalImage = imageJobs.originalImage(job.sourceJobId);
            Path outfitImage = imageJobs.finalImage(job.sourceJobId);
            job.transition(
                    VideoGenerationStatus.GENERATING,
                    "视频已等分并压缩为 " + mediaProcessor.motionInputProfile()
                            + "，两个动作迁移片段将使用 Plus 实例并发执行",
                    sourceVideo);
            persist(job);

            job.startDownloading();
            persist(job);
            CompletableFuture<Path> firstFuture = CompletableFuture.supplyAsync(
                    () -> motionTransfer.transferWithRemoteUrl(
                            originalImage,
                            split.firstSegment(),
                            1,
                            workDirectory,
                            message -> updateSegment(job, 1, message),
                            remoteUrl -> updateRemoteUrl(job, 1, remoteUrl)),
                    segmentExecutor);
            CompletableFuture<Path> secondFuture = CompletableFuture.supplyAsync(
                    () -> motionTransfer.transferWithRemoteUrl(
                            outfitImage,
                            split.secondSegment(),
                            2,
                            workDirectory,
                            message -> updateSegment(job, 2, message),
                            remoteUrl -> updateRemoteUrl(job, 2, remoteUrl)),
                    segmentExecutor);
            // One business job owns both Plus requests until both finish, keeping global concurrency at two.
            CompletableFuture.allOf(firstFuture, secondFuture).join();
            Path firstGenerated = firstFuture.join();
            Path secondGenerated = secondFuture.join();
            job.downloadsComplete();
            persist(job);

            completeFromGeneratedSegments(
                    job,
                    sourceVideo,
                    split,
                    originalImage,
                    outfitImage,
                    firstGenerated,
                    secondGenerated,
                    workDirectory);
        } catch (CompletionException exception) {
            fail(job, exception.getCause() == null ? exception : exception.getCause());
        } catch (Throwable exception) {
            fail(job, exception);
        }
    }

    private void executeDownloadRetry(VideoJob job) {
        try {
            Path sourceVideo = job.sourceVideo();
            Path workDirectory = videoWorkDirectory(job.id);
            if (sourceVideo == null || !Files.isRegularFile(sourceVideo)) {
                throw new IllegalStateException("原始参考视频不存在，无法重新下载并合成：" + sourceVideo);
            }
            Path firstSegment = workDirectory.resolve("segment-01.mp4");
            Path secondSegment = workDirectory.resolve("segment-02.mp4");
            if (!Files.isRegularFile(firstSegment) || !Files.isRegularFile(secondSegment)) {
                throw new IllegalStateException("本地视频拆分片段不存在，无法重新下载");
            }
            Path originalImage = imageJobs.originalImage(job.sourceJobId);
            Path outfitImage = imageJobs.finalImage(job.sourceJobId);
            VideoMediaProcessor.VideoProbe sourceProbe = mediaProcessor.probe(sourceVideo);
            VideoMediaProcessor.SplitResult split = new VideoMediaProcessor.SplitResult(
                    firstSegment,
                    secondSegment,
                    sourceProbe,
                    sourceProbe.durationSeconds() / 2.0);
            job.startDownloading();
            persist(job);
            CompletableFuture<Path> firstFuture = CompletableFuture.supplyAsync(
                    () -> motionTransfer.downloadExistingResult(
                            job.firstSegmentRemoteUrl(),
                            workDirectory.resolve("generated-01.mp4"),
                            1,
                            message -> updateSegment(job, 1, message)),
                    segmentExecutor);
            CompletableFuture<Path> secondFuture = CompletableFuture.supplyAsync(
                    () -> motionTransfer.downloadExistingResult(
                            job.secondSegmentRemoteUrl(),
                            workDirectory.resolve("generated-02.mp4"),
                            2,
                            message -> updateSegment(job, 2, message)),
                    segmentExecutor);
            CompletableFuture.allOf(firstFuture, secondFuture).join();
            Path firstGenerated = firstFuture.join();
            Path secondGenerated = secondFuture.join();
            job.downloadsComplete();
            persist(job);
            completeFromGeneratedSegments(
                    job,
                    sourceVideo,
                    split,
                    originalImage,
                    outfitImage,
                    firstGenerated,
                    secondGenerated,
                    workDirectory);
        } catch (CompletionException exception) {
            fail(job, exception.getCause() == null ? exception : exception.getCause());
        } catch (Throwable exception) {
            fail(job, exception);
        }
    }

    private void completeFromGeneratedSegments(
            VideoJob job,
            Path sourceVideo,
            VideoMediaProcessor.SplitResult split,
            Path originalImage,
            Path outfitImage,
            Path firstGenerated,
            Path secondGenerated,
            Path workDirectory) {

            job.transition(VideoGenerationStatus.MERGING, "动作迁移完成，正在恢复原视频音频并加入推进转场", sourceVideo);
            persist(job);
            Path firstNormalized = mediaProcessor.normalizeGeneratedSegment(
                    firstGenerated,
                    split.firstSegment(),
                    split.segmentDurationSeconds(),
                    workDirectory.resolve("normalized-01.mp4"),
                    workDirectory.resolve("normalize-01.log"));
            Path secondNormalized = mediaProcessor.normalizeGeneratedSegment(
                    secondGenerated,
                    split.secondSegment(),
                    split.segmentDurationSeconds(),
                    workDirectory.resolve("normalized-02.mp4"),
                    workDirectory.resolve("normalize-02.log"));
            Path finalVideo = mediaProcessor.mergeWithZoomTransition(
                    firstNormalized,
                    secondNormalized,
                    split.segmentDurationSeconds(),
                    workDirectory.resolve("final-video.mp4"),
                    workDirectory.resolve("merge.log"));

            job.transition(VideoGenerationStatus.QUALITY_CHECKING, "正在检查最终视频时长、画面参数和音频完整性", sourceVideo);
            persist(job);
            VideoQualityReport quality = mediaProcessor.inspect(sourceVideo, finalVideo);
            Path completedVideo = finalVideo;
            Path packageDirectory = null;
            if (resultPackageService != null) {
                VideoResultPackageService.ExportResult exported = resultPackageService.export(
                        job.id,
                        workDirectory,
                        originalImage,
                        outfitImage,
                        finalVideo);
                completedVideo = exported.finalVideo();
                packageDirectory = exported.directory();
            }
            job.complete(sourceVideo, completedVideo, quality, packageDirectory);
            persist(job);
    }

    private Path videoWorkDirectory(UUID id) {
        return properties.getGeneratedDirectory()
                .toAbsolutePath().normalize()
                .resolve("video-jobs")
                .resolve(id.toString());
    }

    private void updateSegment(VideoJob job, int segmentNumber, String message) {
        job.segmentProgress(segmentNumber, message);
        persist(job);
    }

    private void updateRemoteUrl(VideoJob job, int segmentNumber, URI remoteUrl) {
        job.remoteUrl(segmentNumber, remoteUrl.toASCIIString());
        persist(job);
    }

    private void fail(VideoJob job, Throwable exception) {
        LOGGER.error("Video generation job {} failed", job.id, exception);
        job.fail(rootMessage(exception));
        persist(job);
    }

    private void persist(VideoJob job) {
        synchronized (job) {
            repository.save(job.snapshot());
        }
    }

    private static String rootMessage(Throwable throwable) {
        String message = throwable.getMessage();
        Throwable current = throwable.getCause();
        while (current != null && current != throwable) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            throwable = current;
            current = current.getCause();
        }
        return message == null || message.isBlank() ? "视频生成失败" : message;
    }

    private static final class VideoJob {
        private final UUID id;
        private final UUID sourceJobId;
        private Instant createdAt = Instant.now();
        private VideoGenerationStatus status = VideoGenerationStatus.QUEUED;
        private String message = "视频任务已进入单任务队列";
        private Path sourceVideo;
        private Path finalVideo;
        private String firstSegmentStatus = "等待";
        private String secondSegmentStatus = "等待";
        private VideoQualityReport quality;
        private String error;
        private Instant updatedAt = createdAt;
        private String firstSegmentRemoteUrl;
        private String secondSegmentRemoteUrl;
        private boolean downloadRetryable;
        private boolean downloadInProgress;

        private VideoJob(UUID id, UUID sourceJobId) {
            this.id = id;
            this.sourceJobId = sourceJobId;
        }

        private static VideoJob fromView(VideoGenerationView view) {
            VideoJob job = new VideoJob(view.id(), view.sourceJobId());
            job.createdAt = view.createdAt() == null ? Instant.now() : view.createdAt();
            job.updatedAt = view.updatedAt() == null ? job.createdAt : view.updatedAt();
            job.status = view.status();
            job.message = view.message();
            job.firstSegmentStatus = view.firstSegmentStatus();
            job.secondSegmentStatus = view.secondSegmentStatus();
            job.quality = view.qualityReport();
            job.error = view.error();
            job.firstSegmentRemoteUrl = view.firstSegmentRemoteUrl();
            job.secondSegmentRemoteUrl = view.secondSegmentRemoteUrl();
            job.downloadRetryable = view.downloadRetryable();
            if (view.sourceVideoPath() != null && !view.sourceVideoPath().isBlank()) {
                job.sourceVideo = Path.of(view.sourceVideoPath());
            }
            return job;
        }

        synchronized void transition(VideoGenerationStatus status, String message, Path sourceVideo) {
            this.status = status;
            this.message = message;
            this.sourceVideo = sourceVideo;
            this.updatedAt = Instant.now();
        }

        synchronized void startDownloading() {
            this.downloadInProgress = true;
            this.updatedAt = Instant.now();
        }

        synchronized void remoteUrl(int segmentNumber, String remoteUrl) {
            if (segmentNumber == 1) {
                firstSegmentRemoteUrl = remoteUrl;
            } else {
                secondSegmentRemoteUrl = remoteUrl;
            }
            status = VideoGenerationStatus.DOWNLOADING;
            message = "RunningHub 结果已返回，正在下载动作迁移视频";
            updatedAt = Instant.now();
        }

        synchronized void downloadsComplete() {
            downloadInProgress = false;
            downloadRetryable = false;
            updatedAt = Instant.now();
        }

        synchronized void prepareDownloadRetry() {
            status = VideoGenerationStatus.DOWNLOADING;
            message = "正在根据 RunningHub 已完成结果重新下载视频";
            error = null;
            finalVideo = null;
            downloadInProgress = true;
            downloadRetryable = false;
            firstSegmentStatus = "重新下载中";
            secondSegmentStatus = "重新下载中";
            updatedAt = Instant.now();
        }

        synchronized boolean downloadRetryable() {
            return downloadRetryable
                    && firstSegmentRemoteUrl != null && !firstSegmentRemoteUrl.isBlank()
                    && secondSegmentRemoteUrl != null && !secondSegmentRemoteUrl.isBlank();
        }

        synchronized Path sourceVideo() {
            return sourceVideo;
        }

        synchronized String firstSegmentRemoteUrl() {
            return firstSegmentRemoteUrl;
        }

        synchronized String secondSegmentRemoteUrl() {
            return secondSegmentRemoteUrl;
        }

        synchronized void segmentProgress(int segmentNumber, String progress) {
            if (segmentNumber == 1) {
                firstSegmentStatus = progress;
            } else {
                secondSegmentStatus = progress;
            }
            message = "片段1：" + firstSegmentStatus + "；片段2：" + secondSegmentStatus;
            updatedAt = Instant.now();
        }

        synchronized void complete(
                Path sourceVideo,
                Path finalVideo,
                VideoQualityReport quality,
                Path packageDirectory) {
            this.status = VideoGenerationStatus.SUCCESS;
            this.message = packageDirectory == null
                    ? "最终视频已生成并保存到本地"
                    : "原图、换装图和最终视频已归档到 " + packageDirectory;
            this.sourceVideo = sourceVideo;
            this.finalVideo = finalVideo;
            this.quality = quality;
            this.firstSegmentStatus = "完成";
            this.secondSegmentStatus = "完成";
            this.downloadInProgress = false;
            this.downloadRetryable = false;
            this.updatedAt = Instant.now();
        }

        synchronized void fail(String error) {
            this.status = VideoGenerationStatus.FAILED;
            this.downloadRetryable = downloadInProgress
                    && firstSegmentRemoteUrl != null && !firstSegmentRemoteUrl.isBlank()
                    && secondSegmentRemoteUrl != null && !secondSegmentRemoteUrl.isBlank();
            this.downloadInProgress = false;
            this.message = downloadRetryable ? "视频下载失败，可点击重新下载" : "视频生成失败";
            this.error = error;
            this.updatedAt = Instant.now();
        }

        synchronized Path finalVideo() {
            return finalVideo;
        }

        synchronized VideoGenerationSnapshot snapshot() {
            return new VideoGenerationSnapshot(view(), sourceVideo, finalVideo);
        }

        synchronized VideoGenerationView view() {
            return new VideoGenerationView(
                    id,
                    sourceJobId,
                    status,
                    message,
                    sourceVideo == null ? null : sourceVideo.getFileName().toString(),
                    firstSegmentStatus,
                    secondSegmentStatus,
                    finalVideo == null ? null : "/api/video-generations/" + id + "/final",
                    finalVideo == null ? null : finalVideo.getFileName().toString(),
                    quality,
                    error,
                    createdAt,
                    updatedAt,
                    sourceVideo == null ? null : sourceVideo.toAbsolutePath().normalize().toString(),
                    firstSegmentRemoteUrl,
                    secondSegmentRemoteUrl,
                    downloadRetryable);
        }
    }
}
