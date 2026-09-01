package dev.learning.fashionagent.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.agent.Agent1BeautyCreator;
import dev.learning.fashionagent.agent.Agent2ClothingPicker;
import dev.learning.fashionagent.agent.Agent3OutfitStylist;
import dev.learning.fashionagent.agent.Agent4ResultPresenter;
import dev.learning.fashionagent.agent.FashionReferenceAnalyzer;
import dev.learning.fashionagent.agent.OutfitQualityInspector;
import dev.learning.fashionagent.agent.PortraitPromptEnhancer;
import dev.learning.fashionagent.agent.PortraitQualityInspector;
import dev.learning.fashionagent.ai.AnalysisMode;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.ai.PortraitQualityReport;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.RunningHubContentAuditException;
import dev.learning.fashionagent.rag.FashionKnowledgeContext;
import dev.learning.fashionagent.rag.FashionKnowledgeHit;
import dev.learning.fashionagent.rag.FashionKnowledgeRetriever;
import dev.learning.fashionagent.service.ClothingCatalog;
import dev.learning.fashionagent.service.ClothingReplacementService;
import dev.learning.fashionagent.service.ImageTransferService;
import dev.learning.fashionagent.service.JobArtifactService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FashionAgentPipelineTest {

    @Test
    void shouldUseEnhancedPortraitWorkflowAndKeepTheRemainingPipelineUnchanged() {
        Fixture fixture = new Fixture();
        when(fixture.creator.generateAttempt(
                        fixture.jobId,
                        fixture.portraitPrompt.generationPrompt(),
                        1,
                        PortraitGenerationMode.ENHANCED,
                        fixture.observer))
                .thenReturn(fixture.portraitAttempt1);

        PipelineResult result = fixture.pipeline.run(
                fixture.jobId,
                "测试人物",
                PortraitGenerationMode.ENHANCED,
                fixture.observer);

        assertEquals(fixture.finalImage, result.finalImage());
        verify(fixture.creator).generateAttempt(
                fixture.jobId,
                fixture.portraitPrompt.generationPrompt(),
                1,
                PortraitGenerationMode.ENHANCED,
                fixture.observer);
        verify(fixture.picker).prepare(
                fixture.jobId,
                fixture.original,
                fixture.portraitPrompt.generationPrompt(),
                fixture.observer);
        verify(fixture.stylist).replaceAttempt(
                fixture.jobId,
                fixture.uploaded,
                referenceSpec().replacementPrompt(),
                1,
                fixture.observer);
    }

    @Test
    void shouldNotCallPortraitGenerationWhenPromptEnhancementFails() {
        Fixture fixture = new Fixture();
        when(fixture.portraitEnhancer.enhance("测试人物", fixture.observer))
                .thenThrow(new IllegalStateException("LangChain4j 提示词扩写失败"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer));

        assertTrue(exception.getMessage().contains("提示词扩写失败"));
        verifyNoInteractions(fixture.creator, fixture.picker, fixture.stylist);
    }

    @Test
    void shouldRewritePromptAndRetryWhenRunningHubRejectsPortraitContent() {
        Fixture fixture = new Fixture();
        PortraitPromptSpec safePrompt = safePortraitPrompt();
        when(fixture.creator.generateAttempt(
                        fixture.jobId,
                        fixture.portraitPrompt.generationPrompt(),
                        1,
                        fixture.observer))
                .thenThrow(new RunningHubContentAuditException("RunningHub 内容审核未通过", "805"));
        when(fixture.portraitEnhancer.rewriteAfterAudit(
                        fixture.portraitPrompt,
                        1,
                        fixture.observer))
                .thenReturn(safePrompt);
        when(fixture.creator.generateAttempt(
                        fixture.jobId,
                        safePrompt.generationPrompt(),
                        1,
                        fixture.observer))
                .thenReturn(fixture.portraitAttempt1);
        PortraitQualityReport passedReport = portraitReport(true, false, 90, List.of(), "");
        when(fixture.portraitInspector.inspect(
                        fixture.portraitAttempt1,
                        safePrompt,
                        safePrompt.generationPrompt(),
                        1,
                        fixture.observer))
                .thenReturn(passedReport);

        PipelineResult result = fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        assertEquals(safePrompt, result.portraitPrompt());
        assertEquals(1, result.portraitAttempts().size());
        verify(fixture.portraitEnhancer).rewriteAfterAudit(
                fixture.portraitPrompt,
                1,
                fixture.observer);
        verify(fixture.creator, times(2)).generateAttempt(
                eq(fixture.jobId), anyString(), eq(1), eq(fixture.observer));
        verify(fixture.picker).prepare(
                fixture.jobId, fixture.original, safePrompt.generationPrompt(), fixture.observer);
    }

    @Test
    void shouldStopAfterConfiguredNumberOfAuditPromptRewrites() {
        Fixture fixture = new Fixture();
        PortraitPromptSpec firstSafePrompt = safePortraitPrompt("第一版安全提示词");
        PortraitPromptSpec secondSafePrompt = safePortraitPrompt("第二版安全提示词");
        RunningHubContentAuditException auditFailure =
                new RunningHubContentAuditException("RunningHub 内容审核未通过", "805");
        when(fixture.creator.generateAttempt(
                        fixture.jobId,
                        fixture.portraitPrompt.generationPrompt(),
                        1,
                        fixture.observer))
                .thenThrow(auditFailure);
        when(fixture.portraitEnhancer.rewriteAfterAudit(
                        fixture.portraitPrompt,
                        1,
                        fixture.observer))
                .thenReturn(firstSafePrompt);
        when(fixture.creator.generateAttempt(
                        fixture.jobId,
                        firstSafePrompt.generationPrompt(),
                        1,
                        fixture.observer))
                .thenThrow(auditFailure);
        when(fixture.portraitEnhancer.rewriteAfterAudit(
                        firstSafePrompt,
                        2,
                        fixture.observer))
                .thenReturn(secondSafePrompt);
        when(fixture.creator.generateAttempt(
                        fixture.jobId,
                        secondSafePrompt.generationPrompt(),
                        1,
                        fixture.observer))
                .thenThrow(auditFailure);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer));

        assertTrue(exception.getMessage().contains("已达到上限 2 次"));
        verify(fixture.creator, times(3)).generateAttempt(
                eq(fixture.jobId), anyString(), eq(1), eq(fixture.observer));
        verify(fixture.picker, never()).prepare(any(), any(), anyString(), any());
    }

    @Test
    void shouldRetryOnceWithCorrectionPromptAndReuseUploadedImages() {
        Fixture fixture = new Fixture();
        FashionReferenceSpec spec = referenceSpec();
        OutfitQualityReport firstReport = report(false, true, 62, 35, List.of("红色发带"), "补上红色发带");
        OutfitQualityReport secondReport = report(true, false, 88, 91, List.of(), "");
        when(fixture.referenceAnalyzer.analyze(fixture.clothing, fixture.observer)).thenReturn(spec);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(fixture.attempt1);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(2), eq(fixture.observer)))
                .thenReturn(fixture.attempt2);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt1), eq(spec),
                        anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(firstReport);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt2), eq(spec),
                        anyString(), eq(2), eq(fixture.observer)))
                .thenReturn(secondReport);

        PipelineResult result = fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        assertEquals(2, result.attempts().size());
        assertTrue(result.attempts().get(1).selected());
        assertTrue(result.finalQualityReport().passed());
        verify(fixture.stylist, times(1)).upload(fixture.images, fixture.observer);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.stylist, times(2)).replaceAttempt(
                eq(fixture.jobId), eq(fixture.uploaded), promptCaptor.capture(), anyInt(), eq(fixture.observer));
        assertTrue(promptCaptor.getAllValues().get(1).contains("红色发带"));
    }

    @Test
    void shouldNotRetryWhenMultimodalQualityCheckIsUnavailable() {
        Fixture fixture = new Fixture();
        FashionReferenceSpec spec = FashionReferenceSpec.fallback("AI 未配置");
        OutfitQualityReport report = OutfitQualityReport.notEvaluated("AI 未配置");
        when(fixture.referenceAnalyzer.analyze(fixture.clothing, fixture.observer)).thenReturn(spec);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(fixture.attempt1);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt1), eq(spec),
                        anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(report);

        PipelineResult result = fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        assertEquals(1, result.attempts().size());
        verify(fixture.stylist, times(1)).replaceAttempt(
                eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer));
    }

    @Test
    void shouldRetrieveFashionKnowledgeAndInjectEvidenceIntoReplacementPrompt() {
        Fixture fixture = new Fixture();
        FashionKnowledgeContext context = new FashionKnowledgeContext(
                true,
                "头部和发饰：红色发带",
                List.of(new FashionKnowledgeHit(
                        "hair-and-accessories.md",
                        "发型与配饰迁移规则",
                        0,
                        0.912,
                        "发带必须说明颜色、形状和佩戴位置。")),
                "服装替换经验库检索结果：红色发带必须说明颜色、形状和佩戴位置。",
                "检索到 1 条服装经验并注入换装提示词");
        when(fixture.knowledgeRetriever.retrieve("测试人物", referenceSpec())).thenReturn(context);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(fixture.attempt1);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt1), eq(referenceSpec()),
                        anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(OutfitQualityReport.notEvaluated("AI 未配置"));

        fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.stylist).replaceAttempt(
                eq(fixture.jobId), eq(fixture.uploaded), promptCaptor.capture(), eq(1), eq(fixture.observer));
        String replacementPrompt = promptCaptor.getValue();
        assertTrue(replacementPrompt.startsWith(FashionReferenceSpec.fixedReplacementPrefix()));
        assertTrue(replacementPrompt.contains("红色发带必须说明颜色、形状和佩戴位置"));
        assertTrue(replacementPrompt.contains("对图2穿搭的视觉分析补充"));
        assertTrue(replacementPrompt.length() <= 3500);
        verify(fixture.observer).fashionKnowledge(context);
        verify(fixture.artifactService).writeText(fixture.jobId, "fashion-rag-query.txt", context.query());
        verify(fixture.artifactService).writeJson(fixture.jobId, "fashion-rag-context.json", context);
    }

    @Test
    void shouldStopAfterFirstAttemptWhenOverallScoreReachesSeventy() {
        Fixture fixture = new Fixture();
        FashionReferenceSpec spec = referenceSpec();
        OutfitQualityReport firstReport = report(false, true, 70, 65, List.of("红色发带"), "补上红色发带");
        when(fixture.referenceAnalyzer.analyze(fixture.clothing, fixture.observer)).thenReturn(spec);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(fixture.attempt1);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt1), eq(spec),
                        anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(firstReport);
        PipelineResult result = fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        assertEquals(1, result.attempts().size());
        assertTrue(result.attempts().get(0).selected());
        assertEquals(firstReport, result.finalQualityReport());
        verify(fixture.stylist, times(1)).replaceAttempt(
                eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer));
        verify(fixture.transferService).archiveLocal(fixture.attempt1, fixture.jobId, "outfit");
    }

    @Test
    void shouldPreserveCorrectionAtTheFrontWhenOriginalPromptIsVeryLong() {
        Fixture fixture = new Fixture();
        String longPrompt = "原始约束".repeat(900);
        FashionReferenceSpec spec = new FashionReferenceSpec(
                AnalysisMode.MULTIMODAL_AI,
                "红裙搭配金色发冠",
                List.of("红色连衣裙"),
                List.of("红色"),
                List.of("丝绸"),
                List.of("金色发冠"),
                List.of(),
                List.of("红色连衣裙", "金色发冠"),
                longPrompt);
        OutfitQualityReport firstReport = report(
                false, true, 62, 30, List.of("金色发冠"), "必须恢复金色发冠并保持佩戴位置");
        OutfitQualityReport secondReport = report(true, false, 86, 88, List.of(), "");
        when(fixture.referenceAnalyzer.analyze(fixture.clothing, fixture.observer)).thenReturn(spec);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(fixture.attempt1);
        when(fixture.stylist.replaceAttempt(
                        eq(fixture.jobId), eq(fixture.uploaded), anyString(), eq(2), eq(fixture.observer)))
                .thenReturn(fixture.attempt2);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt1), eq(spec),
                        anyString(), eq(1), eq(fixture.observer)))
                .thenReturn(firstReport);
        when(fixture.inspector.inspect(
                        eq(fixture.original), eq(fixture.clothing), eq(fixture.attempt2), eq(spec),
                        anyString(), eq(2), eq(fixture.observer)))
                .thenReturn(secondReport);

        fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.stylist, times(2)).replaceAttempt(
                eq(fixture.jobId), eq(fixture.uploaded), promptCaptor.capture(), anyInt(), eq(fixture.observer));
        String correctionPrompt = promptCaptor.getAllValues().get(1);
        assertTrue(correctionPrompt.startsWith(FashionReferenceSpec.fixedReplacementPrefix()));
        assertTrue(correctionPrompt.contains("本轮最高优先级纠正要求"));
        assertTrue(correctionPrompt.contains("必须恢复金色发冠并保持佩戴位置"));
        assertTrue(correctionPrompt.length() <= 3500);
    }

    @Test
    void shouldRegeneratePortraitWithCorrectionBeforeSelectingClothing() {
        Fixture fixture = new Fixture();
        PortraitQualityReport firstReport = portraitReport(
                false, true, 58, List.of("左手手指严重畸形"), "修复左手手指并保持其他内容不变");
        PortraitQualityReport secondReport = portraitReport(true, false, 88, List.of(), "");
        when(fixture.creator.generateAttempt(
                        eq(fixture.jobId), anyString(), eq(2), eq(fixture.observer)))
                .thenReturn(fixture.portraitAttempt2);
        when(fixture.portraitInspector.inspect(
                        eq(fixture.portraitAttempt1), eq(fixture.portraitPrompt), anyString(),
                        eq(1), eq(fixture.observer)))
                .thenReturn(firstReport);
        when(fixture.portraitInspector.inspect(
                        eq(fixture.portraitAttempt2), eq(fixture.portraitPrompt), anyString(),
                        eq(2), eq(fixture.observer)))
                .thenReturn(secondReport);
        when(fixture.transferService.archiveLocal(
                        fixture.portraitAttempt2, fixture.jobId, "original"))
                .thenReturn(fixture.original);

        PipelineResult result = fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        assertEquals(2, result.portraitAttempts().size());
        assertTrue(result.portraitAttempts().get(1).selected());
        assertTrue(result.finalPortraitQualityReport().passed());
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.creator, times(2)).generateAttempt(
                eq(fixture.jobId), promptCaptor.capture(), anyInt(), eq(fixture.observer));
        assertTrue(promptCaptor.getAllValues().get(1).contains("修复左手手指"));
        verify(fixture.picker).prepare(
                eq(fixture.jobId), eq(fixture.original), anyString(), eq(fixture.observer));
    }

    @Test
    void shouldStopBeforeClothingWhenPortraitFailsFinalQualityCheck() {
        Fixture fixture = new Fixture();
        PortraitQualityReport failedReport = portraitReport(
                false, false, 45, List.of("图片空白"), "");
        when(fixture.portraitInspector.inspect(
                        eq(fixture.portraitAttempt1), eq(fixture.portraitPrompt), anyString(),
                        eq(1), eq(fixture.observer)))
                .thenReturn(failedReport);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer));

        assertTrue(exception.getMessage().contains("已停止进入换装流程"));
        verify(fixture.picker, never()).prepare(any(), any(), anyString(), any());
    }

    @Test
    void shouldContinueToClothingWhenPortraitQualityCallCannotEvaluateImage() {
        Fixture fixture = new Fixture();

        PipelineResult result = fixture.pipeline.run(fixture.jobId, "测试人物", fixture.observer);

        assertFalse(result.finalPortraitQualityReport().evaluated());
        assertTrue(result.finalPortraitQualityReport().passed());
        verify(fixture.picker).prepare(
                fixture.jobId, fixture.original, fixture.portraitPrompt.generationPrompt(), fixture.observer);
    }

    private static FashionReferenceSpec referenceSpec() {
        return new FashionReferenceSpec(
                AnalysisMode.MULTIMODAL_AI,
                "红裙搭配红色发带",
                List.of("红色连衣裙"),
                List.of("红色"),
                List.of("丝绸"),
                List.of("红色发带"),
                List.of(),
                List.of("红色连衣裙", "红色发带"),
                "迁移红色连衣裙和红色发带，保留人物身份");
    }

    private static PortraitPromptSpec safePortraitPrompt() {
        return safePortraitPrompt(
                "20到30岁的成年女性，穿着完整得体的日常时尚服装，在艺术馆正面自然站立，全身居中构图");
    }

    private static PortraitPromptSpec safePortraitPrompt(String generationPrompt) {
        return new PortraitPromptSpec(
                AnalysisMode.MULTIMODAL_AI,
                "测试人物",
                "20到30岁的成年女性，自然面容",
                "自然站立，姿态舒展",
                "明亮的艺术馆",
                "柔和自然光",
                "9:16全身居中构图",
                "商业人像摄影",
                generationPrompt);
    }

    private static OutfitQualityReport report(
            boolean passed,
            boolean retryable,
            int overall,
            int headScore,
            List<String> missing,
            String correction) {
        return new OutfitQualityReport(
                AnalysisMode.MULTIMODAL_AI,
                true,
                passed,
                overall,
                overall,
                headScore,
                90,
                retryable,
                passed ? "通过" : "遗漏头饰",
                missing,
                missing,
                correction);
    }

    private static PortraitQualityReport portraitReport(
            boolean passed,
            boolean retryable,
            int overall,
            List<String> issues,
            String correction) {
        return new PortraitQualityReport(
                AnalysisMode.MULTIMODAL_AI,
                true,
                passed,
                !issues.contains("图片空白"),
                overall,
                overall,
                overall,
                overall,
                retryable,
                passed ? "人物底图通过" : "人物底图存在问题",
                issues,
                correction);
    }

    private static final class Fixture {
        private final UUID jobId = UUID.randomUUID();
        private final Path original = Path.of("original.png");
        private final Path portraitAttempt1 = Path.of("portrait-attempt-1.png");
        private final Path portraitAttempt2 = Path.of("portrait-attempt-2.png");
        private final Path clothing = Path.of("clothing.png");
        private final Path attempt1 = Path.of("outfit-attempt-1.png");
        private final Path attempt2 = Path.of("outfit-attempt-2.png");
        private final Path finalImage = Path.of("outfit.png");
        private final Agent2ClothingPicker.ImagePair images =
                new Agent2ClothingPicker.ImagePair(original, clothing);
        private final ClothingReplacementService.UploadedImages uploaded =
                new ClothingReplacementService.UploadedImages("person.png", "clothing.png");
        private final Agent1BeautyCreator creator = mock(Agent1BeautyCreator.class);
        private final Agent2ClothingPicker picker = mock(Agent2ClothingPicker.class);
        private final Agent3OutfitStylist stylist = mock(Agent3OutfitStylist.class);
        private final Agent4ResultPresenter presenter = mock(Agent4ResultPresenter.class);
        private final FashionReferenceAnalyzer referenceAnalyzer = mock(FashionReferenceAnalyzer.class);
        private final OutfitQualityInspector inspector = mock(OutfitQualityInspector.class);
        private final PortraitPromptEnhancer portraitEnhancer = mock(PortraitPromptEnhancer.class);
        private final PortraitQualityInspector portraitInspector = mock(PortraitQualityInspector.class);
        private final ClothingCatalog catalog = mock(ClothingCatalog.class);
        private final ImageTransferService transferService = mock(ImageTransferService.class);
        private final JobArtifactService artifactService = mock(JobArtifactService.class);
        private final FashionKnowledgeRetriever knowledgeRetriever = mock(FashionKnowledgeRetriever.class);
        private final PipelineObserver observer = mock(PipelineObserver.class);
        private final PortraitPromptSpec portraitPrompt = PortraitPromptSpec.fallback("测试人物", "AI 未配置");
        private final FashionAgentPipeline pipeline;

        private Fixture() {
            RunningHubProperties runningHubProperties = new RunningHubProperties();
            runningHubProperties.setApiKey("test-key");
            FashionAiProperties aiProperties = new FashionAiProperties();
            aiProperties.setMaxOutfitAttempts(2);
            when(portraitEnhancer.enhance("测试人物", observer)).thenReturn(portraitPrompt);
            when(creator.generateAttempt(jobId, portraitPrompt.generationPrompt(), 1, observer))
                    .thenReturn(portraitAttempt1);
            when(portraitInspector.inspect(
                            portraitAttempt1,
                            portraitPrompt,
                            portraitPrompt.generationPrompt(),
                            1,
                            observer))
                    .thenReturn(PortraitQualityReport.notEvaluated("AI 未配置"));
            when(transferService.archiveLocal(portraitAttempt1, jobId, "original")).thenReturn(original);
            when(picker.prepare(eq(jobId), eq(original), anyString(), eq(observer))).thenReturn(images);
            when(referenceAnalyzer.analyze(clothing, observer)).thenReturn(referenceSpec());
            when(knowledgeRetriever.retrieve(eq("测试人物"), any(FashionReferenceSpec.class)))
                    .thenReturn(FashionKnowledgeContext.disabled("测试中关闭 RAG"));
            when(stylist.upload(images, observer)).thenReturn(uploaded);
            when(stylist.replaceAttempt(jobId, uploaded, referenceSpec().replacementPrompt(), 1, observer))
                    .thenReturn(attempt1);
            when(inspector.inspect(
                            original,
                            clothing,
                            attempt1,
                            referenceSpec(),
                            referenceSpec().replacementPrompt(),
                            1,
                            observer))
                    .thenReturn(OutfitQualityReport.notEvaluated("AI 未配置"));
            when(transferService.archiveLocal(any(Path.class), eq(jobId), eq("outfit"))).thenReturn(finalImage);
            when(presenter.present(
                            eq(finalImage), eq(clothing), any(OutfitQualityReport.class), anyInt(), eq(observer)))
                    .thenReturn(new Agent4ResultPresenter.PresentedResult(finalImage, "done"));
            pipeline = new FashionAgentPipeline(
                    creator,
                    picker,
                    stylist,
                    presenter,
                    portraitEnhancer,
                    portraitInspector,
                    referenceAnalyzer,
                    inspector,
                    catalog,
                    runningHubProperties,
                    aiProperties,
                    transferService,
                    artifactService,
                    knowledgeRetriever);
        }
    }
}
