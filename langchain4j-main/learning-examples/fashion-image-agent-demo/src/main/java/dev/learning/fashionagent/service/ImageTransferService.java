package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.RunningHubClient;
import dev.learning.fashionagent.integration.runninghub.RunningHubException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageTransferService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageTransferService.class);

    private final RunningHubClient client;
    private final RunningHubProperties properties;

    public ImageTransferService(RunningHubClient client, RunningHubProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public String uploadLocal(Path image) {
        return client.upload(image);
    }

    public Path downloadRemote(URI imageUrl, UUID jobId, String fileName) {
        validateRemoteUrl(imageUrl);
        Path jobDirectory = jobDirectory(jobId);
        try {
            Files.createDirectories(jobDirectory);
            Path localImage = jobDirectory.resolve(fileName + suffixOf(imageUrl));
            Files.write(localImage, downloadWithRetry(imageUrl));
            LOGGER.info("图片已保存到本地，jobId={} file={}", jobId, localImage);
            return localImage;
        } catch (IOException exception) {
            throw new RunningHubException("保存图片到本地失败：" + jobDirectory, exception);
        }
    }

    public Path archiveLocal(Path source, UUID jobId, String fileName) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("待归档图片不存在：" + source);
        }
        Path jobDirectory = jobDirectory(jobId);
        try {
            Files.createDirectories(jobDirectory);
            Path archived = jobDirectory.resolve(fileName + suffixOf(source));
            Files.copy(source, archived, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("本地图片已归档，jobId={} source={} target={}", jobId, source, archived);
            return archived;
        } catch (IOException exception) {
            throw new RunningHubException("归档本地图片失败：" + source, exception);
        }
    }

    private Path jobDirectory(UUID jobId) {
        return properties.getGeneratedDirectory()
                .toAbsolutePath()
                .normalize()
                .resolve("jobs")
                .resolve(jobId.toString());
    }

    private byte[] downloadWithRetry(URI imageUrl) {
        int attempts = Math.max(1, properties.getDownloadMaxAttempts());
        URI downloadUrl = trustedDownloadUrl(imageUrl);
        if (!downloadUrl.equals(imageUrl)) {
            LOGGER.info("RunningHub COS 图片使用 HTTP 直连下载，避免 HTTPS 握手超时：{}", downloadUrl);
        }
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return client.download(downloadUrl.toString());
            } catch (RuntimeException exception) {
                lastFailure = exception;
                LOGGER.warn("下载人物底图失败，第 {}/{} 次，URL={}", attempt, attempts, downloadUrl, exception);
                if (attempt < attempts) {
                    waitBeforeRetry();
                }
            }
        }
        throw new RunningHubException("下载人物底图失败，已重试 " + attempts + " 次：" + imageUrl, lastFailure);
    }

    private static URI trustedDownloadUrl(URI imageUrl) {
        String host = imageUrl.getHost();
        if (!"https".equalsIgnoreCase(imageUrl.getScheme()) || host == null
                || !host.toLowerCase(Locale.ROOT).endsWith(".cos.ap-beijing.myqcloud.com")) {
            return imageUrl;
        }
        return URI.create("http://" + imageUrl.toString().substring("https://".length()));
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(Math.max(0, properties.getDownloadRetryDelay().toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunningHubException("等待重新下载人物底图时被中断", exception);
        }
    }

    private static void validateRemoteUrl(URI uri) {
        if (uri == null || uri.getScheme() == null
                || (!uri.getScheme().equalsIgnoreCase("https") && !uri.getScheme().equalsIgnoreCase("http"))) {
            throw new IllegalArgumentException("人物底图必须是 HTTP(S) 图片地址");
        }
    }

    private static String suffixOf(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (path.endsWith(".webp")) {
            return ".webp";
        }
        return ".png";
    }

    private static String suffixOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return ".jpg";
        }
        if (name.endsWith(".webp")) {
            return ".webp";
        }
        return ".png";
    }
}
