package dev.learning.fashionagent.ai;

import java.util.List;

public record PortraitQualityReport(
        AnalysisMode mode,
        boolean evaluated,
        boolean passed,
        boolean technicallyValid,
        int overallScore,
        int promptAlignmentScore,
        int anatomyScore,
        int imageQualityScore,
        boolean retryable,
        String summary,
        List<String> issues,
        String correctionPrompt) {

    public PortraitQualityReport {
        mode = mode == null ? AnalysisMode.RULE_BASED_FALLBACK : mode;
        overallScore = score(overallScore);
        promptAlignmentScore = score(promptAlignmentScore);
        anatomyScore = score(anatomyScore);
        imageQualityScore = score(imageQualityScore);
        summary = text(summary, "未获得人物图片质检结论");
        issues = issues == null ? List.of() : issues.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        correctionPrompt = correctionPrompt == null ? "" : correctionPrompt.trim();
    }

    public static PortraitQualityReport notEvaluated(String reason) {
        return new PortraitQualityReport(
                AnalysisMode.RULE_BASED_FALLBACK,
                false,
                true,
                true,
                0,
                0,
                0,
                0,
                false,
                reason,
                List.of(),
                "");
    }

    private static int score(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
