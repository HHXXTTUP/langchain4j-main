package dev.learning.fashionagent.video;

import java.time.Instant;
import java.util.UUID;

public record VideoGenerationView(
        UUID id,
        UUID sourceJobId,
        VideoGenerationStatus status,
        String message,
        String sourceVideoFileName,
        String firstSegmentStatus,
        String secondSegmentStatus,
        String finalVideoUrl,
        String finalVideoFileName,
        VideoQualityReport qualityReport,
        String error,
        Instant createdAt,
        Instant updatedAt,
        String sourceVideoPath,
        String firstSegmentRemoteUrl,
        String secondSegmentRemoteUrl,
        boolean downloadRetryable) {}
