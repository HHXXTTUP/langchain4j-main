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
        return compose(video, bgmName, outputName, false, null, null, null, null);
    }

    public BgmJobView compose(MultipartFile video, String bgmName, String outputName, boolean ending, String endingBgmName) {
        return compose(video, bgmName, outputName, ending, endingBgmName, null, null, null);
    }

    public BgmJobView compose(MultipartFile video, String bgmName, String outputName, boolean ending,
                              String endingBgmName, Double cutSeconds, Double holdSeconds, String effect) {
        if (video == null || video.isEmpty()) throw new IllegalArgumentException("请上传原视频");
        Path bgm = resolveBgm(bgmName); UUID id = UUID.randomUUID();
        Path endingBgm = ending ? resolveEndingBgm(endingBgmName) : null;
        validateEndingOptions(ending, cutSeconds, holdSeconds);
        double requestedCut = cutSeconds == null ? -1 : cutSeconds;
        double requestedHold = holdSeconds == null ? -1 : holdSeconds;
        String selectedEffect = normalizeEffect(effect);
        String safeOutputName = normalizeOutputName(outputName, video.getOriginalFilename());
        Path outputDirectory = effectiveOutputDirectory();
        try {
            Files.createDirectories(outputDirectory);
            Path work = outputDirectory.resolve("bgm-compose-" + id); Files.createDirectories(work);
            String original = video.getOriginalFilename() == null ? "source.mp4" : video.getOriginalFilename();
            Path source = work.resolve("source" + extension(original)); video.transferTo(source);
            Path output = outputDirectory.resolve(safeOutputName);
            Job job = new Job(id, original, bgm.getFileName().toString(), endingBgm == null ? null : endingBgm.getFileName().toString(),
                    safeOutputName, work, output, requestedCut, requestedHold, selectedEffect, Instant.now()); jobs.put(id, job);
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
        try {
            job.status = "PROCESSING";
            if (endingBgm == null) {
                job.message = "正在按原视频时长循环并裁剪 BGM";
                media.addBackgroundMusic(source, bgm, job.output, job.work.resolve("ffmpeg.log"));
            } else {
                job.message = job.cutSeconds > 0 && job.holdSeconds > 0
                        ? "正在截取前 " + formatSeconds(job.cutSeconds) + " 秒并定格 " + formatSeconds(job.holdSeconds) + " 秒，应用" + effectLabel(job.effect)
                        : "正在按默认规则处理末尾 3.5 秒定格，应用" + effectLabel(job.effect);
                media.addBackgroundMusicWithEnding(source, bgm, endingBgm, job.output, job.work.resolve("ffmpeg.log"),
                        job.cutSeconds, job.holdSeconds, job.effect);
            }
            job.status = "SUCCESS"; job.message = "视频合成完成";
        }
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
    private static String normalizeEffect(String effect) {
        if (effect == null || effect.isBlank()) return "SHAKE";
        return switch (effect.trim().toUpperCase()) {
            case "NONE", "SHAKE", "ZOOM", "FLASH" -> effect.trim().toUpperCase();
            default -> throw new IllegalArgumentException("不支持的定格特效：" + effect);
        };
    }
    private static void validateEndingOptions(boolean ending, Double cutSeconds, Double holdSeconds) {
        if (!ending) return;
        if (cutSeconds != null && (!Double.isFinite(cutSeconds) || cutSeconds <= 0)) {
            throw new IllegalArgumentException("截取秒数必须大于 0");
        }
        if (holdSeconds != null && (!Double.isFinite(holdSeconds) || holdSeconds < 0.1 || holdSeconds > 30)) {
            throw new IllegalArgumentException("定格秒数必须在 0.1 到 30 秒之间");
        }
    }
    private static String effectLabel(String effect) {
        return switch (normalizeEffect(effect)) {
            case "NONE" -> "无特效";
            case "ZOOM" -> "慢速放大";
            case "FLASH" -> "闪白强调";
            default -> "轻微震动";
        };
    }
    private static String formatSeconds(double seconds) { return String.format(java.util.Locale.ROOT, "%.2f", seconds); }
    public record BgmFile(String name, String label, long size) {}
    public record BgmJobView(UUID id, String sourceFileName, String bgmName, String endingBgmName, String status, String message, String error, String outputUrl, String outputFileName, Instant createdAt, Double cutSeconds, Double holdSeconds, String effect) {}
    private static final class Job {
        private final UUID id; private final String source; private final String bgm; private final String endingBgm; private final String outputName; private final Path work; private final Path output; private final double cutSeconds; private final double holdSeconds; private final String effect; private final Instant created;
        private volatile String status = "QUEUED"; private volatile String message = "已接收合成任务"; private volatile String error;
        private Job(UUID id, String source, String bgm, String endingBgm, String outputName, Path work, Path output, double cutSeconds, double holdSeconds, String effect, Instant created) { this.id=id; this.source=source; this.bgm=bgm; this.endingBgm=endingBgm; this.outputName=outputName; this.work=work; this.output=output; this.cutSeconds=cutSeconds; this.holdSeconds=holdSeconds; this.effect=effect; this.created=created; }
        private BgmJobView view() { return new BgmJobView(id, source, bgm, endingBgm, status, message, error, status.equals("SUCCESS") ? "/api/video-bgm-compositions/" + id + "/output" : null, outputName, created, cutSeconds < 0 ? null : cutSeconds, holdSeconds < 0 ? null : holdSeconds, effect); }
    }
}
