package dev.learning.fashionagent.ai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ZhipuCompatibleChatModelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCallZhipuCompatibleEndpointAndRequestJsonObject() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/paas/v4/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            byte[] response = successfulPromptResponse();
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            FashionAiProperties properties = new FashionAiProperties();
            properties.setApiKey("zhipu-test-key");
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/paas/v4");
            properties.setTimeout(Duration.ofSeconds(5));

            FashionAiConfiguration configuration = new FashionAiConfiguration();
            FashionAiConfiguration.FashionAiModelRuntime runtime = configuration.fashionAiModelRuntime(properties);
            PortraitAiService service = configuration.portraitAiService(properties, runtime);

            PortraitPromptSpec result = service.enhancePrompt("卧室美女");

            assertTrue(result.aiEnhanced());
            assertTrue(result.generationPrompt().startsWith("完整人物生成提示词"));
            assertTrue(result.generationPrompt().contains("亚洲面孔"));
            assertTrue(result.generationPrompt().contains("20到30岁"));
            assertTrue(result.generationPrompt().contains("身材匀称健美"));
            assertTrue(result.generationPrompt().contains("适合公开展示"));
            assertTrue(result.generationPrompt().contains("1080x1920"));
            assertTrue(result.generationPrompt().contains("本次人物外观差异化方向"));
            assertTrue(result.generationPrompt().contains("用户原始场景锚点"));
            assertTrue(result.generationPrompt().contains("卧室室内环境"));
            assertEquals("Bearer zhipu-test-key", authorization.get());
            JsonNode requestJson = OBJECT_MAPPER.readTree(requestBody.get());
            assertEquals("glm-4.6v-flash", requestJson.path("model").asText());
            assertEquals("json_object", requestJson.path("response_format").path("type").asText());
            assertTrue(requestBody.get().contains("generationPrompt"));
            assertTrue(requestBody.get().contains("固定业务约束"));
            assertTrue(requestBody.get().contains("亚洲面孔"));
            assertTrue(requestBody.get().contains("脸型、眉形、眼睛形状与眼神"));
            assertTrue(requestBody.get().contains("发色、发长、分缝、卷直程度"));
            assertTrue(requestBody.get().contains("卧室美女"));
            assertTrue(requestBody.get().contains("暖色或晨光照明"));
        } finally {
            server.stop(0);
        }
    }

    private static byte[] successfulPromptResponse() throws IOException {
        String content = OBJECT_MAPPER.writeValueAsString(Map.of(
                "appearance", "成年女性，自然面容",
                "bodyAndPose", "自然站立，全身完整",
                "environment", "室内摄影棚",
                "lighting", "柔和自然光",
                "composition", "全身居中构图",
                "visualStyle", "写实人像摄影",
                "generationPrompt", "完整人物生成提示词"));
        return OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "id", "glm-test-response",
                "object", "chat.completion",
                "created", 0,
                "model", "glm-4.6v-flash",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30)));
    }
}
