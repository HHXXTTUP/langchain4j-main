package dev.learning.fashionagent.pipeline;

import dev.learning.fashionagent.agent.Agent1BeautyCreator;
import dev.learning.fashionagent.agent.Agent2ClothingPicker;
import dev.learning.fashionagent.agent.Agent3OutfitStylist;
import dev.learning.fashionagent.agent.Agent4ResultPresenter;
import dev.learning.fashionagent.agent.FashionReferenceAnalyzer;
import dev.learning.fashionagent.agent.OutfitQualityInspector;
import dev.learning.fashionagent.agent.PortraitPromptEnhancer;
import dev.learning.fashionagent.agent.PortraitQualityInspector;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.ai.PortraitQualityReport;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.RunningHubContentAuditException;
import dev.learning.fashionagent.rag.FashionKnowledgeContext;
import dev.learning.fashionagent.rag.FashionKnowledgeRetriever;
import dev.learning.fashionagent.rag.FashionRagPromptAugmenter;
import dev.learning.fashionagent.service.ClothingCatalog;
import dev.learning.fashionagent.service.ClothingReplacementService;
import dev.learning.fashionagent.service.ImageTransferService;
import dev.learning.fashionagent.service.JobArtifactService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FashionAgentPipeline {

    private static final int MAX_REPLACEMENT_PROMPT_LENGTH = 3500;
    private static final int MAX_PORTRAIT_PROMPT_LENGTH = 3500;
    private static final int MAX_BASE_PROMPT_IN_CORRECTION = 2200;
    private static final int MAX_CORRECTION_LENGTH = 700;

    private final Agent1BeautyCreator agent1;
    private final Agent2ClothingPicker agent2;
    private final Agent3OutfitStylist agent3;
    private final Agent4ResultPresenter agent4;
    private final PortraitPromptEnhancer portraitPromptEnhancer;
    private final PortraitQualityInspector portraitQualityInspector;
    private final FashionReferenceAnalyzer referenceAnalyzer;
    private final OutfitQualityInspector qualityInspector;
    private final ClothingCatalog clothingCatalog;
    private final RunningHubProperties properties;
    private final FashionAiProperties aiProperties;
    private final ImageTransferService imageTransferService;
    private final JobArtifactService artifactService;
    private final FashionKnowledgeRetriever knowledgeRetriever;

    public FashionAgentPipeline(
            Agent1BeautyCreator agent1,
            Agent2ClothingPicker agent2,
            Agent3OutfitStylist agent3,
            Agent4ResultPresenter agent4,
            PortraitPromptEnhancer portraitPromptEnhancer,
            PortraitQualityInspector portraitQualityInspector,
            FashionReferenceAnalyzer referenceAnalyzer,
            OutfitQualityInspector qualityInspector,
            ClothingCatalog clothingCatalog,
            RunningHubProperties properties,
            FashionAiProperties aiProperties,
            ImageTransferService imageTransferService,
            JobArtifactService artifactService,
            FashionKnowledgeRetriever knowledgeRetriever) {
        this.agent1 = agent1;
        this.agent2 = agent2;
        this.agent3 = agent3;
        this.agent4 = agent4;
        this.portraitPromptEnhancer = portraitPromptEnhancer;
        this.portraitQualityInspector = portraitQualityInspector;
        this.referenceAnalyzer = referenceAnalyzer;
        this.qualityInspector = qualityInspector;
        this.clothingCatalog = clothingCatalog;
        this.properties = properties;
        this.aiProperties = aiProperties;
        this.imageTransferService = imageTransferService;
        this.artifactService = artifactService;
        this.knowledgeRetriever = knowledgeRetriever;
    }

    public PipelineResult run(UUID jobId, String prompt, PipelineObserver observer) {
        return run(jobId, prompt, PortraitGenerationMode.STANDARD, observer);
    }

    public PipelineResult run(
            UUID jobId,
            String prompt,
            PortraitGenerationMode portraitGenerationMode,
            PipelineObserver observer) {
        properties.requiredApiKey();
        clothingCatalog.requireImages();
        PortraitGeneration portrait = createPortrait(
                jobId,
                prompt,
                PortraitGenerationMode.defaultIfNull(portraitGenerationMode),
                observer);
        Path originalImage = portrait.image();
        Agent2ClothingPicker.ImagePair images = agent2.prepare(
                jobId, originalImage, portrait.promptSpec().generationPrompt(), observer);
        FashionReferenceSpec fashionAnalysis = referenceAnalyzer.analyze(images.clothingImage(), observer);
        artifactService.writeJson(jobId, "fashion-analysis.json", fashionAnalysis);

        observer.stage(
                PipelineStage.RAG_RETRIEVING_FASHION_KNOWLEDGE,
                "正在根据用户描述和服装视觉分析检索换装经验库");
        FashionKnowledgeContext fashionKnowledge = knowledgeRetriever.retrieve(prompt, fashionAnalysis);
        artifactService.writeText(jobId, "fashion-rag-query.txt", fashionKnowledge.query());
        artifactService.writeJson(jobId, "fashion-rag-context.json", fashionKnowledge);
        observer.fashionKnowledge(fashionKnowledge);

        ClothingReplacementService.UploadedImages uploadedImages = agent3.upload(images, observer);
        int maxAttempts = Math.max(1, aiProperties.getMaxOutfitAttempts());
        List<OutfitAttempt> attempts = new ArrayList<>();
        String currentPrompt = limitPrompt(FashionRagPromptAugmenter.augment(fashionAnalysis, fashionKnowledge));

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            artifactService.writeText(jobId, "replacement-prompt-" + attemptNumber + ".txt", currentPrompt);
            Path attemptImage = agent3.replaceAttempt(
                    jobId, uploadedImages, currentPrompt, attemptNumber, observer);
            OutfitQualityReport qualityReport = qualityInspector.inspect(
                    originalImage,
                    images.clothingImage(),
                    attemptImage,
                    fashionAnalysis,
                    currentPrompt,
                    attemptNumber,
                    observer);
            OutfitAttempt attempt = new OutfitAttempt(
                    attemptNumber, attemptImage, currentPrompt, qualityReport, false);
            attempts.add(attempt);
            observer.outfitAttempt(attempt);
            artifactService.writeJson(jobId, "quality-report-" + attemptNumber + ".json", qualityReport);

            if (!shouldRetry(qualityReport, attemptNumber, maxAttempts)) {
                if (qualityReport.evaluated()
                        && !qualityReport.passed()
                        && qualityReport.overallScore() >= aiProperties.getOutfitAcceptAndLearnScore()) {
                    observer.stage(
                            PipelineStage.AI_VERIFYING_OUTFIT,
                            "换装综合评分 " + qualityReport.overallScore()
                                    + " 分，已达到 " + aiProperties.getOutfitAcceptAndLearnScore()
                                    + " 分停止重试标准；不再生成第二次，遗漏项将在任务完成后写入知识库："
                                    + qualityReport.missingElements());
                }
                break;
            }
            observer.stage(
                    PipelineStage.AI_REFINING_PROMPT,
                    "AI 质检未通过，正在根据遗漏项生成第 " + (attemptNumber + 1) + " 次纠正提示词");
            currentPrompt = correctedPrompt(fashionAnalysis, fashionKnowledge, qualityReport);
        }

        int selectedIndex = selectBestAttemptIndex(attempts);
        OutfitAttempt selectedAttempt = attempts.get(selectedIndex).withSelected(true);
        attempts.set(selectedIndex, selectedAttempt);
        observer.outfitAttempt(selectedAttempt);
        Path finalImage = imageTransferService.archiveLocal(selectedAttempt.image(), jobId, "outfit");
        observer.finalImage(finalImage);
        Agent4ResultPresenter.PresentedResult presented = agent4.present(
                finalImage,
                images.clothingImage(),
                selectedAttempt.qualityReport(),
                attempts.size(),
                observer);
        return new PipelineResult(
                originalImage,
                images.clothingImage(),
                presented.image(),
                portrait.promptSpec(),
                portrait.attempts(),
                portrait.qualityReport(),
                fashionAnalysis,
                List.copyOf(attempts),
                selectedAttempt.qualityReport(),
                presented.reply());
    }

    private PortraitGeneration createPortrait(
            UUID jobId,
            String description,
            PortraitGenerationMode portraitGenerationMode,
            PipelineObserver observer) {
        PortraitPromptSpec promptSpec = portraitPromptEnhancer.enhance(description, observer);
        artifactService.writeJson(jobId, "portrait-prompt-spec.json", promptSpec);
        int maxAttempts = Math.max(1, aiProperties.getMaxPortraitAttempts());
        int maxAuditRetries = Math.max(0, aiProperties.getPortraitAuditMaxRetries());
        List<PortraitAttempt> attempts = new ArrayList<>();
        String currentPrompt = truncate(promptSpec.generationPrompt(), MAX_PORTRAIT_PROMPT_LENGTH);
        int attemptNumber = 1;
        int submissionNumber = 1;
        int auditRetryNumber = 0;

        while (attemptNumber <= maxAttempts) {
            artifactService.writeText(jobId, "portrait-generation-prompt-" + attemptNumber + ".txt", currentPrompt);
            artifactService.writeText(
                    jobId,
                    "portrait-generation-submission-" + submissionNumber + ".txt",
                    currentPrompt);
            Path attemptImage;
            try {
                attemptImage = portraitGenerationMode == PortraitGenerationMode.ENHANCED
                        ? agent1.generateAttempt(
                                jobId,
                                currentPrompt,
                                attemptNumber,
                                portraitGenerationMode,
                                observer)
                        : agent1.generateAttempt(jobId, currentPrompt, attemptNumber, observer);
            } catch (RunningHubContentAuditException auditException) {
                if (auditRetryNumber >= maxAuditRetries) {
                    throw new IllegalStateException(
                            "RunningHub 人物内容审核连续未通过，安全提示词重写已达到上限 "
                                    + maxAuditRetries + " 次",
                            auditException);
                }
                auditRetryNumber++;
                artifactService.writeText(
                        jobId,
                        "portrait-audit-failure-" + auditRetryNumber + ".txt",
                        auditException.getMessage());
                observer.stage(
                        PipelineStage.AI_REFINING_PORTRAIT_PROMPT,
                        "第 " + submissionNumber + " 次人物生成被 RunningHub 内容审核拒绝，"
                                + "即将执行第 " + auditRetryNumber + " 次提示词安全重写");
                promptSpec = portraitPromptEnhancer.rewriteAfterAudit(
                        promptSpec,
                        auditRetryNumber,
                        observer);
                artifactService.writeJson(
                        jobId,
                        "portrait-prompt-spec-audit-rewrite-" + auditRetryNumber + ".json",
                        promptSpec);
                currentPrompt = truncate(promptSpec.generationPrompt(), MAX_PORTRAIT_PROMPT_LENGTH);
                submissionNumber++;
                continue;
            }
            PortraitQualityReport qualityReport = portraitQualityInspector.inspect(
                    attemptImage,
                    promptSpec,
                    currentPrompt,
                    attemptNumber,
                    observer);
            PortraitAttempt attempt = new PortraitAttempt(
                    attemptNumber,
                    attemptImage,
                    currentPrompt,
                    qualityReport,
                    false);
            attempts.add(attempt);
            observer.portraitAttempt(attempt);
            artifactService.writeJson(
                    jobId,
                    "portrait-quality-report-" + attemptNumber + ".json",
                    qualityReport);

            if (!shouldRetryPortrait(qualityReport, attemptNumber, maxAttempts)) {
                break;
            }
            observer.stage(
                    PipelineStage.AI_REFINING_PORTRAIT_PROMPT,
                    "人物质检未通过，正在生成第 " + (attemptNumber + 1) + " 张人物底图的纠正提示词");
            currentPrompt = correctedPortraitPrompt(promptSpec, qualityReport);
            attemptNumber++;
            submissionNumber++;
        }

        int selectedIndex = selectPortraitAttemptIndex(attempts);
        PortraitAttempt selected = attempts.get(selectedIndex).withSelected(true);
        attempts.set(selectedIndex, selected);
        observer.portraitAttempt(selected);
        Path originalImage = imageTransferService.archiveLocal(selected.image(), jobId, "original");
        observer.originalImage(originalImage);
        observer.stage(PipelineStage.AI_VERIFYING_PORTRAIT, "人物底图已通过检查并归档，准备选择服装");
        return new PortraitGeneration(
                originalImage,
                promptSpec,
                List.copyOf(attempts),
                selected.qualityReport());
    }

    private static boolean shouldRetryPortrait(
            PortraitQualityReport report,
            int attemptNumber,
            int maxAttempts) {
        return report.evaluated()
                && !report.passed()
                && report.retryable()
                && attemptNumber < maxAttempts;
    }

    private static int selectPortraitAttemptIndex(List<PortraitAttempt> attempts) {
        for (int index = 0; index < attempts.size(); index++) {
            PortraitQualityReport report = attempts.get(index).qualityReport();
            if (report.evaluated() && report.passed()) {
                return index;
            }
        }
        PortraitQualityReport finalReport = attempts.get(attempts.size() - 1).qualityReport();
        if (!finalReport.evaluated()) {
            return attempts.size() - 1;
        }
        throw new IllegalStateException(
                "人物底图未通过 AI 质量检查，已停止进入换装流程：" + finalReport.summary()
                        + "；问题：" + finalReport.issues());
    }

    private static String correctedPortraitPrompt(
            PortraitPromptSpec spec,
            PortraitQualityReport report) {
        String correction = report.correctionPrompt().isBlank()
                ? "重点修复这些问题：" + report.issues()
                : report.correctionPrompt();
        return truncate("""
                本轮最高优先级纠正要求：%s
                上一轮人物图片问题：%s

                原始人物生成要求：
                %s

                已经正确的人物特征、环境、光线和构图必须保持不变。人物必须是明确年满20岁的成年女性，并保持全身完整、四肢清晰，适合后续服装替换。
                """.formatted(
                truncate(correction, 700),
                truncate(String.join("、", report.issues()), 500),
                truncate(spec.generationPrompt(), 2100)),
                MAX_PORTRAIT_PROMPT_LENGTH);
    }

    private boolean shouldRetry(OutfitQualityReport report, int attemptNumber, int maxAttempts) {
        return report.evaluated()
                && !report.passed()
                && report.overallScore() < aiProperties.getOutfitAcceptAndLearnScore()
                && report.retryable()
                && attemptNumber < maxAttempts;
    }

    private static String correctedPrompt(
            FashionReferenceSpec spec,
            FashionKnowledgeContext knowledge,
            OutfitQualityReport report) {
        String correction = report.correctionPrompt().isBlank()
                ? "重点补齐上一轮遗漏的元素：" + report.missingElements()
                : report.correctionPrompt();
        return limitPrompt("""
                %s

                本轮最高优先级纠正要求：%s
                上一轮遗漏元素：%s
                上一轮视觉质检结论：%s

                本轮仍需遵守的检索知识：
                %s

                对图2穿搭的视觉分析补充：
                %s

                已经正确的服装区域、人物面部和身份必须保持不变。人物姿势、肢体动作、构图、环境和背景只能以图1为准，
                严禁采用或模仿图2人物的姿势、动作、构图、环境或背景。
                """.formatted(
                FashionReferenceSpec.fixedReplacementPrefix(),
                truncate(correction, MAX_CORRECTION_LENGTH),
                truncate(String.join("、", report.missingElements()), 400),
                truncate(report.summary(), 300),
                truncate(knowledge == null ? "" : knowledge.promptContext(), 900),
                truncate(spec.analysisSupplement(), MAX_BASE_PROMPT_IN_CORRECTION)));
    }

    private static String limitPrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        return truncate(normalized, MAX_REPLACEMENT_PROMPT_LENGTH);
    }

    private static String truncate(String text, int maxLength) {
        String normalized = text == null ? "" : text.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static int selectBestAttemptIndex(List<OutfitAttempt> attempts) {
        return attempts.stream()
                .max(Comparator
                        .comparingInt(FashionAgentPipeline::selectionTier)
                        .thenComparingInt(FashionAgentPipeline::lowestCriticalScore)
                        .thenComparingInt(attempt -> attempt.qualityReport().overallScore())
                        .thenComparingInt(OutfitAttempt::attemptNumber))
                .map(attempts::indexOf)
                .orElseThrow(() -> new IllegalStateException("换装流程没有产生任何候选图片"));
    }

    private static int selectionTier(OutfitAttempt attempt) {
        OutfitQualityReport report = attempt.qualityReport();
        if (!report.evaluated()) {
            return 0;
        }
        return report.passed() ? 2 : 1;
    }

    private static int lowestCriticalScore(OutfitAttempt attempt) {
        OutfitQualityReport report = attempt.qualityReport();
        if (!report.evaluated()) {
            return 0;
        }
        return Math.min(
                Math.min(report.overallScore(), report.clothingMatchScore()),
                Math.min(report.headAccessoryMatchScore(), report.identityPreservationScore()));
    }

    private record PortraitGeneration(
            Path image,
            PortraitPromptSpec promptSpec,
            List<PortraitAttempt> attempts,
            PortraitQualityReport qualityReport) {}
}
