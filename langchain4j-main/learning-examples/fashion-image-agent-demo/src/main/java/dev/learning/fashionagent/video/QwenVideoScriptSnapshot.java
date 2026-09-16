package dev.learning.fashionagent.video;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record QwenVideoScriptSnapshot(UUID id, String address, String sourceFileName, Path videoPath,
                                      String status, String message, String script, String error,
                                      Instant createdAt, Instant updatedAt, String model) {
    public QwenVideoScriptSnapshot(UUID id, String address, String sourceFileName, Path videoPath,
                                   String status, String message, String script, String error,
                                   Instant createdAt, Instant updatedAt) {
        this(id, address, sourceFileName, videoPath, status, message, script, error,
                createdAt, updatedAt, "QWEN");
    }

    public String effectiveModel() {
        return "GEMINI".equalsIgnoreCase(model) ? "GEMINI" : "QWEN";
    }
}
