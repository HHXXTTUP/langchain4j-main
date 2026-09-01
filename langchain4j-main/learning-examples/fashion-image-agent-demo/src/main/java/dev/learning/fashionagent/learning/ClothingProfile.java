package dev.learning.fashionagent.learning;

import dev.learning.fashionagent.ai.ClothingCatalogAnalysis;
import java.nio.file.Path;
import java.time.Instant;

public record ClothingProfile(
        String id,
        String fileName,
        Path imagePath,
        String sha256,
        ClothingCatalogAnalysis analysis,
        String modelName,
        Instant updatedAt) {

    public String searchText() {
        return analysis.searchText();
    }
}
