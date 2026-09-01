package dev.learning.fashionagent.integration.runninghub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.learning.fashionagent.config.RunningHubProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

@Component
public class RunningHubClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunningHubClient.class);

    private final RestClient restClient;
    private final RunningHubProperties properties;

    public RunningHubClient(RestClient.Builder restClientBuilder, RunningHubProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getUploadConnectTimeout());
        requestFactory.setReadTimeout(properties.getUploadReadTimeout());
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl().toString())
                .build();
        this.properties = properties;
    }

    public TaskResponse submit(String appId, List<NodeInput> nodeInputs) {
        return submit(appId, nodeInputs, "default");
    }

    public TaskResponse submit(String appId, List<NodeInput> nodeInputs, String instanceType) {
        LOGGER.info("RunningHub 提交请求 appId={} instanceType={} nodeInfoList={}",
                appId, instanceType, nodeInputs);
        TaskResponse response = restClient
                .post()
                .uri("/openapi/v2/run/ai-app/{appId}", appId)
                .headers(headers -> headers.setBearerAuth(properties.requiredApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RunRequest(nodeInputs, instanceType, false))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiError)
                .body(TaskResponse.class);
        LOGGER.info("RunningHub 提交响应 appId={} taskId={} status={} errorCode={} errorMessage={}",
                appId,
                response == null ? null : response.taskId(),
                response == null ? null : response.status(),
                response == null ? null : response.errorCode(),
                response == null ? null : response.errorMessage());
        return response;
    }

    public TaskResponse query(String taskId) {
        LOGGER.info("RunningHub 查询请求 taskId={}", taskId);
        TaskResponse response = restClient
                .post()
                .uri("/openapi/v2/query")
                .headers(headers -> headers.setBearerAuth(properties.requiredApiKey()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new QueryRequest(taskId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiError)
                .body(TaskResponse.class);
        LOGGER.info("RunningHub 查询响应 taskId={} status={} errorCode={} errorMessage={} failedReason={}",
                taskId,
                response == null ? null : response.status(),
                response == null ? null : response.errorCode(),
                response == null ? null : response.errorMessage(),
                response == null ? null : response.failedReason());
        return response;
    }

    public String upload(Path file) {
        return upload(file, properties.getMaxUploadBytes());
    }

    public String upload(Path file, long maxUploadBytes) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new RunningHubException("待上传图片不存在：" + file);
        }
        long fileSize;
        try {
            fileSize = Files.size(file);
        } catch (IOException exception) {
            throw new RunningHubException("无法读取待上传图片大小：" + file, exception);
        }
        if (!Files.isReadable(file)) {
            throw new RunningHubException("待上传图片不可读：" + file);
        }
        if (fileSize <= 0) {
            throw new RunningHubException("待上传图片为空：" + file);
        }
        if (fileSize > maxUploadBytes) {
            throw new RunningHubException("待上传文件超过允许大小：" + fileSize + " bytes");
        }

        int attempts = Math.max(1, properties.getUploadMaxAttempts());
        ResourceAccessException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            long startedAt = System.nanoTime();
            LOGGER.info("RunningHub 上传开始 attempt={}/{} file={} size={} bytes connectTimeout={} readTimeout={}",
                    attempt,
                    attempts,
                    file,
                    fileSize,
                    properties.getUploadConnectTimeout(),
                    properties.getUploadReadTimeout());
            try {
                String remoteFileName = uploadOnce(file);
                LOGGER.info("RunningHub 上传成功 attempt={}/{} elapsedMs={} localFile={} remoteFileName={}",
                        attempt,
                        attempts,
                        elapsedMillis(startedAt),
                        file,
                        remoteFileName);
                return remoteFileName;
            } catch (ResourceAccessException exception) {
                lastFailure = exception;
                LOGGER.warn("RunningHub 上传网络失败 attempt={}/{} elapsedMs={} file={} error={}",
                        attempt,
                        attempts,
                        elapsedMillis(startedAt),
                        file,
                        exception.getMessage(),
                        exception);
                if (attempt < attempts) {
                    waitBeforeUploadRetry();
                }
            }
        }
        throw new RunningHubException("RunningHub 上传失败，已重试 " + attempts + " 次：" + file, lastFailure);
    }

    private String uploadOnce(Path file) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", new FileSystemResource(file));

        UploadResponse response = restClient
                .post()
                .uri("/openapi/v2/media/upload/binary")
                .headers(headers -> headers.setBearerAuth(properties.requiredApiKey()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipart)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiError)
                .body(UploadResponse.class);

        if (response == null || response.code() != 0 || response.data() == null
                || response.data().fileName() == null || response.data().fileName().isBlank()) {
            throw new RunningHubException("RunningHub 上传失败：" + (response == null ? "响应为空" : response.message()));
        }
        return response.data().fileName();
    }

    private void waitBeforeUploadRetry() {
        try {
            Duration delay = properties.getUploadRetryDelay();
            Thread.sleep(Math.max(0, delay.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunningHubException("等待重新上传图片时被中断", exception);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    public byte[] download(String url) {
        return download(url, properties.getMaxDownloadBytes());
    }

    public byte[] download(String url, long maxDownloadBytes) {
        return download(URI.create(url), maxDownloadBytes);
    }

    public byte[] download(URI uri, long maxDownloadBytes) {
        LOGGER.info("生成文件下载请求 url={}", uri);
        SimpleClientHttpRequestFactory requestFactory = downloadRequestFactory();
        byte[] data = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 FashionImageAgent/1.0")
                .defaultHeader(HttpHeaders.CONNECTION, "close")
                .build()
                .get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::throwApiError)
                .body(byte[].class);
        if (data == null || data.length == 0) {
            throw new RunningHubException("生成图片下载结果为空");
        }
        if (data.length > maxDownloadBytes) {
            throw new RunningHubException("生成文件超过允许大小：" + data.length + " bytes");
        }
        LOGGER.info("生成文件下载成功 url={} size={} bytes", uri, data.length);
        return data;
    }

    public Path downloadToFile(URI uri, Path target, long maxDownloadBytes) {
        return downloadToFile(uri, target, maxDownloadBytes, downloadRequestFactory());
    }

    /**
     * Uses Conscrypt/BoringSSL for COS endpoints that reset both the JDK and Windows Schannel TLS handshakes.
     */
    public Path downloadToFileViaConscrypt(URI uri, Path target, long maxDownloadBytes) {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path temporary = normalizedTarget.resolveSibling(normalizedTarget.getFileName() + ".conscrypt.part");
        long startedAt = System.nanoTime();
        try {
            Path parent = normalizedTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(temporary);

            SSLContext sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider());
            sslContext.init(null, null, null);
            LOGGER.info("生成文件 Conscrypt 下载开始 url={} target={}", uri, normalizedTarget);
            long size = downloadViaConscryptSocket(sslContext, uri, temporary, maxDownloadBytes);
            if (size == 0) {
                throw new RunningHubException("生成文件 Conscrypt 下载结果为空");
            }
            Files.move(temporary, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("生成文件 Conscrypt 下载成功 url={} target={} size={} bytes elapsedMs={}",
                    uri, normalizedTarget, size, elapsedMillis(startedAt));
            return normalizedTarget;
        } catch (GeneralSecurityException | IOException exception) {
            deletePartialFile(temporary);
            throw new RunningHubException("生成文件 Conscrypt 下载失败：" + uri, exception);
        } catch (RuntimeException exception) {
            deletePartialFile(temporary);
            throw exception;
        }
    }

    private long downloadViaConscryptSocket(
            SSLContext sslContext, URI uri, Path temporary, long maxDownloadBytes) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new RunningHubException("Conscrypt 下载仅支持合法的 HTTPS 地址：" + uri);
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 443;
        IOException lastFailure = null;
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            try {
                LOGGER.info("生成文件 Conscrypt 节点连接开始 host={} address={} port={}",
                        uri.getHost(), address.getHostAddress(), port);
                long size = downloadViaConscryptAddress(
                        sslContext, uri, address, port, temporary, maxDownloadBytes);
                LOGGER.info("生成文件 Conscrypt 节点连接成功 host={} address={} size={} bytes",
                        uri.getHost(), address.getHostAddress(), size);
                return size;
            } catch (IOException exception) {
                lastFailure = exception;
                deletePartialFile(temporary);
                LOGGER.warn("生成文件 Conscrypt 节点连接失败 host={} address={} cause={}",
                        uri.getHost(), address.getHostAddress(), exception.getMessage());
            }
        }
        throw new IOException("所有 COS 节点均下载失败：" + uri.getHost(), lastFailure);
    }

    private long downloadViaConscryptAddress(
            SSLContext sslContext,
            URI uri,
            InetAddress address,
            int port,
            Path temporary,
            long maxDownloadBytes) throws IOException {
        int connectTimeoutMillis = Math.toIntExact(properties.getDownloadConnectTimeout().toMillis());
        int readTimeoutMillis = Math.toIntExact(properties.getDownloadReadTimeout().toMillis());
        try (Socket rawSocket = new Socket()) {
            rawSocket.connect(new InetSocketAddress(address, port), connectTimeoutMillis);
            try (SSLSocket socket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(rawSocket, uri.getHost(), port, true)) {
                socket.setSoTimeout(readTimeoutMillis);
                SSLParameters sslParameters = socket.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(sslParameters);
                socket.startHandshake();

                String requestTarget = uri.getRawPath();
                if (requestTarget == null || requestTarget.isBlank()) {
                    requestTarget = "/";
                }
                if (uri.getRawQuery() != null) {
                    requestTarget += "?" + uri.getRawQuery();
                }
                String request = "GET " + requestTarget + " HTTP/1.1\r\n"
                        + "Host: " + uri.getHost() + "\r\n"
                        + "User-Agent: Mozilla/5.0 FashionImageAgent/1.0\r\n"
                        + "Accept: */*\r\n"
                        + "Connection: close\r\n\r\n";
                OutputStream socketOutput = socket.getOutputStream();
                socketOutput.write(request.getBytes(StandardCharsets.US_ASCII));
                socketOutput.flush();

                InputStream input = socket.getInputStream();
                String statusLine = readHttpLine(input);
                if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                    throw new IOException("COS 返回了无效 HTTP 状态行：" + statusLine);
                }
                String[] statusParts = statusLine.split(" ", 3);
                int statusCode = statusParts.length > 1 ? Integer.parseInt(statusParts[1]) : -1;
                long contentLength = -1;
                boolean chunked = false;
                String headerLine;
                while ((headerLine = readHttpLine(input)) != null && !headerLine.isEmpty()) {
                    int separator = headerLine.indexOf(':');
                    if (separator <= 0) {
                        continue;
                    }
                    String name = headerLine.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                    String value = headerLine.substring(separator + 1).trim();
                    if (name.equals("content-length")) {
                        contentLength = Long.parseLong(value);
                    } else if (name.equals("transfer-encoding")
                            && value.toLowerCase(Locale.ROOT).contains("chunked")) {
                        chunked = true;
                    }
                }
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IOException("COS 下载返回 HTTP " + statusCode + "：" + uri);
                }
                if (contentLength > maxDownloadBytes) {
                    throw new RunningHubException("生成文件超过允许大小：" + contentLength + " bytes");
                }
                try (OutputStream fileOutput = Files.newOutputStream(temporary)) {
                    if (chunked) {
                        return copyChunked(input, fileOutput, maxDownloadBytes, uri);
                    }
                    if (contentLength >= 0) {
                        return copyFixedLength(input, fileOutput, contentLength, maxDownloadBytes, uri);
                    }
                    return copyWithLimit(input, fileOutput, maxDownloadBytes, uri);
                }
            }
        } catch (NumberFormatException exception) {
            throw new IOException("COS 返回的 HTTP 响应头格式错误：" + uri, exception);
        }
    }

    private static String readHttpLine(InputStream input) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                if (line.size() >= 32 * 1024) {
                    throw new IOException("COS 返回的 HTTP 响应头过长");
                }
                line.write(value);
            }
        }
        if (value == -1 && line.size() == 0) {
            return null;
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }

    private static long copyFixedLength(
            InputStream input,
            OutputStream output,
            long contentLength,
            long maxBytes,
            URI uri) throws IOException {
        if (contentLength > maxBytes) {
            throw new RunningHubException("生成文件超过允许大小：" + contentLength + " bytes");
        }
        byte[] buffer = new byte[64 * 1024];
        long remaining = contentLength;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new IOException("COS 下载提前结束，仍缺少 " + remaining + " bytes：" + uri);
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        return contentLength;
    }

    private static long copyChunked(
            InputStream input, OutputStream output, long maxBytes, URI uri) throws IOException {
        long total = 0;
        while (true) {
            String sizeLine = readHttpLine(input);
            if (sizeLine == null) {
                throw new IOException("COS chunked 响应提前结束：" + uri);
            }
            int extension = sizeLine.indexOf(';');
            String value = (extension >= 0 ? sizeLine.substring(0, extension) : sizeLine).trim();
            long chunkSize = Long.parseLong(value, 16);
            if (chunkSize == 0) {
                while (true) {
                    String trailer = readHttpLine(input);
                    if (trailer == null || trailer.isEmpty()) {
                        return total;
                    }
                }
            }
            if (total + chunkSize > maxBytes) {
                throw new RunningHubException("生成文件超过允许大小：" + (total + chunkSize) + " bytes");
            }
            copyFixedLength(input, output, chunkSize, maxBytes - total, uri);
            total += chunkSize;
            String chunkTerminator = readHttpLine(input);
            if (chunkTerminator == null || !chunkTerminator.isEmpty()) {
                throw new IOException("COS chunked 响应分隔符无效：" + uri);
            }
        }
    }

    private static long copyWithLimit(InputStream input, OutputStream output, long maxBytes, URI uri)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        long nextProgress = 10L * 1024 * 1024;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new RunningHubException("生成文件超过允许大小：" + total + " bytes");
            }
            output.write(buffer, 0, read);
            if (total >= nextProgress) {
                LOGGER.info("生成文件下载进度 url={} downloaded={} bytes", uri, total);
                nextProgress = total + 10L * 1024 * 1024;
            }
        }
        return total;
    }

    /** Uses Windows' browser-compatible HTTP stack when COS resets JVM and curl TLS connections. */
    public Path downloadToFileViaPowerShell(URI uri, Path target, long maxDownloadBytes) {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path temporary = normalizedTarget.resolveSibling(normalizedTarget.getFileName() + ".powershell.part");
        Path logFile = normalizedTarget.resolveSibling(normalizedTarget.getFileName() + ".powershell.log");
        try {
            Path parent = normalizedTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(logFile);
            long timeoutSeconds = Math.max(60L,
                    properties.getDownloadConnectTimeout().plus(properties.getDownloadReadTimeout()).toSeconds());
            String script = "$ErrorActionPreference = 'Stop'; "
                    + "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; "
                    + "Invoke-WebRequest -UseBasicParsing -Uri " + powerShellQuote(uri.toASCIIString())
                    + " -OutFile " + powerShellQuote(temporary.toString())
                    + " -TimeoutSec " + timeoutSeconds;
            String encodedScript = Base64.getEncoder().encodeToString(
                    script.getBytes(StandardCharsets.UTF_16LE));
            List<String> command = List.of(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encodedScript);
            LOGGER.info("生成文件 PowerShell 下载开始 url={} target={}", uri, normalizedTarget);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            if (!process.waitFor(timeoutSeconds + 15L, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RunningHubException("生成文件 PowerShell 下载超时：" + uri);
            }
            String output = Files.exists(logFile)
                    ? new String(Files.readAllBytes(logFile), StandardCharsets.ISO_8859_1).trim()
                    : "";
            if (process.exitValue() != 0) {
                throw new RunningHubException("生成文件 PowerShell 下载失败，exitCode="
                        + process.exitValue() + (output.isBlank() ? "" : "，output=" + output));
            }
            long size = Files.size(temporary);
            if (size == 0 || size > maxDownloadBytes) {
                throw new RunningHubException("生成文件 PowerShell 下载大小异常：" + size + " bytes");
            }
            Files.move(temporary, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(logFile);
            LOGGER.info("生成文件 PowerShell 下载成功 url={} target={} size={} bytes", uri, normalizedTarget, size);
            return normalizedTarget;
        } catch (IOException exception) {
            deletePartialFile(temporary);
            throw new RunningHubException("生成文件 PowerShell 下载失败：" + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deletePartialFile(temporary);
            throw new RunningHubException("生成文件 PowerShell 下载被中断：" + uri, exception);
        } catch (RuntimeException exception) {
            deletePartialFile(temporary);
            throw exception;
        }
    }

    private static String powerShellQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private Path downloadToFile(
            URI uri, Path target, long maxDownloadBytes, ClientHttpRequestFactory requestFactory) {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path temporary = normalizedTarget.resolveSibling(normalizedTarget.getFileName() + ".part");
        long startedAt = System.nanoTime();
        LOGGER.info(
                "生成文件流式下载开始 url={} target={} connectTimeout={} readTimeout={} maxBytes={}",
                uri,
                normalizedTarget,
                properties.getDownloadConnectTimeout(),
                properties.getDownloadReadTimeout(),
                maxDownloadBytes);
        try {
            Path parent = normalizedTarget.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(temporary);
            Long downloadedBytes = RestClient.builder()
                    .requestFactory(requestFactory)
                    .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 FashionImageAgent/1.0")
                    .defaultHeader(HttpHeaders.CONNECTION, "close")
                    .build()
                    .get()
                    .uri(uri)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throwApiError(request, response);
                        }
                        long contentLength = response.getHeaders().getContentLength();
                        if (contentLength > maxDownloadBytes) {
                            throw new RunningHubException("生成文件超过允许大小：" + contentLength + " bytes");
                        }
                        LOGGER.info(
                                "生成文件下载响应已建立 url={} status={} contentLength={} bytes",
                                uri,
                                response.getStatusCode(),
                                contentLength);
                        try (InputStream input = response.getBody();
                                OutputStream output = Files.newOutputStream(temporary)) {
                            byte[] buffer = new byte[64 * 1024];
                            long total = 0;
                            long nextProgress = 10L * 1024 * 1024;
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                total += read;
                                if (total > maxDownloadBytes) {
                                    throw new RunningHubException("生成文件超过允许大小：" + total + " bytes");
                                }
                                output.write(buffer, 0, read);
                                if (total >= nextProgress) {
                                    LOGGER.info("生成文件下载进度 url={} downloaded={} bytes", uri, total);
                                    nextProgress = total + 10L * 1024 * 1024;
                                }
                            }
                            return total;
                        }
                    });
            if (downloadedBytes == null || downloadedBytes == 0) {
                throw new RunningHubException("生成文件下载结果为空");
            }
            Files.move(temporary, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info(
                    "生成文件流式下载成功 url={} target={} size={} bytes elapsedMs={}",
                    uri,
                    normalizedTarget,
                    downloadedBytes,
                    elapsedMillis(startedAt));
            return normalizedTarget;
        } catch (IOException exception) {
            deletePartialFile(temporary);
            throw new RunningHubException("保存生成文件失败：" + normalizedTarget, exception);
        } catch (RuntimeException exception) {
            deletePartialFile(temporary);
            throw exception;
        }
    }

    private SimpleClientHttpRequestFactory downloadRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getDownloadConnectTimeout());
        requestFactory.setReadTimeout(properties.getDownloadReadTimeout());
        return requestFactory;
    }

    private static void deletePartialFile(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            LOGGER.warn("清理未完成的下载文件失败 file={}", temporary, exception);
        }
    }

    private void throwApiError(org.springframework.http.HttpRequest request,
                               org.springframework.http.client.ClientHttpResponse response) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        throw new RunningHubException("RunningHub API 请求失败（HTTP " + response.getStatusCode().value() + "）：" + body);
    }

    record RunRequest(List<NodeInput> nodeInfoList, String instanceType, boolean usePersonalQueue) {}

    record QueryRequest(String taskId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskResponse(
            String taskId,
            String status,
            String errorCode,
            String errorMessage,
            Object failedReason,
            String promptTips,
            List<TaskResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskResult(String url, String nodeId, String outputType, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UploadResponse(int code, String message, UploadData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UploadData(
            String type,
            @JsonProperty("download_url") String downloadUrl,
            String fileName,
            String size) {}
}
