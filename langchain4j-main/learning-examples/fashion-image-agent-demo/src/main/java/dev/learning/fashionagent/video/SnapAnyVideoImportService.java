package dev.learning.fashionagent.video;

import com.fasterxml.jackson.databind.JsonNode;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.config.SnapAnyProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SnapAnyVideoImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapAnyVideoImportService.class);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"'，。；！？、]+");
    private static final String VIDEO_TYPE = "video";

    private final SnapAnyProperties properties;
    private final RunningHubProperties runningHubProperties;
    private final VideoMediaProcessor mediaProcessor;
    private final ChromiumSegmentedMediaDownloader chromiumDownloader;
    private final RestClient apiClient;
    private final RestClient downloadClient;
    private final Executor executor;
    private final Map<UUID, ImportJob> jobs = new ConcurrentHashMap<>();

    public SnapAnyVideoImportService(
            SnapAnyProperties properties,
            RunningHubProperties runningHubProperties,
            VideoMediaProcessor mediaProcessor,
            ChromiumSegmentedMediaDownloader chromiumDownloader,
            @Qualifier("snapAnyImportExecutor") Executor executor) {
        this.properties = properties;
        this.runningHubProperties = runningHubProperties;
        this.mediaProcessor = mediaProcessor;
        this.chromiumDownloader = chromiumDownloader;
        var apiFactory = buildApiRequestFactory(properties);
        this.apiClient = RestClient.builder()
                .requestFactory(apiFactory)
                .baseUrl(properties.getBaseUrl().toString())
                .build();
        SimpleClientHttpRequestFactory downloadFactory = new SimpleClientHttpRequestFactory();
        downloadFactory.setConnectTimeout(properties.getConnectTimeout());
        downloadFactory.setReadTimeout(properties.getReadTimeout());
        this.downloadClient = RestClient.builder()
                .requestFactory(downloadFactory)
                .defaultHeader(HttpHeaders.ACCEPT, "*/*")
                .defaultHeader(HttpHeaders.CONNECTION, "close")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .build();
        this.executor = executor;
    }

    private static org.springframework.http.client.ClientHttpRequestFactory buildApiRequestFactory(
            SnapAnyProperties properties) {
        String host = properties.getProxyHost() == null ? "" : properties.getProxyHost().trim();
        int port = properties.getProxyPort();
        if (properties.isProxyEnabled() && !host.isBlank() && port > 0) {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(properties.getConnectTimeout())
                    .proxy(ProxySelector.of(new InetSocketAddress(host, port)))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(properties.getReadTimeout());
            LOGGER.info("SnapAny 提取 API HTTP 代理已启用 proxy={}:{}", host, port);
            return factory;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        LOGGER.info("SnapAny 提取 API 使用直连");
        return factory;
    }

    public ImportView create(String folderName, String rawContent) {
        String folder = validateFolderName(folderName);
        List<String> urls = extractUrls(rawContent);
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("没有解析到 http(s) 视频地址");
        }
        int max = Math.max(1, properties.getMaxUrlsPerImport());
        if (urls.size() > max) {
            throw new IllegalArgumentException("本次最多支持 " + max + " 个视频地址，当前解析到 " + urls.size() + " 个");
        }
        properties.requiredApiKey();
        Path folderPath = videoDirectory().resolve(folder).normalize();
        try {
            Files.createDirectories(folderPath);
            if (Files.isSymbolicLink(folderPath)) {
                throw new IllegalArgumentException("视频保存文件夹不能是符号链接：" + folder);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建视频文件夹：" + folderPath, exception);
        }
        ImportJob job = new ImportJob(UUID.randomUUID(), folder, urls);
        jobs.put(job.id, job);
        dispatch(job, folderPath);
        return job.view();
    }

    public ImportView get(UUID id) {
        ImportJob job = jobs.get(id);
        if (job == null) {
            throw new IllegalArgumentException("视频提取任务不存在：" + id);
        }
        return job.view();
    }

    public Path downloadFirst(String rawContent, Path targetDirectory) {
        List<String> urls = extractUrls(rawContent);
        if (urls.isEmpty()) throw new IllegalArgumentException("没有解析到 http(s) 视频地址");
        properties.requiredApiKey();
        Path directory = targetDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new IllegalArgumentException("影视复刻保存目录不能是符号链接");
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建影视复刻保存目录：" + directory, exception);
        }
        return extractAndDownload(urls.get(0), directory, 1);
    }

    static List<String> extractUrls(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return List.of();
        Matcher matcher = URL_PATTERN.matcher(rawContent);
        Map<String, Boolean> unique = new LinkedHashMap<>();
        while (matcher.find()) {
            String url = trimUrlPunctuation(matcher.group());
            if (!url.isBlank()) unique.putIfAbsent(url, Boolean.TRUE);
        }
        return List.copyOf(unique.keySet());
    }

    private void dispatch(ImportJob job, Path folder) {
        job.start();
        for (int i = 0; i < job.urls.size(); i++) {
            int index = i;
            try {
                executor.execute(() -> executeItem(job, folder, index));
            } catch (RuntimeException exception) {
                ImportItem item = job.items.get(index);
                item.fail("SnapAny 下载任务无法进入队列：" + exception.getMessage());
                job.itemCompleted();
            }
        }
    }

    private void executeItem(ImportJob job, Path folder, int index) {
        ImportItem item = job.items.get(index);
        String postUrl = job.urls.get(index);
        item.start();
        try {
            Path saved = extractAndDownload(postUrl, folder, index + 1);
            item.success(saved.getFileName().toString());
        } catch (Exception exception) {
            LOGGER.warn("SnapAny 视频提取失败 index={} url={} reason={}", index + 1, postUrl, exception.getMessage());
            item.fail(exception.getMessage());
        } finally {
            job.itemCompleted();
        }
    }

    private Path extractAndDownload(String postUrl, Path folder, int sequence) {
        int maxAttempts = Math.max(1, properties.getDownloadMaxAttempts());
        RuntimeException lastFailure = null;
        Path target = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                LOGGER.info("SnapAny 重新解析原视频地址以刷新 CDN 链接和请求头 attempt={}/{} postUrl={}",
                        attempt, maxAttempts, postUrl);
            }
            try {
                DownloadSpec spec = requestDownloadSpec(postUrl);
                if (target == null) {
                    String extension = spec.audioUrl == null ? extension(spec.videoUrl) : ".mp4";
                    target = nextTarget(folder, sequence, extension);
                }
                return downloadSpec(spec, target);
            } catch (IOException | RuntimeException exception) {
                lastFailure = asRuntimeFailure(exception);
                cleanupDownloadFiles(target);
                LOGGER.warn("SnapAny 本轮提取或下载失败 attempt={}/{} postUrl={} reason={}",
                        attempt, maxAttempts, postUrl, exception.getMessage());
                if (attempt < maxAttempts) {
                    sleepBeforeRetry(postUrl);
                }
            }
        }
        throw new IllegalStateException(
                "SnapAny 视频提取下载失败，已重新解析原地址 " + maxAttempts + " 次：" + postUrl,
                lastFailure);
    }

    private DownloadSpec requestDownloadSpec(String postUrl) {
        LOGGER.info("SnapAny 提取请求发送 endpoint={} proxyEnabled={}",
                properties.getBaseUrl() + "/openapi/v1/extract/post", properties.isProxyEnabled());
        JsonNode response = apiClient.post()
                .uri("/openapi/v1/extract/post")
                .headers(headers -> {
                    headers.setBearerAuth(properties.requiredApiKey());
                    headers.set("Accept-Language", "zh");
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("url", postUrl))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> throwRemoteError(result))
                .body(JsonNode.class);
        return chooseVideo(response);
    }

    private Path downloadSpec(DownloadSpec spec, Path target) throws IOException {
        Path videoPart = target.resolveSibling(target.getFileName() + ".video.part");
        Path audioPart = target.resolveSibling(target.getFileName() + ".audio.part");
        download(spec.videoUrl, spec.headers, videoPart);
        if (spec.audioUrl != null) {
            download(spec.audioUrl, spec.headers, audioPart);
            mediaProcessor.muxSeparatedStreams(
                    videoPart,
                    audioPart,
                    target,
                    target.resolveSibling(target.getFileName() + ".ffmpeg.log"));
            Files.deleteIfExists(videoPart);
            Files.deleteIfExists(audioPart);
        } else {
            Files.move(videoPart, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    void download(String url, Map<String, String> headers, Path target) {
        URI uri = URI.create(url.replace(" ", "%20"));
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("SnapAny 返回了不支持的媒体地址：" + uri);
        }
        RuntimeException lastFailure = null;
        boolean curlFirst = isDouyinCdn(uri);
        boolean bitsFirst = isWindows() && curlFirst;
        if (curlFirst && chromiumDownloader.isAvailable()) {
            try {
                chromiumDownloader.download(uri, headers, target);
                LOGGER.info("SnapAny Chromium 严格请求头分段下载完成 url={} file={} size={} bytes",
                        uri, target, Files.size(target));
                return;
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(target);
                lastFailure = asRuntimeFailure(exception);
                LOGGER.warn("SnapAny Chromium 严格请求头分段下载失败，将回退常规通道 url={} reason={}",
                        uri, exception.getMessage());
            }
        }
        if (bitsFirst) {
            try {
                downloadViaBits(uri, headers, target);
                LOGGER.info("SnapAny 抖音 CDN BITS 媒体下载完成 url={} file={} size={} bytes",
                        uri, target, Files.size(target));
                return;
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(target);
                lastFailure = asRuntimeFailure(exception);
                LOGGER.warn("SnapAny 抖音 CDN BITS 下载失败，将切换 curl url={} reason={}", uri, exception.getMessage());
            }
        }
        if (curlFirst) {
            try {
                downloadViaCurl(uri, headers, target);
                LOGGER.info("SnapAny 抖音 CDN curl 媒体下载完成 url={} file={} size={} bytes",
                        uri, target, Files.size(target));
                return;
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(target);
                lastFailure = asRuntimeFailure(exception);
                LOGGER.warn("SnapAny 抖音 CDN curl 下载失败，切换 Java 下载 url={} reason={}", uri, exception.getMessage());
            }
        }
        try {
            downloadViaJava(uri, headers, target);
            LOGGER.info("SnapAny 媒体下载完成 url={} file={} size={} bytes",
                    uri, target, Files.size(target));
            return;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(target);
            lastFailure = asRuntimeFailure(exception);
            LOGGER.warn("SnapAny Java 媒体下载失败，切换 curl 下载 url={} reason={}", uri, exception.getMessage());
        }

        if (!curlFirst) {
            try {
                downloadViaCurl(uri, headers, target);
                LOGGER.info("SnapAny curl 媒体下载完成 url={} file={} size={} bytes",
                        uri, target, Files.size(target));
                return;
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(target);
                lastFailure = asRuntimeFailure(exception);
                LOGGER.warn("SnapAny curl 媒体下载失败，将继续使用 Java 重试 url={} reason={}", uri, exception.getMessage());
            }
        }

        throw new IllegalStateException("当前 SnapAny CDN 地址的全部下载通道均失败：" + uri, lastFailure);
    }

    private static boolean isDouyinCdn(URI uri) {
        String host = uri.getHost();
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".365yg.com")
                || normalized.endsWith(".douyinvod.com")
                || normalized.endsWith(".douyin.com");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void downloadViaJava(URI uri, Map<String, String> headers, Path target) throws IOException {
        Files.deleteIfExists(target);
        Files.createDirectories(target.toAbsolutePath().getParent());
        downloadClient.get()
                .uri(uri)
                .headers(requestHeaders -> headers.forEach(requestHeaders::set))
                .exchange((request, response) -> streamToFile(response, target, uri));
    }

    private void downloadViaCurl(URI uri, Map<String, String> headers, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".curl.part");
        deleteQuietly(temporary);
        Files.createDirectories(target.toAbsolutePath().getParent());

        List<String> command = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        command.add(osName.contains("win") ? "curl.exe" : "curl");
        command.add("--ipv4");
        command.add("--http1.1");
        command.add("--location");
        command.add("--fail");
        command.add("--silent");
        command.add("--show-error");
        command.add("--connect-timeout");
        command.add(Long.toString(Math.max(1, properties.getConnectTimeout().toSeconds())));
        command.add("--max-time");
        command.add(Long.toString(Math.max(1, properties.getReadTimeout().toSeconds())));
        command.add("--output");
        command.add(temporary.toString());
        headers.forEach((name, value) -> {
            if (name == null || value == null || name.contains("\r") || name.contains("\n")
                    || value.contains("\r") || value.contains("\n")) {
                return;
            }
            command.add("--header");
            command.add(value.isEmpty() ? name + ";" : name + ": " + value);
        });
        command.add("--url");
        command.add(uri.toASCIIString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, properties.getReadTimeout().toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("curl 下载被中断", exception);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("curl 下载超时");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException("curl exitCode=" + process.exitValue() + (output.isBlank() ? "" : " output=" + output));
        }
        long size = Files.exists(temporary) ? Files.size(temporary) : 0;
        if (size <= 0) {
            throw new IOException("curl 下载结果为空" + (output.isBlank() ? "" : " output=" + output));
        }
        if (size > properties.getMaxDownloadBytes()) {
            throw new IOException("媒体超过允许大小：" + size + " bytes");
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void downloadViaBits(URI uri, Map<String, String> headers, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".bits.part");
        deleteQuietly(temporary);
        Files.createDirectories(target.toAbsolutePath().getParent());

        String customHeaders = headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> !entry.getKey().contains("\r") && !entry.getKey().contains("\n"))
                .filter(entry -> !entry.getValue().contains("\r") && !entry.getValue().contains("\n"))
                .map(entry -> "'" + powershellQuote(entry.getKey() + ": " + entry.getValue()) + "'")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String source = powershellQuote(uri.toASCIIString());
        String destination = powershellQuote(temporary.toAbsolutePath().toString());
        String script = "$ErrorActionPreference='Stop';"
                + "$source='" + source + "';"
                + "$destination='" + destination + "';"
                + "$customHeaders=@(" + customHeaders + ");"
                + "$arguments=@{Source=$source;Destination=$destination;Asynchronous=$true;"
                + "DisplayName='FashionImageAgent SnapAny';Priority='Foreground';RetryInterval=60;RetryTimeout=120;"
                + "MaxDownloadTime=300};"
                + "if($customHeaders.Count -gt 0){$arguments.CustomHeaders=$customHeaders};"
                + "$job=Start-BitsTransfer @arguments;"
                + "$deadline=[DateTime]::UtcNow.AddSeconds(360);"
                + "while($job.JobState -in @('Connecting','Transferring','Queued') -and [DateTime]::UtcNow -lt $deadline){"
                + "Start-Sleep -Seconds 2;$job=Get-BitsTransfer -JobId $job.JobId};"
                + "if($job.JobState -ne 'Transferred'){"
                + "$errorText=if($job.ErrorDescription){$job.ErrorDescription}else{'unknown'};"
                + "Remove-BitsTransfer -BitsJob $job -Confirm:$false -ErrorAction SilentlyContinue;"
                + "throw ('BITS state=' + $job.JobState + ' error=' + $errorText)};"
                + "Complete-BitsTransfer -BitsJob $job;"
                + "if(!(Test-Path -LiteralPath $destination)){throw 'BITS output file missing'};"
                + "Write-Output ('BITS completed bytes=' + (Get-Item -LiteralPath $destination).Length)";
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded)
                .redirectErrorStream(true)
                .start();
        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, properties.getReadTimeout().toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("BITS 下载被中断", exception);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("BITS 下载超时");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException("BITS exitCode=" + process.exitValue() + (output.isBlank() ? "" : " output=" + output));
        }
        long size = Files.exists(temporary) ? Files.size(temporary) : 0;
        if (size <= 0) throw new IOException("BITS 下载结果为空");
        if (size > properties.getMaxDownloadBytes()) {
            throw new IOException("媒体超过允许大小：" + size + " bytes");
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String powershellQuote(String value) {
        return value.replace("'", "''");
    }

    private static RuntimeException asRuntimeFailure(Exception exception) {
        return exception instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(exception);
    }

    private long streamToFile(ClientHttpResponse response, Path target, URI uri) throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("媒体下载返回 HTTP " + response.getStatusCode().value() + "：" + uri);
        }
        long contentLength = response.getHeaders().getContentLength();
        if (contentLength > properties.getMaxDownloadBytes()) {
            throw new IllegalStateException("媒体超过允许大小：" + contentLength + " bytes");
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".download");
        deleteQuietly(temporary);
        long size = 0;
        boolean completed = false;
        try {
            try (InputStream input = response.getBody(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    size += read;
                    if (size > properties.getMaxDownloadBytes()) {
                        throw new IllegalStateException("媒体超过允许大小：" + size + " bytes");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (contentLength >= 0 && size != contentLength) {
                throw new IllegalStateException("媒体下载不完整，预期 " + contentLength + " bytes，实际 " + size
                        + " bytes：" + uri);
            }
            if (size <= 0) throw new IllegalStateException("媒体下载结果为空：" + uri);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            completed = true;
            return size;
        } finally {
            if (!completed) deleteQuietly(temporary);
        }
    }

    private DownloadSpec chooseVideo(JsonNode response) {
        JsonNode medias = response == null ? null : response.path("medias");
        if (medias == null || !medias.isArray()) {
            throw new IllegalStateException("SnapAny 响应没有 medias 媒体列表");
        }
        for (JsonNode media : medias) {
            if (!isVideo(media)) continue;
            JsonNode variants = media.path("variants");
            if (variants.isArray() && variants.size() > 0) {
                List<JsonNode> all = new ArrayList<>();
                variants.forEach(all::add);
                List<JsonNode> underTarget = all.stream()
                        .filter(variant -> quality(variant) > 0 && quality(variant) <= Math.max(1, properties.getTargetQuality()))
                        .sorted(Comparator.comparingInt(this::quality).reversed())
                        .toList();
                JsonNode variant = underTarget.isEmpty() ? all.get(all.size() - 1) : underTarget.get(0);
                String videoUrl = firstText(variant, "video_url", "resource_url", "url");
                if (videoUrl != null) {
                    Map<String, String> headers = new LinkedHashMap<>(readHeaders(media.path("headers")));
                    headers.putAll(readHeaders(variant.path("headers")));
                    LOGGER.info("SnapAny 视频清晰度选择 quality={} videoUrl={} headers={} emptyHeaders={}",
                            quality(variant), videoUrl, headers.keySet(), emptyHeaderNames(headers));
                    return new DownloadSpec(videoUrl, firstText(variant, "audio_url"), headers);
                }
            }
            String resourceUrl = firstText(media, "resource_url", "video_url", "url", "download_url");
            if (resourceUrl != null) {
                Map<String, String> headers = new LinkedHashMap<>(readHeaders(media.path("headers")));
                return new DownloadSpec(resourceUrl, null, headers);
            }
        }
        throw new IllegalStateException("SnapAny 响应没有可下载的视频媒体");
    }

    private boolean isVideo(JsonNode media) {
        String type = media.path("type").asText("").toLowerCase();
        if (type.contains("image") || type.contains("audio")) return false;
        if (VIDEO_TYPE.equals(type) || type.contains("video")) return true;
        if (media.path("variants").isArray() && media.path("variants").size() > 0) return true;
        String url = firstText(media, "resource_url", "video_url", "url", "download_url");
        return url != null && !url.toLowerCase().matches(".*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$");
    }

    private Map<String, String> readHeaders(JsonNode node) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (node != null && node.isObject()) node.fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText("")));
        return headers;
    }

    private static List<String> emptyHeaderNames(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
    }

    private int quality(JsonNode variant) {
        JsonNode value = variant.get("quality");
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.asText("").replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText("").trim();
            if (!value.isBlank()) return value;
        }
        return null;
    }

    private Path nextTarget(Path folder, int sequence, String extension) {
        String prefix = String.format("video-%03d", sequence);
        Path target = folder.resolve(prefix + extension);
        if (!Files.exists(target)) return target;
        return folder.resolve(prefix + "-" + Instant.now().toEpochMilli() + extension);
    }

    private Path videoDirectory() {
        return runningHubProperties.getVideoDirectory().toAbsolutePath().normalize();
    }

    private static String validateFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) throw new IllegalArgumentException("请输入视频文件夹名称");
        String value = folderName.trim();
        if (value.equals(".") || value.equals("..") || value.contains("\\") || value.contains("/") || value.contains(":")
                || value.contains("\u0000")) {
            throw new IllegalArgumentException("视频文件夹名称只能是 video_ai 下的一级文件夹名称");
        }
        return value;
    }

    private static String trimUrlPunctuation(String url) {
        return url.replaceAll("[，。；！？、\\)\\]}>]+$", "");
    }

    private static String extension(String url) {
        String path = URI.create(url.replace(" ", "%20")).getPath();
        if (path != null) {
            String lower = path.toLowerCase();
            for (String extension : List.of(".mp4", ".mov", ".webm", ".m4v", ".mkv")) {
                if (lower.endsWith(extension)) return extension;
            }
        }
        return ".mp4";
    }

    private void sleepBeforeRetry(String postUrl) {
        try {
            Thread.sleep(Math.max(0L, properties.getDownloadRetryDelay().toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SnapAny 视频提取下载被中断：" + postUrl, interrupted);
        }
    }

    private static void cleanupDownloadFiles(Path target) {
        if (target == null) return;
        Path videoPart = target.resolveSibling(target.getFileName() + ".video.part");
        Path audioPart = target.resolveSibling(target.getFileName() + ".audio.part");
        deleteDownloadArtifacts(videoPart);
        deleteDownloadArtifacts(audioPart);
        deleteQuietly(target);
    }

    private static void deleteDownloadArtifacts(Path part) {
        deleteQuietly(part);
        deleteQuietly(part.resolveSibling(part.getFileName() + ".download"));
        deleteQuietly(part.resolveSibling(part.getFileName() + ".curl.part"));
        deleteQuietly(part.resolveSibling(part.getFileName() + ".bits.part"));
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original extraction failure.
        }
    }

    private void throwRemoteError(ClientHttpResponse response) throws IOException {
        throw new IllegalStateException("SnapAny 提取接口返回 HTTP " + response.getStatusCode().value()
                + "：" + new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    public enum ImportStatus { QUEUED, RUNNING, SUCCESS, PARTIAL, FAILED }

    public record ImportView(UUID id, String folderName, ImportStatus status, int total, int completed, int succeeded,
            int failed, List<ImportItemView> items, Instant createdAt, Instant updatedAt) {}

    public record ImportItemView(int index, String url, String status, String fileName, String error) {}

    private record DownloadSpec(String videoUrl, String audioUrl, Map<String, String> headers) {}

    private static final class ImportJob {
        private final UUID id;
        private final String folderName;
        private final List<String> urls;
        private final List<ImportItem> items;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile ImportStatus status = ImportStatus.QUEUED;
        private volatile int completed;

        private ImportJob(UUID id, String folderName, List<String> urls) {
            this.id = id;
            this.folderName = folderName;
            this.urls = List.copyOf(urls);
            this.items = urls.stream().map(url -> new ImportItem(urls.indexOf(url) + 1, url)).toList();
        }

        private synchronized void start() { status = ImportStatus.RUNNING; updatedAt = Instant.now(); }

        private synchronized void itemCompleted() {
            completed++;
            updatedAt = Instant.now();
            if (completed == items.size()) {
                finish();
            }
        }

        private void finish() {
            int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status)).count();
            status = failed == 0 ? ImportStatus.SUCCESS : (failed == items.size() ? ImportStatus.FAILED : ImportStatus.PARTIAL);
            updatedAt = Instant.now();
        }

        private synchronized ImportView view() {
            int succeeded = (int) items.stream().filter(item -> "SUCCESS".equals(item.status)).count();
            int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status)).count();
            return new ImportView(id, folderName, status, items.size(), completed, succeeded, failed,
                    items.stream().map(ImportItem::view).toList(), createdAt, updatedAt);
        }
    }

    private static final class ImportItem {
        private final int index;
        private final String url;
        private volatile String status = "QUEUED";
        private volatile String fileName;
        private volatile String error;

        private ImportItem(int index, String url) { this.index = index; this.url = url; }

        private void start() { status = "RUNNING"; }
        private void success(String fileName) { this.fileName = fileName; status = "SUCCESS"; }
        private void fail(String error) { this.error = error == null ? "未知错误" : error; status = "FAILED"; }
        private ImportItemView view() { return new ImportItemView(index, url, status, fileName, error); }
    }
}
