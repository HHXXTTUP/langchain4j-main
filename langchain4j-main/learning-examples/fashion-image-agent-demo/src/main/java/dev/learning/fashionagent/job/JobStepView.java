package dev.learning.fashionagent.job;

import dev.learning.fashionagent.pipeline.PipelineStage;
import java.time.Instant;

public record JobStepView(
        Long id,
        String eventType,
        PipelineStage stage,
        String message,
        String resultJson,
        Instant createdAt) {}
