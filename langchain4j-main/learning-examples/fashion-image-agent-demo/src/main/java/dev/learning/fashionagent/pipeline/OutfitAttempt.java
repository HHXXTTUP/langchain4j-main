package dev.learning.fashionagent.pipeline;

import dev.learning.fashionagent.ai.OutfitQualityReport;
import java.nio.file.Path;

public record OutfitAttempt(
        int attemptNumber,
        Path image,
        String prompt,
        OutfitQualityReport qualityReport,
        boolean selected) {

    public OutfitAttempt withSelected(boolean selected) {
        return new OutfitAttempt(attemptNumber, image, prompt, qualityReport, selected);
    }
}
