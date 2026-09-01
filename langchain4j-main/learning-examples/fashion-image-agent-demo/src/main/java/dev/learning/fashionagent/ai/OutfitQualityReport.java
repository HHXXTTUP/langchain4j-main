package dev.learning.fashionagent.ai;

import java.util.List;

public record OutfitQualityReport(
        AnalysisMode mode,
        boolean evaluated,
        boolean passed,
        int overallScore,
        int clothingMatchScore,
        int headAccessoryMatchScore,
        int identityPreservationScore,
        boolean retryable,
        String summary,
        List<String> differences,
        List<String> missingElements,
        String correctionPrompt) {

    public OutfitQualityReport {
        mode = mode == null ? AnalysisMode.RULE_BASED_FALLBACK : mode;
        overallScore = score(overallScore);
        clothingMatchScore = score(clothingMatchScore);
        headAccessoryMatchScore = score(headAccessoryMatchScore);
        identityPreservationScore = score(identityPreservationScore);
        summary = summary == null || summary.isBlank() ? "未获得视觉质检摘要" : summary.trim();
        differences = safeList(differences);
        missingElements = safeList(missingElements);
        correctionPrompt = correctionPrompt == null ? "" : correctionPrompt.trim();
    }

    public static OutfitQualityReport notEvaluated(String reason) {
        return new OutfitQualityReport(
                AnalysisMode.RULE_BASED_FALLBACK,
                false,
                true,
                0,
                0,
                0,
                0,
                false,
                reason,
                List.of(),
                List.of(),
                "");
    }

    private static int score(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
