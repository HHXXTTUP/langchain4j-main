package dev.learning.fashionagent.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.config.RunningHubProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class VideoMediaProcessor {

    private final RunningHubProperties properties;
    private final ObjectMapper objectMapper;

    public VideoMediaProcessor(RunningHubProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean available() {
        return commandAvailable(ffmpegCommand()) && commandAvailable(ffprobeCommand());
    }

    public void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException(
                    "视频拆分和合并需要 FFmpeg。请安装 FFmpeg，并配置 FFMPEG_PATH 与 FFPROBE_PATH");
        }
    }

    public VideoProbe probe(Path video) {
        requireFile(video, "待探测视频");
        List<String> command = List.of(
                ffprobeCommand(),
                "-v", "error",
                "-count_frames",
                "-show_entries", "format=duration:stream=codec_type,width,height,r_frame_rate,nb_read_frames",
                "-of", "json",
                video.toAbsolutePath().toString());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            Process process = builder.start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("FFprobe 读取视频信息超时：" + video);
            }
            String json = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("FFprobe 无法读取视频：" + video);
            }
            JsonNode root = objectMapper.readTree(json);
            double duration = root.path("format").path("duration").asDouble(0);
            int width = 0;
            int height = 0;
            double frameRate = 0;
            long frameCount = 0;
            boolean hasAudio = false;
            for (JsonNode stream : root.path("streams")) {
                String type = stream.path("codec_type").asText("");
                if ("video".equals(type) && width == 0) {
                    width = stream.path("width").asInt(0);
                    height = stream.path("height").asInt(0);
                    frameRate = rate(stream.path("r_frame_rate").asText("0/1"));
                    frameCount = stream.path("nb_read_frames").asLong(0);
                } else if ("audio".equals(type)) {
                    hasAudio = true;
                }
            }
            if (duration <= 0 || width <= 0 || height <= 0) {
                throw new IllegalStateException("视频缺少有效的视频流或时长：" + video);
            }
            return new VideoProbe(duration, width, height, frameRate, frameCount, hasAudio);
        } catch (IOException exception) {
            throw new IllegalStateException("无法执行 FFprobe：" + ffprobeCommand(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFprobe 被中断", exception);
        }
    }

    public Path extractFrameAt(Path source, double seconds, Path output, Path logFile) {
        requireAvailable();
        requireFile(source, "待抽帧视频");
        List<String> command = List.of(
                ffmpegCommand(), "-y", "-ss", decimal(Math.max(0, seconds)),
                "-i", source.toAbsolutePath().toString(), "-frames:v", "1",
                "-q:v", "2", output.toAbsolutePath().toString());
        run(command, logFile, properties.getFfmpegTimeout());
        return output;
    }

    public Path extractLastFrame(Path source, Path output, Path logFile) {
        VideoProbe probe = probe(source);
        return extractFrameAt(source, Math.max(0, probe.durationSeconds() - 0.05), output, logFile);
    }

    public Path concatSegments(List<Path> segments, Path output, Path logFile) {
        requireAvailable();
        if (segments == null || segments.isEmpty()) throw new IllegalArgumentException("至少需要一个视频片段");
        try {
            Path list = logFile.resolveSibling("concat-list.txt");
            String content = segments.stream()
                    .map(path -> "file '" + path.toAbsolutePath().toString().replace("'", "'\\''") + "'")
                    .reduce((a, b) -> a + System.lineSeparator() + b).orElseThrow();
            Files.writeString(list, content, StandardCharsets.UTF_8);
            run(List.of(ffmpegCommand(), "-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
                    "-c", "copy", "-movflags", "+faststart", output.toAbsolutePath().toString()),
                    logFile, properties.getFfmpegTimeout());
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建视频合并清单", exception);
        }
    }

    public SplitResult split(Path source, Path workDirectory) {
        requireAvailable();
        validateMotionInputConfiguration();
        VideoProbe sourceProbe = probe(source);
        double halfDuration = sourceProbe.durationSeconds() / 2.0;
        if (halfDuration < 0.5) {
            throw new IllegalArgumentException("参考视频过短，无法等分为两个有效片段：" + sourceProbe.durationSeconds() + " 秒");
        }
        try {
            Files.createDirectories(workDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建视频任务目录：" + workDirectory, exception);
        }
        Path first = workDirectory.resolve("segment-01.mp4");
        Path second = workDirectory.resolve("segment-02.mp4");
        encodeSegment(source, 0, halfDuration, first, workDirectory.resolve("split-01.log"));
        encodeSegment(source, halfDuration, halfDuration, second, workDirectory.resolve("split-02.log"));
        return new SplitResult(first, second, sourceProbe, halfDuration);
    }

    public Path prepareMotionReferenceImage(Path source, Path workDirectory, int segmentNumber) {
        requireAvailable();
        requireFile(source, "动作迁移人物参考图");
        validateMotionInputConfiguration();
        Path target = workDirectory.resolve("motion-reference-0" + segmentNumber + ".png");
        List<String> command = List.of(
                ffmpegCommand(), "-y",
                "-i", source.toAbsolutePath().toString(),
                "-vf", motionInputScaleFilter() + ",format=rgb24",
                "-frames:v", "1",
                target.toAbsolutePath().toString());
        run(command, workDirectory.resolve("motion-reference-0" + segmentNumber + ".log"), properties.getFfmpegTimeout());
        return target;
    }

    public VideoProbe requireMotionInputVideo(Path video) {
        VideoProbe input = probe(video);
        long pixels = (long) input.width() * input.height();
        if (pixels > properties.getVideoMotionInputMaxPixels()) {
            throw new IllegalStateException("动作迁移视频输入分辨率过高："
                    + input.width() + "x" + input.height() + "=" + pixels
                    + " 像素，限制为 " + properties.getVideoMotionInputMaxPixels() + " 像素");
        }
        if (input.frameCount() > properties.getVideoMotionMaxFrames()) {
            throw new IllegalStateException("动作迁移视频输入帧数过多：" + input.frameCount()
                    + " 帧，限制为 " + properties.getVideoMotionMaxFrames() + " 帧");
        }
        long pixelFrames = pixels * input.frameCount();
        if (pixelFrames > properties.getVideoMotionMaxPixelFrames()) {
            throw new IllegalStateException("动作迁移视频时空像素预算超限：" + pixelFrames
                    + " 像素帧，限制为 " + properties.getVideoMotionMaxPixelFrames() + " 像素帧");
        }
        return input;
    }

    public String motionInputSize() {
        return properties.getVideoMotionInputWidth() + "x" + properties.getVideoMotionInputHeight();
    }

    public String motionInputProfile() {
        return motionInputSize() + " / " + properties.getVideoMotionInputFrameRate()
                + "fps / 最多 " + properties.getVideoMotionMaxFrames() + " 帧";
    }

    public Path normalizeGeneratedSegment(
            Path generatedVideo,
            Path sourceSegment,
            double expectedDuration,
            Path output,
            Path logFile) {
        requireFile(generatedVideo, "动作迁移视频");
        requireFile(sourceSegment, "原始视频片段");
        String filter = "scale=" + properties.getVideoOutputWidth() + ":" + properties.getVideoOutputHeight()
                + ":force_original_aspect_ratio=decrease,pad=" + properties.getVideoOutputWidth() + ":"
                + properties.getVideoOutputHeight() + ":(ow-iw)/2:(oh-ih)/2,setsar=1,fps="
                + properties.getVideoOutputFrameRate() + ",format=yuv420p,tpad=stop_mode=clone:stop_duration="
                + decimal(expectedDuration);
        List<String> command = new ArrayList<>(List.of(
                ffmpegCommand(), "-y",
                "-i", generatedVideo.toAbsolutePath().toString(),
                "-i", sourceSegment.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "1:a:0?",
                "-vf", filter,
                "-t", decimal(expectedDuration),
                "-c:v", "libx264", "-preset", "fast", "-crf", "18",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2",
                "-movflags", "+faststart",
                output.toAbsolutePath().toString()));
        run(command, logFile, properties.getFfmpegTimeout());
        return output;
    }

    public Path muxSeparatedStreams(Path video, Path audio, Path output, Path logFile) {
        requireFile(video, "SnapAny 视频流");
        requireFile(audio, "SnapAny 音频流");
        List<String> command = List.of(
                ffmpegCommand(), "-y",
                "-i", video.toAbsolutePath().toString(),
                "-i", audio.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c:v", "copy",
                "-c:a", "aac",
                "-movflags", "+faststart",
                output.toAbsolutePath().toString());
        run(command, logFile, properties.getFfmpegTimeout());
        return output;
    }

    public Path addBackgroundMusic(Path video, Path backgroundMusic, Path output, Path logFile) {
        requireFile(video, "原视频");
        requireFile(backgroundMusic, "背景音乐");
        requireAvailable();
        double duration = probe(video).durationSeconds();
        if (duration <= 0) throw new IllegalArgumentException("无法读取原视频时长");
        boolean sourceHasAudio = probe(video).hasAudio();
        List<String> command = new ArrayList<>(List.of(ffmpegCommand(), "-y",
                "-i", video.toAbsolutePath().toString(),
                "-stream_loop", "-1", "-i", backgroundMusic.toAbsolutePath().toString()));
        command.addAll(List.of("-map", "0:v:0"));
        if (sourceHasAudio) {
            command.addAll(List.of("-filter_complex", "[1:a]volume=0.35[bgm];[0:a][bgm]amix=inputs=2:duration=first:dropout_transition=2[a]", "-map", "[a]"));
        } else {
            command.addAll(List.of("-map", "1:a:0"));
        }
        command.addAll(List.of("-t", decimal(duration), "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", output.toAbsolutePath().toString()));
        run(command, logFile, properties.getFfmpegTimeout());
        return output;
    }

    /** Holds the final frame for up to 3.5 seconds with a subtle camera shake and mixes an ending cue. */
    public Path addBackgroundMusicWithEnding(Path video, Path backgroundMusic, Path endingMusic, Path output, Path logFile) {
        requireFile(video, "原视频");
        requireFile(backgroundMusic, "背景音乐");
        requireFile(endingMusic, "结尾背景音乐");
        requireAvailable();
        VideoProbe probe = probe(video);
        double duration = probe.durationSeconds();
        double hold = Math.min(3.5, duration);
        double start = Math.max(0, duration - hold);
        String total = decimal(duration);
        String startText = decimal(start);
        String outputSize = probe.width() + ":" + probe.height();
        String videoFilter;
        if (start > 0.04) {
            videoFilter = "[0:v]split=2[preSource][stillSource];"
                    + "[preSource]trim=start=0:end=" + startText + ",setpts=PTS-STARTPTS[pre];"
                    + "[stillSource]trim=start=" + startText + ":end=" + decimal(start + 0.04)
                    + ",setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration=" + decimal(hold - 0.04)
                    + ",crop=iw-24:ih-24:12+8*sin(n*1.7):12+8*cos(n*1.9),scale=" + outputSize + ",setsar=1[holdFrame];"
                    + "[pre][holdFrame]concat=n=2:v=1:a=0[v]";
        } else {
            videoFilter = "[0:v]trim=start=0:end=0.04,setpts=PTS-STARTPTS"
                    + ",tpad=stop_mode=clone:stop_duration=" + decimal(Math.max(0, duration - 0.04))
                    + ",crop=iw-24:ih-24:12+8*sin(n*1.7):12+8*cos(n*1.9),scale=" + outputSize + ",setsar=1[v]";
        }
        String delay = Long.toString(Math.round(start * 1000));
        String audioFilter;
        if (probe.hasAudio()) {
            audioFilter = "[0:a]aresample=48000,volume=1[orig];"
                    + "[1:a]aresample=48000,volume=0.35,atrim=duration=" + total + "[main];"
                    + "[2:a]aresample=48000,adelay=" + delay + "|" + delay + ",volume=1.0,atrim=duration=" + total + "[ending];"
                    + "[orig][main][ending]amix=inputs=3:duration=first:dropout_transition=0[a]";
        } else {
            audioFilter = "[1:a]aresample=48000,volume=0.35,atrim=duration=" + total + "[main];"
                    + "[2:a]aresample=48000,adelay=" + delay + "|" + delay + ",volume=1.0,atrim=duration=" + total + "[ending];"
                    + "[main][ending]amix=inputs=2:duration=first:dropout_transition=0[a]";
        }
        List<String> command = new ArrayList<>(List.of(ffmpegCommand(), "-y",
                "-i", video.toAbsolutePath().toString(),
                "-stream_loop", "-1", "-i", backgroundMusic.toAbsolutePath().toString(),
                "-stream_loop", "-1", "-i", endingMusic.toAbsolutePath().toString(),
                "-filter_complex", videoFilter + ";" + audioFilter,
                "-map", "[v]", "-map", "[a]", "-t", total,
                "-c:v", "libx264", "-preset", "fast", "-crf", "18",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2",
                "-movflags", "+faststart", output.toAbsolutePath().toString()));
        run(command, logFile, properties.getFfmpegTimeout());
        return output;
    }

    public Path mergeWithZoomTransition(
            Path first,
            Path second,
            double segmentDuration,
            Path output,
            Path logFile) {
        VideoProbe firstProbe = probe(first);
        VideoProbe secondProbe = probe(second);
        double transition = Math.min(
                properties.getVideoTransitionDuration().toMillis() / 1000.0,
                Math.max(0.2, segmentDuration / 2.0));
        StringBuilder filter = new StringBuilder();
        filter.append("[0:v]tpad=stop_mode=clone:stop_duration=")
                .append(decimal(transition))
                .append(",setpts=PTS-STARTPTS[v0];")
                .append("[1:v]setpts=PTS-STARTPTS[v1];")
                .append("[v0][v1]xfade=transition=zoomin:duration=")
                .append(decimal(transition))
                .append(":offset=")
                .append(decimal(segmentDuration))
                .append("[v]");
        boolean hasAudio = firstProbe.hasAudio() && secondProbe.hasAudio();
        if (hasAudio) {
            filter.append(";[0:a][1:a]concat=n=2:v=0:a=1[a]");
        }
        List<String> command = new ArrayList<>(List.of(
                ffmpegCommand(), "-y",
                "-i", first.toAbsolutePath().toString(),
                "-i", second.toAbsolutePath().toString(),
                "-filter_complex", filter.toString(),
                "-map", "[v]"));
        if (hasAudio) {
            command.addAll(List.of("-map", "[a]"));
        }
        command.addAll(List.of(
                "-t", decimal(segmentDuration * 2),
                "-c:v", "libx264", "-preset", "fast", "-crf", "18",
                "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart",
                output.toAbsolutePath().toString()));
        run(command, logFile, properties.getFfmpegTimeout());
        return output;
    }

    public VideoQualityReport inspect(Path source, Path result) {
        VideoProbe sourceProbe = probe(source);
        VideoProbe resultProbe = probe(result);
        List<String> issues = new ArrayList<>();
        int score = 30;
        double durationDifference = Math.abs(sourceProbe.durationSeconds() - resultProbe.durationSeconds());
        if (durationDifference <= 0.35) {
            score += 40;
        } else if (durationDifference <= 1.0) {
            score += 25;
            issues.add("最终视频时长与原视频相差 " + decimal(durationDifference) + " 秒");
        } else {
            issues.add("最终视频时长偏差过大：" + decimal(durationDifference) + " 秒");
        }
        if (!sourceProbe.hasAudio() || resultProbe.hasAudio()) {
            score += 20;
        } else {
            issues.add("原视频包含音频，但最终视频未检测到音频流");
        }
        if (resultProbe.width() == properties.getVideoOutputWidth()
                && resultProbe.height() == properties.getVideoOutputHeight()
                && Math.abs(resultProbe.frameRate() - properties.getVideoOutputFrameRate()) <= 1) {
            score += 10;
        } else {
            issues.add("最终视频分辨率或帧率未达到标准化配置");
        }
        return new VideoQualityReport(
                score,
                score >= 70,
                sourceProbe.durationSeconds(),
                resultProbe.durationSeconds(),
                resultProbe.width(),
                resultProbe.height(),
                resultProbe.frameRate(),
                sourceProbe.hasAudio(),
                resultProbe.hasAudio(),
                issues);
    }

    private void encodeSegment(Path source, double start, double duration, Path output, Path logFile) {
        String filter = motionInputScaleFilter() + ",fps="
                + properties.getVideoMotionInputFrameRate() + ",format=yuv420p";
        List<String> command = List.of(
                ffmpegCommand(), "-y",
                "-i", source.toAbsolutePath().toString(),
                "-ss", decimal(start),
                "-t", decimal(duration),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-vf", filter,
                "-frames:v", Integer.toString(properties.getVideoMotionMaxFrames()),
                "-c:v", "libx264", "-preset", "fast", "-crf", "18",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2",
                "-avoid_negative_ts", "make_zero", "-movflags", "+faststart",
                output.toAbsolutePath().toString());
        run(command, logFile, properties.getFfmpegTimeout());
    }

    private String motionInputScaleFilter() {
        int width = properties.getVideoMotionInputWidth();
        int height = properties.getVideoMotionInputHeight();
        return "scale=" + width + ":" + height
                + ":force_original_aspect_ratio=decrease:flags=lanczos,pad="
                + width + ":" + height + ":(ow-iw)/2:(oh-ih)/2,setsar=1";
    }

    private void validateMotionInputConfiguration() {
        int width = properties.getVideoMotionInputWidth();
        int height = properties.getVideoMotionInputHeight();
        long pixels = (long) width * height;
        int frameRate = properties.getVideoMotionInputFrameRate();
        int maxFrames = properties.getVideoMotionMaxFrames();
        long pixelFrames = pixels * maxFrames;
        if (width <= 0 || height <= 0 || pixels > properties.getVideoMotionInputMaxPixels()) {
            throw new IllegalStateException("动作迁移输入尺寸配置无效：" + width + "x" + height + "=" + pixels
                    + " 像素，必须大于 0 且不超过 " + properties.getVideoMotionInputMaxPixels() + " 像素");
        }
        if (frameRate <= 0 || maxFrames <= 0 || pixelFrames > properties.getVideoMotionMaxPixelFrames()) {
            throw new IllegalStateException("动作迁移时间维度配置无效：" + frameRate + "fps，最多 " + maxFrames
                    + " 帧，共 " + pixelFrames + " 像素帧；上限为 "
                    + properties.getVideoMotionMaxPixelFrames() + " 像素帧");
        }
    }

    private static void requireFile(Path file, String label) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException(label + "不存在：" + file);
        }
    }

    private boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String ffmpegCommand() {
        return resolveBundledCommand(properties.getFfmpegPath());
    }

    private String ffprobeCommand() {
        return resolveBundledCommand(properties.getFfprobePath());
    }

    private static String resolveBundledCommand(String configured) {
        Path value = Path.of(configured);
        if (value.isAbsolute()) {
            return value.normalize().toString();
        }
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                workingDirectory.resolve(value),
                workingDirectory.resolve("learning-examples")
                        .resolve("fashion-image-agent-demo")
                        .resolve(value));
        return candidates.stream()
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .findFirst()
                .map(Path::toString)
                .orElse(configured);
    }

    private static void run(List<String> command, Path logFile, Duration timeout) {
        try {
            Files.createDirectories(logFile.toAbsolutePath().getParent());
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("FFmpeg 执行超时，日志：" + logFile);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("FFmpeg 执行失败，退出码 " + process.exitValue() + "，日志：" + logFile);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法执行 FFmpeg，命令：" + command.get(0), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FFmpeg 执行被中断", exception);
        }
    }

    private static double rate(String fraction) {
        String[] values = fraction.split("/", 2);
        try {
            double numerator = Double.parseDouble(values[0]);
            double denominator = values.length == 2 ? Double.parseDouble(values[1]) : 1;
            return denominator == 0 ? 0 : numerator / denominator;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    public record VideoProbe(
            double durationSeconds,
            int width,
            int height,
            double frameRate,
            long frameCount,
            boolean hasAudio) {}

    public record SplitResult(
            Path firstSegment,
            Path secondSegment,
            VideoProbe sourceProbe,
            double segmentDurationSeconds) {}
}
