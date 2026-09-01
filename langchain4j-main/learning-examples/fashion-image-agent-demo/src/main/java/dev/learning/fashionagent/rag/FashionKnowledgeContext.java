package dev.learning.fashionagent.rag;

import java.util.List;

public record FashionKnowledgeContext(
        boolean enabled,
        String query,
        List<FashionKnowledgeHit> hits,
        String promptContext,
        String message) {

    public FashionKnowledgeContext {
        query = query == null ? "" : query.trim();
        hits = hits == null ? List.of() : List.copyOf(hits);
        promptContext = promptContext == null ? "" : promptContext.trim();
        message = message == null ? "" : message.trim();
    }

    public static FashionKnowledgeContext disabled(String message) {
        return new FashionKnowledgeContext(false, "", List.of(), "", message);
    }

    public boolean hasEvidence() {
        return !hits.isEmpty() && !promptContext.isBlank();
    }
}
