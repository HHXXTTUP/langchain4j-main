package dev.learning.fashionagent.job;

import dev.learning.fashionagent.ai.OutfitQualityReport;

public record OutfitAttemptView(
        int attemptNumber,
        String imageUrl,
        String prompt,
        OutfitQualityReport qualityReport,
        boolean selected) {}
