package dev.learning.fashionagent.ai;

final class PortraitQualityGate {

    private final FashionAiProperties properties;

    PortraitQualityGate(FashionAiProperties properties) {
        this.properties = properties;
    }

    boolean passes(
            boolean technicallyValid,
            int overallScore,
            int promptAlignmentScore,
            int anatomyScore,
            int imageQualityScore) {
        return technicallyValid
                && overallScore >= properties.getPortraitQualityPassScore()
                && promptAlignmentScore >= properties.getPortraitPromptAlignmentPassScore()
                && anatomyScore >= properties.getPortraitAnatomyPassScore()
                && imageQualityScore >= properties.getPortraitImageQualityPassScore();
    }
}
