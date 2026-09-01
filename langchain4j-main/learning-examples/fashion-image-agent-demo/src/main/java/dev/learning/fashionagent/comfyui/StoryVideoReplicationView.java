package dev.learning.fashionagent.comfyui;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryVideoReplicationView(
        UUID id,
        String sourceFileName,
        double sourceDurationSeconds,
        String speechSummary,
        StoryVideoPlan plan,
        String status,
        String message,
        List<ShotExecutionView> shotExecutions,
        String finalVideoUrl,
        String finalVideoFileName,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    public record ShotExecutionView(
            int sequence,
            String interfaceType,
            String status,
            String message,
            String remoteTaskId,
            String videoUrl,
            String firstFrameUrl,
            boolean firstFrameRecognized,
            String error) {}
}
