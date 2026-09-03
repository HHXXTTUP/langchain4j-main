package dev.learning.fashionagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.config.GptImageProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GptImageClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GptImageClient.class);
    private static final int MAX_LOG_CHARS = 8000;
    private final GptImageProperties properties;
    private final QwenRestClientProvider clients;
    private final ObjectMapper mapper;

    public GptImageClient(GptImageProperties properties, QwenRestClientProvider clients, ObjectMapper mapper) {
        this.properties = properties; this.clients = clients; this.mapper = mapper;
    }

    public Path generate(String prompt, Path output) throws IOException {
        return generate(prompt, output, properties.requiredApiKey());
    }
    public Path generate(String prompt, Path output, String apiKey) throws IOException {
        return generate(prompt, output, apiKey, "1024x1024");
    }
    public Path generate(String prompt, Path output, String apiKey, String size) throws IOException {
        String requestedSize = size == null || size.isBlank() ? "1024x1024" : size.trim();
        Map<String, Object> body = Map.of("model", properties.requiredModel(), "prompt", prompt,
                "size", requestedSize, "quality", "medium", "output_format", "png", "n", 1);
        JsonNode response = postJson("/v1/images/generations", body, apiKey);
        Path result = writeImage(response, output);
        LOGGER.info("GPT Images 图片落盘 operation=GPT_IMAGES_GENERATION output={} bytes={}",
                result, Files.size(result));
        return result;
    }

    public Path edit(List<Path> images, String prompt, Path output) throws IOException {
        return edit(images, prompt, output, properties.requiredApiKey());
    }
    public Path edit(List<Path> images, String prompt, Path output, String apiKey) throws IOException {
        return edit(images, prompt, output, apiKey, "1024x1024");
    }
    /**
     * Edits one or more reference images using the Images edits contract.
     * The size is explicit because the image API does not infer aspect ratio
     * from prompt text (for example, a 16:9 prompt with a square size remains square).
     */
    public Path edit(List<Path> images, String prompt, Path output, String apiKey, String size) throws IOException {
        if (images == null || images.isEmpty()) throw new IllegalArgumentException("图生图至少需要一张参考图");
        String requestedSize = size == null || size.isBlank() ? "1024x1024" : size.trim();
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("model", properties.requiredModel()); multipart.part("prompt", prompt);
        multipart.part("size", requestedSize); multipart.part("quality", "medium");
        multipart.part("output_format", "png"); multipart.part("n", "1");
        for (Path image : images) multipart.part("image[]", new FileSystemResource(image.toFile()));
        String operation = "GPT_IMAGES_EDIT";
        String publicEndpoint = endpoint("/v1/images/edits").toString();
        QwenRestClientProvider.Selection selection = clients.select();
        long started = System.nanoTime();
        GptImageProperties.ApiKeyDiagnostic key = properties.apiKeyDiagnostic(apiKey);
        LOGGER.info("GPT Images 请求发送 operation={} method=POST endpoint={} route={} model={} promptChars={} requestBodyChars={} imageCount={} imageBytes={} outputFormat={} size={} quality={} keySource={} keyFingerprint={} keyLength={}",
                operation, publicEndpoint, selection.route(), properties.getModel(), length(prompt),
                multipartSummaryLength(prompt, images), images.size(), imageBytes(images), "png", requestedSize, "medium",
                key.source(), key.fingerprint(), key.length());
        LOGGER.info("GPT Images 请求详情 operation={} fields={{model:{},promptChars:{},size:{},quality:medium,output_format:png,n:1}} images={}",
                operation, properties.requiredModel(), length(prompt), requestedSize, imageNames(images));
        try {
            JsonNode response = selection.client().post().uri(publicEndpoint).headers(h -> h.setBearerAuth(apiKey))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(multipart.build()).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        String responseBody = readBody(result);
                        LOGGER.error("GPT Images HTTP 错误 operation={} status={} endpoint={} route={} durationMs={} responseBodyChars={} responseBody={}",
                                operation, result.getStatusCode().value(), publicEndpoint, selection.route(), elapsedMillis(started),
                                responseBody.length(), truncate(responseBody));
                        throw new IllegalStateException("GPT Images 图生图请求失败（HTTP " + result.getStatusCode().value() + "）："
                                + truncate(responseBody));
                    }).body(JsonNode.class);
            LOGGER.info("GPT Images 响应成功 operation={} status=2xx endpoint={} route={} model={} durationMs={} responseBodyChars={} responseSummary={}",
                    operation, publicEndpoint, selection.route(), properties.requiredModel(), elapsedMillis(started),
                    response == null ? 0 : response.toString().length(), summarize(response));
            Path result = writeImage(response, output);
            LOGGER.info("GPT Images 图片落盘 operation={} output={} bytes={}", operation, result, Files.size(result));
            return result;
        } catch (RuntimeException exception) {
            LOGGER.warn("GPT Images 请求失败（不会自动重试） operation={} endpoint={} route={} model={} durationMs={} reason={}",
                    operation, publicEndpoint, selection.route(), properties.getModel(), elapsedMillis(started), rootMessage(exception), exception);
            throw exception;
        }
    }

    private JsonNode postJson(String path, Map<String, Object> body, String apiKey) throws IOException {
        String operation = "GPT_IMAGES_GENERATION";
        String publicEndpoint = endpoint(path).toString();
        String requestBody = mapper.writeValueAsString(body);
        QwenRestClientProvider.Selection selection = clients.select();
        long started = System.nanoTime();
        GptImageProperties.ApiKeyDiagnostic key = properties.apiKeyDiagnostic(apiKey);
        LOGGER.info("GPT Images 请求发送 operation={} method=POST endpoint={} route={} model={} promptChars={} requestBodyChars={} outputFormat={} size={} quality={} keySource={} keyFingerprint={} keyLength={}",
                operation, publicEndpoint, selection.route(), properties.requiredModel(), length(body.get("prompt")), requestBody.length(),
                body.get("output_format"), body.get("size"), body.get("quality"), key.source(), key.fingerprint(), key.length());
        LOGGER.info("GPT Images 请求详情 operation={} headers=[Content-Type: application/json, Authorization: Bearer **REDACTED**] bodySummary={}",
                operation, jsonBodySummary(body));
        try {
            JsonNode response = selection.client().post().uri(publicEndpoint).headers(h -> h.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        String responseBody = readBody(result);
                        LOGGER.error("GPT Images HTTP 错误 operation={} status={} endpoint={} route={} durationMs={} responseBodyChars={} responseBody={}",
                                operation, result.getStatusCode().value(), publicEndpoint, selection.route(), elapsedMillis(started),
                                responseBody.length(), truncate(responseBody));
                        throw new IllegalStateException("GPT Images 文生图请求失败（HTTP " + result.getStatusCode().value() + "）："
                                + truncate(responseBody));
                    }).body(JsonNode.class);
            LOGGER.info("GPT Images 响应成功 operation={} status=2xx endpoint={} route={} model={} durationMs={} responseBodyChars={} responseSummary={}",
                    operation, publicEndpoint, selection.route(), properties.requiredModel(), elapsedMillis(started),
                    response == null ? 0 : response.toString().length(), summarize(response));
            return response;
        } catch (RuntimeException exception) {
            LOGGER.warn("GPT Images 请求失败（不会自动重试） operation={} endpoint={} route={} model={} durationMs={} reason={}",
                    operation, publicEndpoint, selection.route(), properties.getModel(), elapsedMillis(started), rootMessage(exception), exception);
            throw exception;
        }
    }

    private Path writeImage(JsonNode response, Path output) throws IOException {
        String encoded = response == null ? null : response.at("/data/0/b64_json").asText(null);
        if (encoded == null || encoded.isBlank()) throw new IllegalStateException("GPT Images 未返回图片数据");
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        Files.write(output, Base64.getDecoder().decode(encoded));
        return output;
    }
    private static String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try { return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException exception) { return "<无法读取响应体: " + rootMessage(exception) + ">"; }
    }
    private static String summarize(JsonNode response) {
        if (response == null) return "null";
        JsonNode data = response.path("data");
        JsonNode first = data.isArray() && !data.isEmpty() ? data.get(0) : null;
        return "dataCount=" + (data.isArray() ? data.size() : 0)
                + ",b64Chars=" + (first == null ? 0 : first.path("b64_json").asText("").length())
                + ",urlPresent=" + (first != null && first.hasNonNull("url"));
    }
    private static String imageNames(List<Path> images) {
        return images.stream().map(path -> path == null ? "null" : path.getFileName().toString()).toList().toString();
    }
    private static long imageBytes(List<Path> images) {
        long total = 0;
        for (Path image : images) { try { total += Files.size(image); } catch (IOException ignored) { } }
        return total;
    }
    private static int multipartSummaryLength(String prompt, List<Path> images) {
        return length(prompt) + images.stream().mapToInt(path -> path == null ? 0 : path.toString().length()).sum();
    }
    private static int length(Object value) { return value == null ? 0 : value.toString().length(); }
    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_LOG_CHARS) return value == null ? "" : value;
        return value.substring(0, MAX_LOG_CHARS) + "...(truncated)";
    }
    private static String jsonBodySummary(Map<String, Object> body) {
        return "{model=" + body.get("model") + ",promptChars=" + length(body.get("prompt"))
                + ",promptPreview=" + preview(body.get("prompt")) + ",size=" + body.get("size")
                + ",quality=" + body.get("quality") + ",output_format=" + body.get("output_format")
                + ",n=" + body.get("n") + "}";
    }
    private static String preview(Object value) {
        if (value == null) return "";
        String normalized = value.toString().replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 300) return normalized;
        return normalized.substring(0, 300) + "...(truncated)";
    }
    private static long elapsedMillis(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }
    private URI endpoint(String path) { return URI.create(properties.getBaseUrl().toString().replaceAll("/+$", "") + path); }
}
