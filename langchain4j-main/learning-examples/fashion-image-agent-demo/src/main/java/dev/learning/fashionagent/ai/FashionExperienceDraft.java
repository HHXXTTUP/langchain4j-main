package dev.learning.fashionagent.ai;

import java.util.List;

public record FashionExperienceDraft(
        String title,
        String scenario,
        String successfulStrategy,
        List<String> reusableRules,
        List<String> risks,
        List<String> keywords) {

    public FashionExperienceDraft {
        title = text(title, "换装复用经验");
        scenario = text(scenario, "通用服装替换场景");
        successfulStrategy = text(successfulStrategy, "沿用本次已验证有效的换装部分");
        reusableRules = values(reusableRules);
        risks = values(risks);
        keywords = values(keywords);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static List<String> values(List<String> source) {
        return source == null ? List.of() : source.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
