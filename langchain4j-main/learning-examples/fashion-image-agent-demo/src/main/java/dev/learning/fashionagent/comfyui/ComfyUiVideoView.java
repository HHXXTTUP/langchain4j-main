package dev.learning.fashionagent.comfyui;

import java.time.Instant;
import java.util.UUID;

public record ComfyUiVideoView(
        UUID id,
        String prompt,
        int duration,
        String resolution,
        int imageCount,
        ComfyUiVideoStatus status,
        String message,
        String remoteTaskId,
        String remoteResultUrl,
        String finalVideoUrl,
        String finalVideoFileName,
        String error,
        Instant createdAt,
        Instant updatedAt) {}
