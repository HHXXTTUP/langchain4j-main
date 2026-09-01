package dev.learning.fashionagent.pipeline;

import dev.learning.fashionagent.ai.PortraitQualityReport;
import java.nio.file.Path;

public record PortraitAttempt(
        int attemptNumber,
        Path image,
        String prompt,
        PortraitQualityReport qualityReport,
        boolean selected) {

    public PortraitAttempt withSelected(boolean selected) {
        return new PortraitAttempt(attemptNumber, image, prompt, qualityReport, selected);
    }
}
