package dev.learning.fashionagent.video;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.NodeInput;
import dev.learning.fashionagent.integration.runninghub.RunningHubClient;
import dev.learning.fashionagent.integration.runninghub.RunningHubException;
import dev.learning.fashionagent.integration.runninghub.RunningHubTaskRunner;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class VideoMotionTransferService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoMotionTransferService.class);

    private final Semaphore downloadPermit = new Semaphore(1, true);
    private final RunningHubClient client;
    private final RunningHubTaskRunner taskRunner;
    private final RunningHubProperties properties;
    private final VideoMediaProcessor mediaProcessor;

    private enum DownloadChannel {
        JAVA,
        CONSCRYPT,
        POWERSHELL
    }

    private record DownloadAttempt(URI uri, DownloadChannel channel) {}

    public VideoMotionTransferService(
            RunningHubClient client,
            RunningHubTaskRunner taskRunner,
            RunningHubProperties properties,
            VideoMediaProcessor mediaProcessor) {
        this.client = client;
        this.taskRunner = taskRunner;
        this.properties = properties;
        this.mediaProcessor = mediaProcessor;
    }

    public Path transfer(
            Path referenceImage,
            Path referenceVideo,
            int segmentNumber,
            Path workDirectory,
            Consumer<String> progress) {
        return transferWithRemoteUrl(referenceImage, referenceVideo, segmentNumber, workDirectory, progress, ignored -> {});
    }

    public Path transferWithRemoteUrl(
            Path referenceImage,
            Path referenceVideo,
            int segmentNumber,
            Path workDirectory,
            Consumer<String> progress,
            Consumer<URI> remoteUrlConsumer) {
        VideoMediaProcessor.VideoProbe videoProbe = mediaProcessor.requireMotionInputVideo(referenceVideo);
        progress.accept("第 " + segmentNumber + " 段视频输入已限制为 "
                + videoProbe.width() + "x" + videoProbe.height() + "（"
                + videoProbe.frameRate() + "fps / " + videoProbe.frameCount() + " 帧 / "
                + ((long) videoProbe.width() * videoProbe.height() * videoProbe.frameCount())
                + " 像素帧），正在上传");
        String videoFile = client.upload(referenceVideo, properties.getVideoMaxUploadBytes());
        progress.accept("正在生成第 " + segmentNumber + " 张 " + mediaProcessor.motionInputSize() + " 动作迁移参考图");
        Path preparedImage = mediaProcessor.prepareMotionReferenceImage(referenceImage, workDirectory, segmentNumber);
        progress.accept("正在上传第 " + segmentNumber + " 张低分辨率人物参考图");
        String imageFile = client.upload(preparedImage, properties.getVideoMaxUploadBytes());
        List<NodeInput> inputs = workflowInputs(videoFile, imageFile);
        RunningHubTaskRunner.TaskOutput output = taskRunner.runVideo(
                properties.getVideoMotionAppId(),
                inputs,
                status -> progress.accept("第 " + segmentNumber + " 段 RunningHub 状态：" + status));
        URI remoteUri = normalizeRemoteUri(output.url());
        remoteUrlConsumer.accept(remoteUri);
        progress.accept("第 " + segmentNumber + " 段动作迁移完成，正在下载视频");
        return download(
                remoteUri.toASCIIString(),
                workDirectory.resolve("generated-0" + segmentNumber + ".mp4"),
                segmentNumber,
                progress);
    }

    public Path downloadExistingResult(
            String remoteUrl,
            Path target,
            int segmentNumber,
            Consumer<String> progress) {
        return download(remoteUrl, target, segmentNumber, progress);
    }

    private Path download(String remoteUrl, Path target, int segmentNumber, Consumer<String> progress) {
        URI originalUri = normalizeRemoteUri(remoteUrl);
        List<DownloadAttempt> downloadPlan = downloadPlan(originalUri);
        int attempts = Math.max(1, properties.getVideoDownloadMaxAttempts());
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            DownloadAttempt downloadAttempt = downloadPlan.get((attempt - 1) % downloadPlan.size());
            URI uri = downloadAttempt.uri();
            DownloadChannel channel = downloadAttempt.channel();
            try {
                progress.accept("第 " + segmentNumber + " 段视频等待下载通道（第 " + attempt + "/" + attempts
                        + " 次，" + channelName(channel) + "，" + uri.getScheme().toUpperCase(Locale.ROOT) + "）");
                acquireDownloadPermit(segmentNumber);
                try {
                    progress.accept("第 " + segmentNumber + " 段视频正在下载（第 " + attempt + "/" + attempts
                            + " 次，" + channelName(channel) + "，" + uri.getScheme().toUpperCase(Locale.ROOT) + "）");
                    Path downloaded = downloadWithChannel(channel, uri, target);
                    progress.accept("第 " + segmentNumber + " 段视频下载完成");
                    return downloaded;
                } finally {
                    downloadPermit.release();
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < attempts) {
                    LOGGER.warn(
                            "第 {} 段动作迁移视频下载失败，第 {}/{} 次，稍后重试 url={} cause={}",
                            segmentNumber,
                            attempt,
                            attempts,
                            uri,
                            exception.getMessage());
                    progress.accept("第 " + segmentNumber + " 段视频下载失败（第 " + attempt + "/" + attempts
                            + " 次）：" + exception.getMessage() + "；准备重试");
                    sleep(properties.getVideoDownloadRetryDelay());
                }
            }
        }
        throw new RunningHubException("动作迁移视频下载失败，已重试 " + attempts + " 次：" + originalUri, lastFailure);
    }

    private Path downloadWithChannel(DownloadChannel channel, URI uri, Path target) {
        long maxBytes = properties.getVideoMaxDownloadBytes();
        return switch (channel) {
            case JAVA -> client.downloadToFile(uri, target, maxBytes);
            case CONSCRYPT -> client.downloadToFileViaConscrypt(uri, target, maxBytes);
            case POWERSHELL -> client.downloadToFileViaPowerShell(uri, target, maxBytes);
        };
    }

    private static String channelName(DownloadChannel channel) {
        return switch (channel) {
            case JAVA -> "Java HTTPS";
            case CONSCRYPT -> "Conscrypt HTTPS";
            case POWERSHELL -> "PowerShell";
        };
    }

    private List<DownloadAttempt> downloadPlan(URI originalUri) {
        if ("https".equalsIgnoreCase(originalUri.getScheme())) {
            URI httpFallback = properties.isVideoDownloadAllowHttpFallback()
                    ? trustedCosHttpFallback(originalUri)
                    : originalUri;
            if (!httpFallback.equals(originalUri)) {
                return List.of(
                        new DownloadAttempt(originalUri, DownloadChannel.JAVA),
                        new DownloadAttempt(originalUri, DownloadChannel.CONSCRYPT),
                        new DownloadAttempt(originalUri, DownloadChannel.POWERSHELL),
                        new DownloadAttempt(httpFallback, DownloadChannel.JAVA),
                        new DownloadAttempt(httpFallback, DownloadChannel.POWERSHELL));
            }
            return List.of(
                    new DownloadAttempt(originalUri, DownloadChannel.JAVA),
                    new DownloadAttempt(originalUri, DownloadChannel.CONSCRYPT),
                    new DownloadAttempt(originalUri, DownloadChannel.POWERSHELL));
        }
        return List.of(
                new DownloadAttempt(originalUri, DownloadChannel.JAVA),
                new DownloadAttempt(originalUri, DownloadChannel.POWERSHELL));
    }

    private static URI trustedCosHttpFallback(URI originalUri) {
        String host = originalUri.getHost();
        if (host == null) {
            return originalUri;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!normalizedHost.startsWith("rh-images-")
                || !normalizedHost.endsWith(".cos.ap-beijing.myqcloud.com")) {
            return originalUri;
        }
        String value = originalUri.toASCIIString();
        return URI.create("http://" + value.substring("https://".length()));
    }

    private void acquireDownloadPermit(int segmentNumber) {
        try {
            downloadPermit.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunningHubException("等待第 " + segmentNumber + " 段视频下载通道时被中断", exception);
        }
    }

    static URI normalizeRemoteUri(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new RunningHubException("RunningHub 返回的视频下载地址为空");
        }
        String value = remoteUrl.trim();
        value = unwrapMarkdownUrl(value);
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            try {
                return UriComponentsBuilder.fromUriString(value).build().encode().toUri();
            } catch (IllegalArgumentException encodingFailure) {
                throw new RunningHubException("RunningHub 返回的视频下载地址不合法：" + value, encodingFailure);
            }
        }
    }

    private static String unwrapMarkdownUrl(String value) {
        if (!value.startsWith("[")) {
            return value;
        }
        int linkSeparator = value.indexOf("](");
        int linkEnd = linkSeparator < 0 ? -1 : value.indexOf(')', linkSeparator + 2);
        if (linkSeparator <= 1 || linkEnd <= linkSeparator + 2) {
            return value;
        }
        String target = value.substring(linkSeparator + 2, linkEnd);
        String suffix = value.substring(linkEnd + 1);
        return target + suffix;
    }

    private void sleep(java.time.Duration delay) {
        try {
            Thread.sleep(Math.max(0, delay.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunningHubException("等待重新下载动作迁移视频时被中断", exception);
        }
    }

    private List<NodeInput> workflowInputs(String videoFile, String imageFile) {
        return List.of(
                // RunningHub 最新视频工作流已移除旧的 535/select 节点，改为输出方式 561/select，
                // 并新增 538/value 控制“正常模式/防突变长视频模式”。
                node("561", "select", "1", "输出方式"),
                node("538", "value", "true", "正常模式（长视频防突变模式关闭）"),
                node("293", "select", "1", "姿势计算方式"),
                node("497", "value", "false", "姿势3，正常关闭"),
                node("297", "value", "1.0000000000000002", "姿势强度"),
                node("370", "value", "false", "运镜开关"),
                node("361", "value", "1.0000000000000002", "运镜强度"),
                node("271", "value", "false", "面具头盔模式"),
                node("265", "value", "0.8000000000000002", "表情强度"),
                node("266", "value", "0.20000000000000004", "胸部动作幅度"),
                node("499", "value", "0", "跳过帧数"),
                node("422", "value", Integer.toString(properties.getVideoMotionMaxFrames()), "加载帧上限"),
                node("264", "value", Integer.toString(properties.getVideoMotionInputFrameRate()), "帧率"),
                node("470", "select", properties.getVideoMotionResolutionPreset(), "分辨率(推荐默认)"),
                node("452", "value", "false", "关闭自定义比例"),
                node("451", "value", "9", "比例宽"),
                node("450", "value", "16", "比例高"),
                node("275", "video", videoFile, "加载参考视频"),
                node("299", "image", imageFile, "加载参考图片"));
    }

    private static NodeInput node(String nodeId, String fieldName, String value, String description) {
        return new NodeInput(nodeId, fieldName, value, description);
    }
}
