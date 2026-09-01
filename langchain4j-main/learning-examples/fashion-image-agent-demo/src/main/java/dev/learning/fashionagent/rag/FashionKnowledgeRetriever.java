package dev.learning.fashionagent.rag;

import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.learning.LearnedFashionExperience;

public interface FashionKnowledgeRetriever {

    FashionKnowledgeContext retrieve(String userDescription, FashionReferenceSpec referenceSpec);

    default void addExperience(LearnedFashionExperience experience) {
        // Disabled or external retrievers may choose not to support live updates.
    }
}
