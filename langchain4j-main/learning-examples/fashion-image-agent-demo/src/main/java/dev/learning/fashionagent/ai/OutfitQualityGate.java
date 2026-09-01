package dev.learning.fashionagent.ai;

final class OutfitQualityGate {

    private final FashionAiProperties properties;

    OutfitQualityGate(FashionAiProperties properties) {
        this.properties = properties;
    }

    boolean passes(
            int overallScore,
            int clothingMatchScore,
            int headAccessoryMatchScore,
            int identityPreservationScore,
            boolean hasHeadAccessories) {
        return overallScore >= properties.getQualityPassScore()
                && clothingMatchScore >= properties.getClothingMatchPassScore()
                && identityPreservationScore >= properties.getIdentityPassScore()
                && (!hasHeadAccessories
                        || headAccessoryMatchScore >= properties.getHeadAccessoryPassScore());
    }
}
