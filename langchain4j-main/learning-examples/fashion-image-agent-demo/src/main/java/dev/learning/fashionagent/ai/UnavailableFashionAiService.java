package dev.learning.fashionagent.ai;

import java.nio.file.Path;

final class UnavailableFashionAiService implements PortraitAiService, FashionVisionService {

    private final String reason;

    UnavailableFashionAiService(String reason) {
        this.reason = reason;
    }

    @Override
    public PortraitPromptSpec enhancePrompt(String description) {
        throw unavailable("人物提示词扩写");
    }

    @Override
    public PortraitPromptSpec rewritePromptAfterAudit(PortraitPromptSpec rejectedPrompt, int auditRetryNumber) {
        throw unavailable("人物审核提示词重写");
    }

    @Override
    public PortraitQualityReport inspectPortrait(
            Path portraitImage,
            PortraitPromptSpec promptSpec,
            String appliedPrompt,
            int attemptNumber) {
        throw unavailable("人物图片质检");
    }

    @Override
    public ClothingCatalogAnalysis analyzeCatalogImage(Path clothingImage) {
        throw unavailable("服装目录资料生成");
    }

    @Override
    public FashionReferenceSpec analyzeClothing(Path clothingImage) {
        throw unavailable("服装视觉分析");
    }

    @Override
    public OutfitQualityReport inspectResult(
            Path originalImage,
            Path clothingImage,
            Path resultImage,
            FashionReferenceSpec referenceSpec,
            String appliedPrompt,
            int attemptNumber) {
        throw unavailable("换装视觉质检");
    }

    @Override
    public FashionExperienceDraft extractSuccessfulExperience(
            String userDescription,
            FashionReferenceSpec referenceSpec,
            OutfitQualityReport qualityReport,
            String appliedPrompt) {
        throw unavailable("成功任务经验提取");
    }

    @Override
    public boolean aiEnabled() {
        return false;
    }

    private IllegalStateException unavailable(String operation) {
        return new IllegalStateException(
                operation + "需要 LangChain4j AI 服务，任务已停止且不会使用默认提示词：" + reason);
    }
}
