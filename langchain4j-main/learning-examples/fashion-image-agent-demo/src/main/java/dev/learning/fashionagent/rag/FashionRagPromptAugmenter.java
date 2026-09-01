package dev.learning.fashionagent.rag;

import dev.learning.fashionagent.ai.FashionReferenceSpec;

public final class FashionRagPromptAugmenter {

    private FashionRagPromptAugmenter() {}

    public static String augment(FashionReferenceSpec spec, FashionKnowledgeContext context) {
        if (context == null || !context.hasEvidence()) {
            return spec.replacementPrompt();
        }
        return """
                %s

                %s

                对图2穿搭的视觉分析补充：
                %s
                """.formatted(
                FashionReferenceSpec.fixedReplacementPrefix(),
                context.promptContext(),
                spec.analysisSupplement()).trim();
    }
}
