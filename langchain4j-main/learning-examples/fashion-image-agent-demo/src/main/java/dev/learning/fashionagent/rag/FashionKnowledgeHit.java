package dev.learning.fashionagent.rag;

public record FashionKnowledgeHit(
        String source,
        String title,
        int chunkIndex,
        double score,
        String text) {}
