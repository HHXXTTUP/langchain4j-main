package dev.learning.fashionagent.job;

import dev.learning.fashionagent.ai.PortraitQualityReport;

public record PortraitAttemptView(
        int attemptNumber,
        String imageUrl,
        String prompt,
        PortraitQualityReport qualityReport,
        boolean selected) {}
