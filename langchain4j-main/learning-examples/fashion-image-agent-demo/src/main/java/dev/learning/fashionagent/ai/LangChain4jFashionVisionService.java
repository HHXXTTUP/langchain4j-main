package dev.learning.fashionagent.ai;

import dev.langchain4j.data.message.ImageContent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LangChain4jFashionVisionService implements FashionVisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LangChain4jFashionVisionService.class);

    private final ModelFashionVisionAgent agent;
    private final VisionImageEncoder imageEncoder;
    private final FashionAiProperties properties;
    private final FashionAiCallExecutor callExecutor;
    private final OutfitQualityGate qualityGate;

    LangChain4jFashionVisionService(
            ModelFashionVisionAgent agent,
            VisionImageEncoder imageEncoder,
            FashionAiProperties properties) {
        this.agent = agent;
        this.imageEncoder = imageEncoder;
        this.properties = properties;
        this.callExecutor = new FashionAiCallExecutor(properties);
        this.qualityGate = new OutfitQualityGate(properties);
    }

    @Override
    public ClothingCatalogAnalysis analyzeCatalogImage(Path clothingImage) {
        long startedAt = System.nanoTime();
        LOGGER.info("LangChain4j 服装目录资料分析开始 image={}", clothingImage);
        try {
            ModelFashionVisionAgent.ModelClothingCatalogAnalysis analysis = callExecutor.execute(
                    "服装目录资料分析",
                    () -> agent.analyzeCatalog(
                            "请生成用于人物描述与服装之间语义检索的完整目录资料。",
                            imageEncoder.encode(clothingImage)));
            ClothingCatalogAnalysis result = new ClothingCatalogAnalysis(
                    analysis.name(),
                    analysis.summary(),
                    analysis.styles(),
                    analysis.occasions(),
                    analysis.seasons(),
                    analysis.colors(),
                    analysis.materials(),
                    analysis.garments(),
                    analysis.headAccessories(),
                    analysis.bodyAccessories(),
                    analysis.silhouette(),
                    analysis.suitableBodyCharacteristics(),
                    analysis.keywords());
            LOGGER.info("LangChain4j 服装目录资料分析完成 image={} elapsedMs={} name={}",
                    clothingImage, elapsedMillis(startedAt), result.name());
            return result;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("LangChain4j 服装目录资料分析失败 image={}", clothingImage, exception);
            throw failFast("服装目录资料分析", exception);
        }
    }

    @Override
    public FashionReferenceSpec analyzeClothing(Path clothingImage) {
        long startedAt = System.nanoTime();
        LOGGER.info("LangChain4j 服装视觉分析开始 image={}", clothingImage);
        try {
            ImageContent image = imageEncoder.encode(clothingImage);
            ModelFashionVisionAgent.ModelFashionAnalysis analysis = callExecutor.execute(
                    "服装视觉分析",
                    () -> agent.analyze(
                            "请完整识别参考图2的穿搭。replacementPrompt 只补充图2从头到脚的具体造型细节，"
                                    + "重点检查发型、头发颜色、发饰、头饰、项链、手套和腿部装饰，不要重复通用换装要求。",
                            image));
            requireText(analysis.replacementPrompt(), "LangChain4j 未返回换装提示词");
            FashionReferenceSpec result = new FashionReferenceSpec(
                    AnalysisMode.MULTIMODAL_AI,
                    analysis.summary(),
                    analysis.garments(),
                    analysis.colors(),
                    analysis.materials(),
                    analysis.headAccessories(),
                    analysis.bodyAccessories(),
                    analysis.mustTransfer(),
                    analysis.replacementPrompt());
            LOGGER.info("LangChain4j 服装视觉分析完成 elapsedMs={} headAccessories={} mustTransfer={}",
                    elapsedMillis(startedAt), result.headAccessories(), result.mustTransfer());
            return result;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("LangChain4j 服装视觉分析失败，任务立即停止，不会调用换装接口", exception);
            throw failFast("服装视觉分析", exception);
        }
    }

    @Override
    public OutfitQualityReport inspectResult(
            Path originalImage,
            Path clothingImage,
            Path resultImage,
            FashionReferenceSpec referenceSpec,
            String appliedPrompt,
            int attemptNumber) {
        long startedAt = System.nanoTime();
        LOGGER.info("LangChain4j 换装视觉质检开始 attempt={} result={}", attemptNumber, resultImage);
        try {
            List<ImageContent> images = List.of(
                    imageEncoder.encode(originalImage),
                    imageEncoder.encode(clothingImage),
                    imageEncoder.encode(resultImage));
            String instruction = """
                    本次是第 %d 次换装。服装分析摘要：%s
                    质检职责必须严格分开：图2与图3只比较穿搭细节；图1与图3只比较人物身份、姿势、动作、构图、环境和背景。
                    图1是姿势、动作、构图、环境和背景的唯一基准，禁止检查图3是否复刻图2人物姿势或背景。
                    必须迁移元素：%s
                    已识别头部配饰：%s
                    本次使用提示词：%s
                    """.formatted(
                    attemptNumber,
                    referenceSpec.summary(),
                    referenceSpec.mustTransfer(),
                    referenceSpec.headAccessories(),
                    appliedPrompt);
            ModelFashionVisionAgent.ModelQualityAnalysis assessment = callExecutor.execute(
                    "换装视觉质检",
                    () -> agent.inspect(instruction, images));
            boolean hasHeadAccessories = !referenceSpec.headAccessories().isEmpty();
            boolean passed = qualityGate.passes(
                    assessment.overallScore(),
                    assessment.clothingMatchScore(),
                    assessment.headAccessoryMatchScore(),
                    assessment.identityPreservationScore(),
                    hasHeadAccessories);
            OutfitQualityReport result = new OutfitQualityReport(
                    AnalysisMode.MULTIMODAL_AI,
                    true,
                    passed,
                    assessment.overallScore(),
                    assessment.clothingMatchScore(),
                    assessment.headAccessoryMatchScore(),
                    assessment.identityPreservationScore(),
                    !passed && assessment.retryable(),
                    assessment.summary(),
                    assessment.differences(),
                    assessment.missingElements(),
                    assessment.correctionPrompt());
            LOGGER.info("LangChain4j 换装视觉质检完成 attempt={} elapsedMs={} passed={} scores=[overall:{}, clothing:{}, head:{}, identity:{}] missing={}",
                    attemptNumber,
                    elapsedMillis(startedAt),
                    result.passed(),
                    result.overallScore(),
                    result.clothingMatchScore(),
                    result.headAccessoryMatchScore(),
                    result.identityPreservationScore(),
                    result.missingElements());
            return result;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String reason = "LangChain4j 换装视觉质检调用失败，已跳过质检并继续流程："
                    + failureDetail(exception);
            LOGGER.error(reason, exception);
            return OutfitQualityReport.notEvaluated(reason);
        }
    }

    @Override
    public FashionExperienceDraft extractSuccessfulExperience(
            String userDescription,
            FashionReferenceSpec referenceSpec,
            OutfitQualityReport qualityReport,
            String appliedPrompt) {
        long startedAt = System.nanoTime();
        LOGGER.info("LangChain4j 换装经验提取开始 qualityScore={} missing={}",
                qualityReport.overallScore(), qualityReport.missingElements());
        String evidence = """
                用户人物描述：%s
                服装摘要：%s
                服装单品：%s
                颜色：%s
                材质：%s
                头部配饰：%s
                身体配饰：%s
                最终换装提示词：%s
                视觉质检综合分：%d
                服装匹配分：%d
                头饰匹配分：%d
                身份保持分：%d
                质检结论：%s
                结果差异：%s
                遗漏元素：%s
                学习要求：将遗漏元素转换成下次任务执行前必须注入提示词、并在质检时逐项确认的规则。
                """.formatted(
                userDescription,
                referenceSpec.summary(),
                referenceSpec.garments(),
                referenceSpec.colors(),
                referenceSpec.materials(),
                referenceSpec.headAccessories(),
                referenceSpec.bodyAccessories(),
                appliedPrompt,
                qualityReport.overallScore(),
                qualityReport.clothingMatchScore(),
                qualityReport.headAccessoryMatchScore(),
                qualityReport.identityPreservationScore(),
                qualityReport.summary(),
                qualityReport.differences(),
                qualityReport.missingElements());
        try {
            ModelFashionVisionAgent.ModelExperienceAnalysis analysis = callExecutor.execute(
                    "换装经验与遗漏规则提取",
                    () -> agent.extractExperience(evidence));
            FashionExperienceDraft result = new FashionExperienceDraft(
                    analysis.title(),
                    analysis.scenario(),
                    analysis.successfulStrategy(),
                    analysis.reusableRules(),
                    analysis.risks(),
                    analysis.keywords());
            LOGGER.info("LangChain4j 换装经验提取完成 elapsedMs={} title={}",
                    elapsedMillis(startedAt), result.title());
            return result;
        } catch (CancellationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.error("LangChain4j 换装经验提取失败", exception);
            throw failFast("换装经验提取", exception);
        }
    }

    @Override
    public boolean aiEnabled() {
        return true;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private static IllegalStateException failFast(String operation, RuntimeException cause) {
        String detail = failureDetail(cause);
        return new IllegalStateException(
                "LangChain4j " + operation + "失败，任务已停止且不会降级执行：" + detail,
                cause);
    }

    private static String failureDetail(RuntimeException cause) {
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }

}
