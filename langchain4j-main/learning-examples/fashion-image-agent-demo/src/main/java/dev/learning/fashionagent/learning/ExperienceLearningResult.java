package dev.learning.fashionagent.learning;

public record ExperienceLearningResult(
        String status,
        String message,
        LearnedFashionExperience experience) {

    public static ExperienceLearningResult learned(LearnedFashionExperience experience) {
        return new ExperienceLearningResult(
                "LEARNED",
                "换装策略和遗漏修复规则已写入本地 H2 数据库，并加入当前 RAG 索引供后续任务检索",
                experience);
    }

    public static ExperienceLearningResult skipped(String reason) {
        return new ExperienceLearningResult("SKIPPED", reason, null);
    }
}
