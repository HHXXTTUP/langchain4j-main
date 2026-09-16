package dev.learning.fashionagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.config.GeminiProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Sends text prompts using the Gemini generateContent contract from gemini3.7.md. */
@Component
public class GeminiTextClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiTextClient.class);
    private final GeminiProperties properties;
    private final QwenRestClientProvider clients;
    private final ObjectMapper mapper;

    public GeminiTextClient(GeminiProperties properties, QwenRestClientProvider clients, ObjectMapper mapper) {
        this.properties = properties;
        this.clients = clients;
        this.mapper = mapper;
    }

    public JsonNode call(String operation, String system, String user, String apiKey) throws IOException {
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        String model = properties.getModel();
        URI endpoint = URI.create(base + "/v1beta/models/" + model + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        String publicEndpoint = base + "/v1beta/models/" + model + ":generateContent";
        String prompt = "【系统要求】\n" + (system == null ? "" : system) + "\n【用户内容】\n" + (user == null ? "" : user);
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        String requestBody = mapper.writeValueAsString(body);
        QwenRestClientProvider.Selection selection = clients.select();
        long started = System.nanoTime();
        LOGGER.info("Gemini 文本请求发送 operation={} model={} endpoint={} route={} promptChars={} bodyChars={}",
                operation, model, publicEndpoint, selection.route(), prompt.length(), requestBody.length());
        LOGGER.info("Gemini 文本请求详情 operation={} method=POST headers=[Content-Type: application/json, x-api-key: **REDACTED**] body={}", operation, requestBody);
        try {
            JsonNode response = selection.client().post().uri(endpoint).headers(headers -> {
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("User-Agent", "atelier-flow/1.0");
            }).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        String error = new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        LOGGER.error("Gemini HTTP 错误 operation={} status={} durationMs={} responseBody={}",
                                operation, result.getStatusCode().value(), elapsedMillis(started), error);
                        throw new IllegalStateException("Gemini 接口请求失败（HTTP " + result.getStatusCode().value() + "）：" + error);
                    }).body(JsonNode.class);
            LOGGER.info("Gemini 文本响应成功 operation={} model={} durationMs={} responseChars={}",
                    operation, model, elapsedMillis(started), response == null ? 0 : response.toString().length());
            return response;
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini 文本请求失败（不会自动重试） operation={} model={} endpoint={} route={} durationMs={} reason={}",
                    operation, model, publicEndpoint, selection.route(), elapsedMillis(started), rootMessage(exception), exception);
            throw exception;
        }
    }

    /** Calls Gemini's native multimodal generateContent endpoint with a video part. */
    public String callVideo(String operation, String prompt, Path video, String fileUri, String apiKey) throws IOException {
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        String model = properties.getModel();
        URI endpoint = URI.create(base + "/v1beta/models/" + model + ":generateContent?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
        String mime = Files.probeContentType(video);
        if (mime == null || !mime.startsWith("video/")) mime = "video/mp4";
        Map<String, Object> videoPart = new LinkedHashMap<>();
        if (fileUri != null && (fileUri.startsWith("http://") || fileUri.startsWith("https://"))) {
            videoPart.put("fileData", Map.of("mimeType", mime, "fileUri", fileUri));
        } else {
            videoPart.put("inlineData", Map.of("mimeType", mime,
                    "data", Base64.getEncoder().encodeToString(Files.readAllBytes(video))));
        }
        Map<String, Object> body = Map.of("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(videoPart, Map.of("text", prompt)))));
        QwenRestClientProvider.Selection selection = clients.select();
        long started = System.nanoTime();
        LOGGER.info("Gemini 视频请求发送 operation={} model={} endpoint={} route={} file={} mime={} inputMode={} promptChars={}",
                operation, model, base + "/v1beta/models/" + model + ":generateContent", selection.route(), video,
                mime, videoPart.containsKey("fileData") ? "fileData" : "inlineData", prompt.length());
        try {
            JsonNode response = selection.client().post().uri(endpoint).headers(headers -> {
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("User-Agent", "atelier-flow/1.0");
            }).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        String error = new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new IllegalStateException("Gemini 视频接口请求失败（HTTP " + result.getStatusCode().value() + "）：" + error);
                    }).body(JsonNode.class);
            String text = extractVideoText(response);
            if (text == null || text.isBlank()) throw new IllegalStateException("Gemini 视频接口未返回脚本文本");
            LOGGER.info("Gemini 视频响应成功 operation={} model={} durationMs={} responseChars={}",
                    operation, model, elapsedMillis(started), response == null ? 0 : response.toString().length());
            return text.trim();
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini 视频请求失败 operation={} model={} durationMs={} reason={}",
                    operation, model, elapsedMillis(started), rootMessage(exception), exception);
            throw exception;
        }
    }

    private static String extractVideoText(JsonNode response) {
        if (response == null) return null;
        JsonNode parts = response.at("/candidates/0/content/parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) if (part.has("text") && part.get("text").isTextual()) return part.get("text").asText();
        }
        JsonNode text = response.at("/candidates/0/content/parts/0/text");
        return text.isTextual() ? text.asText() : null;
    }

    private static long elapsedMillis(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }
}
