package dev.learning.fashionagent.pipeline;

import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.ai.PortraitQualityReport;
import java.nio.file.Path;
import java.util.List;

public record PipelineResult(
        Path originalImage,
        Path clothingImage,
        Path finalImage,
        PortraitPromptSpec portraitPrompt,
        List<PortraitAttempt> portraitAttempts,
        PortraitQualityReport finalPortraitQualityReport,
        FashionReferenceSpec fashionAnalysis,
        List<OutfitAttempt> attempts,
        OutfitQualityReport finalQualityReport,
        String reply) {}
