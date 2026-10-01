package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.RunningHubClient;
import dev.learning.fashionagent.integration.runninghub.RunningHubException;
import dev.learning.fashionagent.video.ChromiumSegmentedMediaDownloader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ImageTransferService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageTransferService.class);
    private static final Semaphore DOWNLOAD_PERMIT = new Semaphore(1, true);

    private final RunningHubClient client;
    private final RunningHubProperties properties;
    private final ChromiumSegmentedMediaDownloader chromiumDownloader;

    @Autowired
    public ImageTransferService(
            RunningHubClient client,
            RunningHubProperties properties,
            ChromiumSegmentedMediaDownloader chromiumDownloader) {
        this.client = client;
        this.properties = properties;
        this.chromiumDownloader = chromiumDownloader;
    }

    /** Backward-compatible constructor for lightweight unit tests. */
    public ImageTransferService(RunningHubClient client, RunningHubProperties properties) {
        this(client, properties, null);
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
            acquireDownloadPermit(imageUrl);
            try {
                downloadWithRetry(imageUrl, localImage);
            } finally {
                DOWNLOAD_PERMIT.release();
            }
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

    private void downloadWithRetry(URI imageUrl, Path target) throws IOException {
        int attempts = Math.max(1, properties.getDownloadMaxAttempts());
        boolean trustedAsset = isTrustedRunningHubAsset(imageUrl);
        URI downloadUrl = imageUrl;
        if (trustedAsset) {
            LOGGER.info("RunningHub 人物底图使用浏览器兼容下载通道（首选 Chromium） url={} target={}",
                    imageUrl, target);
        }
        boolean chromiumAvailable = trustedAsset
                && chromiumDownloader != null
                && chromiumDownloader.isAvailable();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if (chromiumAvailable && attempt == 1) {
                    downloadViaChromium(imageUrl, target);
                } else if (trustedAsset && attempt == (chromiumAvailable ? 2 : 1)) {
                    client.downloadToFileViaBits(imageUrl, target, properties.getMaxDownloadBytes());
                } else if (trustedAsset && attempt == (chromiumAvailable ? 3 : 2)) {
                    client.downloadToFileViaCurl(imageUrl, target, properties.getMaxDownloadBytes(), true);
                } else if (trustedAsset && attempt == (chromiumAvailable ? 4 : 3)) {
                    client.downloadToFileViaPowerShell(imageUrl, target, properties.getMaxDownloadBytes(), true);
                } else {
                    if (trustedAsset) {
                        client.downloadToFileAsBytes(imageUrl, target, properties.getMaxDownloadBytes());
                    } else {
                        Files.write(target, client.download(downloadUrl.toString()));
                    }
                }
                validateDownloadedImage(target);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                LOGGER.warn("下载人物底图失败，第 {}/{} 次，通道={}，URL={}，原因={}",
                        attempt, attempts,
                        trustedAsset ? switch (attempt) {
                            case 1 -> chromiumAvailable ? "Chromium浏览器" : "BITS直连";
                            case 2 -> chromiumAvailable ? "BITS直连" : "curl直连";
                            case 3 -> chromiumAvailable ? "curl直连" : "PowerShell直连";
                            case 4 -> chromiumAvailable ? "PowerShell直连" : "Java字节直连";
                            default -> "Java字节直连";
                        } : "Java直连",
                        downloadUrl, exception.getMessage(), exception);
                deleteIfExists(target);
                if (attempt < attempts) {
                    waitBeforeRetry();
                }
            } catch (IOException exception) {
                lastFailure = new RunningHubException(exception.getMessage(), exception);
                LOGGER.warn("下载人物底图失败，第 {}/{} 次，通道={}，URL={}，原因={}",
                        attempt, attempts,
                        trustedAsset ? switch (attempt) {
                            case 1 -> chromiumAvailable ? "Chromium浏览器" : "BITS直连";
                            case 2 -> chromiumAvailable ? "BITS直连" : "curl直连";
                            case 3 -> chromiumAvailable ? "curl直连" : "PowerShell直连";
                            case 4 -> chromiumAvailable ? "PowerShell直连" : "Java字节直连";
                            default -> "Java字节直连";
                        } : "Java直连",
                        downloadUrl, exception.getMessage(), exception);
                deleteIfExists(target);
                if (attempt < attempts) {
                    waitBeforeRetry();
                }
            }
        }
        throw new RunningHubException("下载人物底图失败，已重试 " + attempts + " 次：" + imageUrl, lastFailure);
    }

    private void downloadViaChromium(URI imageUrl, Path target) throws IOException {
        chromiumDownloader.download(imageUrl, Map.of(
                "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "Chrome/131.0.0.0 Safari/537.36",
                "Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                "Accept-Encoding", "identity",
                "Referer", "https://www.runninghub.cn/"), target);
    }

    private void validateDownloadedImage(Path target) throws IOException {
        long size = Files.size(target);
        if (size <= 0 || size > properties.getMaxDownloadBytes()) {
            throw new IOException("人物底图大小异常：" + size + " bytes");
        }
    }

    private static boolean isTrustedRunningHubAsset(URI imageUrl) {
        String host = imageUrl.getHost();
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".cos.ap-beijing.myqcloud.com")
                || normalized.endsWith(".tos-cn-beijing.volces.com");
    }

    private static void deleteIfExists(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private static void acquireDownloadPermit(URI imageUrl) {
        try {
            LOGGER.info("人物底图等待独占下载通道 url={}", imageUrl);
            DOWNLOAD_PERMIT.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunningHubException("等待人物底图下载通道时被中断：" + imageUrl, exception);
        }
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
