package dev.learning.fashionagent.ai;

import java.nio.file.Path;

final class RuleBasedFashionVisionService implements FashionVisionService {

    private final String reason;

    RuleBasedFashionVisionService(String reason) {
        this.reason = reason;
    }

    @Override
    public ClothingCatalogAnalysis analyzeCatalogImage(Path clothingImage) {
        throw new IllegalStateException("服装目录资料生成需要多模态 AI：" + reason);
    }

    @Override
    public FashionReferenceSpec analyzeClothing(Path clothingImage) {
        return FashionReferenceSpec.fallback(reason);
    }

    @Override
    public OutfitQualityReport inspectResult(
            Path originalImage,
            Path clothingImage,
            Path resultImage,
            FashionReferenceSpec referenceSpec,
            String appliedPrompt,
            int attemptNumber) {
        return OutfitQualityReport.notEvaluated(reason);
    }

    @Override
    public FashionExperienceDraft extractSuccessfulExperience(
            String userDescription,
            FashionReferenceSpec referenceSpec,
            OutfitQualityReport qualityReport,
            String appliedPrompt) {
        throw new IllegalStateException("成功任务经验提取需要 AI：" + reason);
    }

    @Override
    public boolean aiEnabled() {
        return false;
    }
}
