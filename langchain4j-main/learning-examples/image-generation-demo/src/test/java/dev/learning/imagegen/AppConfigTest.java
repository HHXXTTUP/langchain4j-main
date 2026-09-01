package dev.learning.imagegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void shouldApplyDefaults() {
        AppConfig config = AppConfig.from(Map.of("OPENAI_API_KEY", "test-key"));

        assertEquals("test-key", config.apiKey());
        assertEquals("gpt-image-1", config.modelName());
        assertEquals("png", config.outputFormat());
        assertEquals(Path.of("output"), config.outputDirectory());
        assertEquals(Duration.ofSeconds(120), config.timeout());
        assertNull(config.baseUrl());
    }

    @Test
    void shouldReadOverrides() {
        Map<String, String> environment = new HashMap<>();
        environment.put("OPENAI_API_KEY", "test-key");
        environment.put("OPENAI_BASE_URL", "http://localhost:8080/v1/");
        environment.put("OPENAI_IMAGE_MODEL", "gpt-image-1-mini");
        environment.put("OPENAI_IMAGE_SIZE", "1024x1024");
        environment.put("OPENAI_IMAGE_QUALITY", "medium");
        environment.put("OPENAI_IMAGE_OUTPUT_FORMAT", "webp");
        environment.put("IMAGE_OUTPUT_DIR", "generated-images");
        environment.put("OPENAI_TIMEOUT_SECONDS", "60");

        AppConfig config = AppConfig.from(environment);

        assertEquals("http://localhost:8080/v1/", config.baseUrl());
        assertEquals("gpt-image-1-mini", config.modelName());
        assertEquals("1024x1024", config.size());
        assertEquals("medium", config.quality());
        assertEquals("webp", config.outputFormat());
        assertEquals(Path.of("generated-images"), config.outputDirectory());
        assertEquals(Duration.ofSeconds(60), config.timeout());
    }

    @Test
    void shouldRejectMissingApiKey() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.from(Map.of()));
    }

    @Test
    void shouldRejectUnsupportedOutputFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfig.from(Map.of(
                        "OPENAI_API_KEY", "test-key",
                        "OPENAI_IMAGE_OUTPUT_FORMAT", "bmp")));
    }
}
