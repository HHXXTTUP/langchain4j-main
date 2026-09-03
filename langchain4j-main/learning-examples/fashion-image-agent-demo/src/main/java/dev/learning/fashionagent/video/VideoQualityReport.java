package dev.learning.fashionagent.video;

import java.util.List;

public record VideoQualityReport(
        int overallScore,
        boolean passed,
        double expectedDurationSeconds,
        double actualDurationSeconds,
        int width,
        int height,
        double frameRate,
        boolean audioExpected,
        boolean audioPresent,
        List<String> issues) {

    public VideoQualityReport {
        overallScore = Math.max(0, Math.min(100, overallScore));
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
