package dev.learning.fashionagent.video;

import dev.learning.fashionagent.account.AccountContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BgmVideoCompositionService {
    private static final List<String> AUDIO_EXTENSIONS = List.of(".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg");
    private final VideoMediaProcessor media;
    private final Executor executor;
    private final Path bgmDirectory;
    private final Path outputDirectory;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public BgmVideoCompositionService(VideoMediaProcessor media, @Qualifier("storyVideoExecutor") Executor executor,
                                      @Value("${video.bgm-directory:E:/ai-workspace/langchain4j-main/langchain4j-main/learning-examples/fashion-image-agent-demo/bgm}") String bgmDirectory,
                                      @Value("${video.bgm-output-directory:E:/AI影视复刻}") String outputDirectory) {
        this.media = media; this.executor = executor;
        this.bgmDirectory = Path.of(bgmDirectory).toAbsolutePath().normalize();
        this.outputDirectory = Path.of(outputDirectory).toAbsolutePath().normalize();
    }

    public List<BgmFile> listBgm() {
        Path bgmDirectory = effectiveBgmDirectory();
        try {
            if (!Files.isDirectory(bgmDirectory)) return List.of();
            try (var stream = Files.list(bgmDirectory)) {
                return stream.filter(Files::isRegularFile).filter(this::isAudio)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .map(path -> new BgmFile(path.getFileName().toString(), path.getFileName().toString(), fileSize(path))).toList();
            }
        } catch (IOException e) { throw new IllegalStateException("无法读取 BGM 目录: " + bgmDirectory, e); }
    }

    public BgmJobView compose(MultipartFile video, String bgmName, String outputName) {
        return compose(video, bgmName, outputName, false, null);
    }

    public BgmJobView compose(MultipartFile video, String bgmName, String outputName, boolean ending, String endingBgmName) {
        if (video == null || video.isEmpty()) throw new IllegalArgumentException("请上传原视频");
        Path bgm = resolveBgm(bgmName); UUID id = UUID.randomUUID();
        Path endingBgm = ending ? resolveEndingBgm(endingBgmName) : null;
        String safeOutputName = normalizeOutputName(outputName, video.getOriginalFilename());
        Path outputDirectory = effectiveOutputDirectory();
        try {
            Files.createDirectories(outputDirectory);
            Path work = outputDirectory.resolve("bgm-compose-" + id); Files.createDirectories(work);
            String original = video.getOriginalFilename() == null ? "source.mp4" : video.getOriginalFilename();
            Path source = work.resolve("source" + extension(original)); video.transferTo(source);
            Path output = outputDirectory.resolve(safeOutputName);
            Job job = new Job(id, original, bgm.getFileName().toString(), endingBgm == null ? null : endingBgm.getFileName().toString(), safeOutputName, work, output, Instant.now()); jobs.put(id, job);
            executor.execute(() -> run(job, source, bgm, endingBgm)); return job.view();
        } catch (IOException e) { throw new IllegalStateException("无法保存原视频", e); }
    }

    public BgmJobView compose(MultipartFile video, String bgmName) { return compose(video, bgmName, null); }

    public List<BgmFile> listEndingBgm() {
        Path directory = effectiveBgmDirectory().resolve("结尾").normalize();
        try {
            if (!Files.isDirectory(directory)) return List.of();
            try (var stream = Files.list(directory)) {
                return stream.filter(Files::isRegularFile).filter(this::isAudio).sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).map(path -> new BgmFile(path.getFileName().toString(), path.getFileName().toString(), fileSize(path))).toList();
            }
        } catch (IOException e) { throw new IllegalStateException("无法读取结尾 BGM 目录: " + directory, e); }
    }

    public Path preview(String name, boolean ending) {
        return ending ? resolveEndingBgm(name) : resolveBgm(name);
    }

    public BgmJobView get(UUID id) { Job job = jobs.get(id); if (job == null) throw new IllegalArgumentException("视频合成任务不存在: " + id); return job.view(); }
    public Path output(UUID id) { Job job = jobs.get(id); if (job == null || job.output == null || !Files.isRegularFile(job.output)) throw new IllegalStateException("视频尚未合成完成"); return job.output; }

    private void run(Job job, Path source, Path bgm, Path endingBgm) {
        try { job.status = "PROCESSING"; job.message = endingBgm == null ? "正在按原视频时长循环并裁剪 BGM" : "正在处理最后 3.5 秒定格震动并混入结尾 BGM"; if (endingBgm == null) media.addBackgroundMusic(source, bgm, job.output, job.work.resolve("ffmpeg.log")); else media.addBackgroundMusicWithEnding(source, bgm, endingBgm, job.output, job.work.resolve("ffmpeg.log")); job.status = "SUCCESS"; job.message = "视频合成完成"; }
        catch (Exception e) { job.status = "FAILED"; job.message = "视频合成失败"; job.error = rootMessage(e); }
    }

    private static String normalizeOutputName(String requested, String original) {
        String value = requested == null ? "" : requested.trim();
        if (value.isBlank()) {
            String source = original == null ? "合成视频" : original;
            int dot = source.lastIndexOf('.');
            value = dot > 0 ? source.substring(0, dot) + "-合成" : source + "-合成";
        }
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) throw new IllegalArgumentException("视频名称不能为空");
        if (!value.toLowerCase().endsWith(".mp4")) value += ".mp4";
        return value;
    }

    private Path resolveBgm(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("请选择 BGM");
        Path bgmDirectory = effectiveBgmDirectory();
        Path candidate = bgmDirectory.resolve(name).normalize();
        if (!candidate.getParent().equals(bgmDirectory) || !Files.isRegularFile(candidate) || !isAudio(candidate)) throw new IllegalArgumentException("BGM 不存在或格式不支持");
        return candidate;
    }
    private Path resolveEndingBgm(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("已勾选首尾，请选择结尾 BGM");
        Path directory = effectiveBgmDirectory().resolve("结尾").normalize(); Path candidate = directory.resolve(name).normalize();
        if (!candidate.getParent().equals(directory) || !Files.isRegularFile(candidate) || !isAudio(candidate)) throw new IllegalArgumentException("结尾 BGM 不存在或格式不支持"); return candidate;
    }
    private Path effectiveBgmDirectory() { return Path.of(AccountContext.value("bgmDirectory", bgmDirectory.toString())).toAbsolutePath().normalize(); }
    private Path effectiveOutputDirectory() { return Path.of(AccountContext.value("storyOutputDirectory", outputDirectory.toString())).toAbsolutePath().normalize(); }
    private boolean isAudio(Path path) { String lower = path.getFileName().toString().toLowerCase(); return AUDIO_EXTENSIONS.stream().anyMatch(lower::endsWith); }
    private static long fileSize(Path path) { try { return Files.size(path); } catch (IOException e) { return 0; } }
    private static String extension(String name) { int i = name.lastIndexOf('.'); return i < 0 ? ".mp4" : name.substring(i).toLowerCase(); }
    private static String rootMessage(Throwable e) { Throwable c = e; while (c.getCause() != null) c = c.getCause(); return c.getMessage() == null ? c.toString() : c.getMessage(); }
    public record BgmFile(String name, String label, long size) {}
    public record BgmJobView(UUID id, String sourceFileName, String bgmName, String endingBgmName, String status, String message, String error, String outputUrl, String outputFileName, Instant createdAt) {}
    private static final class Job {
        private final UUID id; private final String source; private final String bgm; private final String endingBgm; private final String outputName; private final Path work; private final Path output; private final Instant created;
        private volatile String status = "QUEUED"; private volatile String message = "已接收合成任务"; private volatile String error;
        private Job(UUID id, String source, String bgm, String endingBgm, String outputName, Path work, Path output, Instant created) { this.id=id; this.source=source; this.bgm=bgm; this.endingBgm=endingBgm; this.outputName=outputName; this.work=work; this.output=output; this.created=created; }
        private BgmJobView view() { return new BgmJobView(id, source, bgm, endingBgm, status, message, error, status.equals("SUCCESS") ? "/api/video-bgm-compositions/" + id + "/output" : null, outputName, created); }
    }
}
