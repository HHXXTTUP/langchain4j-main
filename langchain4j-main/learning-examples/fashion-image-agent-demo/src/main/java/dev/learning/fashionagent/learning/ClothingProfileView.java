package dev.learning.fashionagent.learning;

import dev.learning.fashionagent.ai.ClothingCatalogAnalysis;
import java.time.Instant;

public record ClothingProfileView(
        String id,
        String fileName,
        String imageUrl,
        ClothingCatalogAnalysis analysis,
        String modelName,
        Instant updatedAt) {

    static ClothingProfileView from(ClothingProfile profile) {
        return new ClothingProfileView(
                profile.id(),
                profile.fileName(),
                "/api/clothing-catalog/" + profile.id() + "/image",
                profile.analysis(),
                profile.modelName(),
                profile.updatedAt());
    }
}
