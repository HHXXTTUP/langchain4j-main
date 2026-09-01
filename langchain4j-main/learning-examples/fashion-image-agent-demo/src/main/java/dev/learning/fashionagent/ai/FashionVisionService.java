package dev.learning.fashionagent.ai;

import java.nio.file.Path;

public interface FashionVisionService {

    ClothingCatalogAnalysis analyzeCatalogImage(Path clothingImage);

    FashionReferenceSpec analyzeClothing(Path clothingImage);

    OutfitQualityReport inspectResult(
            Path originalImage,
            Path clothingImage,
            Path resultImage,
            FashionReferenceSpec referenceSpec,
            String appliedPrompt,
            int attemptNumber);

    FashionExperienceDraft extractSuccessfulExperience(
            String userDescription,
            FashionReferenceSpec referenceSpec,
            OutfitQualityReport qualityReport,
            String appliedPrompt);

    boolean aiEnabled();
}
