package dev.learning.fashionagent.job;

import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.ai.PortraitQualityReport;
import dev.learning.fashionagent.pipeline.PipelineStage;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobView(
        UUID id,
        JobStatus status,
        PipelineStage stage,
        String message,
        String prompt,
        PortraitGenerationMode portraitGenerationMode,
        String originalImageUrl,
        String clothingPreviewUrl,
        String clothingFileName,
        String clothingMatchName,
        Double clothingMatchPercentage,
        String clothingMatchRule,
        String finalImageUrl,
        PortraitPromptSpec portraitPrompt,
        List<PortraitAttemptView> portraitAttempts,
        PortraitQualityReport finalPortraitQualityReport,
        FashionReferenceSpec fashionAnalysis,
        List<OutfitAttemptView> attempts,
        OutfitQualityReport finalQualityReport,
        String reply,
        String error,
        String errorDetails,
        Instant createdAt,
        Instant updatedAt) {

    public JobView {
        portraitGenerationMode = PortraitGenerationMode.defaultIfNull(portraitGenerationMode);
    }
}
