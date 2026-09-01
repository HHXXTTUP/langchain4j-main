package dev.learning.fashionagent.learning;

import dev.learning.fashionagent.ai.FashionExperienceDraft;
import java.time.Instant;
import java.util.UUID;

public record LearnedFashionExperience(
        String id,
        UUID sourceJobId,
        FashionExperienceDraft content,
        int qualityScore,
        boolean approved,
        Instant createdAt) {

    public String knowledgeText() {
        return """
                # %s
                适用场景：%s
                成功策略：%s
                可复用规则：%s
                注意风险：%s
                检索关键词：%s
                证据质量分：%d
                """.formatted(
                content.title(),
                content.scenario(),
                content.successfulStrategy(),
                String.join("；", content.reusableRules()),
                String.join("；", content.risks()),
                String.join("、", content.keywords()),
                qualityScore).trim();
    }
}
