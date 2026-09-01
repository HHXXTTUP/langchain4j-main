package dev.learning.imagegen;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

record AppConfig(
        String apiKey,
        String baseUrl,
        String modelName,
        String size,
        String quality,
        String outputFormat,
        Path outputDirectory,
        Duration timeout) {

    private static final String DEFAULT_MODEL = "gpt-image-1";
    private static final String DEFAULT_OUTPUT_FORMAT = "png";
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    static AppConfig from(Map<String, String> environment) {
        String apiKey = required(environment, "OPENAI_API_KEY");
        String outputFormat = valueOrDefault(environment, "OPENAI_IMAGE_OUTPUT_FORMAT", DEFAULT_OUTPUT_FORMAT);
        validateOutputFormat(outputFormat);

        long timeoutSeconds = parsePositiveLong(
                valueOrDefault(environment, "OPENAI_TIMEOUT_SECONDS", Long.toString(DEFAULT_TIMEOUT_SECONDS)),
                "OPENAI_TIMEOUT_SECONDS");

        return new AppConfig(
                apiKey,
                optional(environment, "OPENAI_BASE_URL"),
                valueOrDefault(environment, "OPENAI_IMAGE_MODEL", DEFAULT_MODEL),
                optional(environment, "OPENAI_IMAGE_SIZE"),
                optional(environment, "OPENAI_IMAGE_QUALITY"),
                outputFormat,
                Path.of(valueOrDefault(environment, "IMAGE_OUTPUT_DIR", "output")),
                Duration.ofSeconds(timeoutSeconds));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = optional(environment, name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String optional(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrDefault(Map<String, String> environment, String name, String defaultValue) {
        String value = optional(environment, name);
        return value == null ? defaultValue : value;
    }

    private static long parsePositiveLong(String value, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be greater than 0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a positive integer", e);
        }
    }

    private static void validateOutputFormat(String outputFormat) {
        if (!outputFormat.equals("png") && !outputFormat.equals("jpeg") && !outputFormat.equals("webp")) {
            throw new IllegalArgumentException("OPENAI_IMAGE_OUTPUT_FORMAT must be png, jpeg, or webp");
        }
    }
}
