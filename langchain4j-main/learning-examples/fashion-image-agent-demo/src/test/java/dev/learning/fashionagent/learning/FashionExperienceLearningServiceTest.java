package dev.learning.fashionagent.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.ai.AnalysisMode;
import dev.learning.fashionagent.ai.FashionExperienceDraft;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.FashionVisionService;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.pipeline.OutfitAttempt;
import dev.learning.fashionagent.pipeline.PipelineResult;
import dev.learning.fashionagent.rag.FashionKnowledgeRetriever;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FashionExperienceLearningServiceTest {

    @Test
    void shouldPersistAndIndexExperienceOnlyAfterPassedQualityInspection() {
        FashionVisionService vision = mock(FashionVisionService.class);
        FashionLearningRepository repository = mock(FashionLearningRepository.class);
        FashionKnowledgeRetriever retriever = mock(FashionKnowledgeRetriever.class);
        UUID jobId = UUID.randomUUID();
        FashionExperienceDraft draft = new FashionExperienceDraft(
                "发饰完整迁移",
                "包含头饰的复古造型",
                "按从头到脚顺序描述",
                List.of("明确头饰颜色和位置"),
                List.of("避免改变人物身份"),
                List.of("头饰", "复古"));
        when(vision.extractSuccessfulExperience(
                        eq("复古展厅人物"), any(), any(), eq("最终提示词")))
                .thenReturn(draft);

        FashionExperienceLearningService service = new FashionExperienceLearningService(
                vision, repository, retriever, properties());
        ExperienceLearningResult result = service.learn(
                jobId, "复古展厅人物", pipelineResult(passedReport()));

        assertEquals("LEARNED", result.status());
        assertNotNull(result.experience());
        ArgumentCaptor<LearnedFashionExperience> captor = ArgumentCaptor.forClass(LearnedFashionExperience.class);
        verify(repository).saveExperience(captor.capture());
        verify(retriever).addExperience(captor.getValue());
        assertEquals(jobId, captor.getValue().sourceJobId());
        assertEquals(88, captor.getValue().qualityScore());
    }

    @Test
    void shouldNotLearnWhenQualityInspectionWasSkipped() {
        FashionVisionService vision = mock(FashionVisionService.class);
        FashionLearningRepository repository = mock(FashionLearningRepository.class);
        FashionKnowledgeRetriever retriever = mock(FashionKnowledgeRetriever.class);
        FashionExperienceLearningService service = new FashionExperienceLearningService(
                vision, repository, retriever, properties());

        ExperienceLearningResult result = service.learn(
                UUID.randomUUID(),
                "测试人物",
                pipelineResult(OutfitQualityReport.notEvaluated("模型繁忙")));

        assertEquals("SKIPPED", result.status());
        verifyNoInteractions(vision, repository, retriever);
    }

    @Test
    void shouldLearnMissingElementsWhenOverallScoreReachesSeventy() {
        FashionVisionService vision = mock(FashionVisionService.class);
        FashionLearningRepository repository = mock(FashionLearningRepository.class);
        FashionKnowledgeRetriever retriever = mock(FashionKnowledgeRetriever.class);
        UUID jobId = UUID.randomUUID();
        OutfitQualityReport acceptableWithMissingElements = new OutfitQualityReport(
                AnalysisMode.MULTIMODAL_AI,
                true,
                false,
                70,
                72,
                62,
                85,
                true,
                "整体可接受，但遗漏红色发带",
                List.of("发带未迁移"),
                List.of("红色发带"),
                "补齐红色发带");
        FashionExperienceDraft draft = new FashionExperienceDraft(
                "红色发带遗漏修复",
                "包含发带的服装替换",
                "服装主体迁移正确",
                List.of("生成前明确红色发带的颜色、形状和佩戴位置", "质检时逐项确认红色发带"),
                List.of("发带容易遗漏"),
                List.of("红色发带", "头饰"));
        when(vision.extractSuccessfulExperience(
                        eq("测试人物"), any(), eq(acceptableWithMissingElements), eq("最终提示词")))
                .thenReturn(draft);
        FashionExperienceLearningService service = new FashionExperienceLearningService(
                vision, repository, retriever, properties());

        ExperienceLearningResult result = service.learn(
                jobId, "测试人物", pipelineResult(acceptableWithMissingElements));

        assertEquals("LEARNED", result.status());
        verify(repository).saveExperience(any());
        verify(retriever).addExperience(any());
    }

    @Test
    void shouldNotLearnWhenOverallScoreIsBelowSeventy() {
        FashionVisionService vision = mock(FashionVisionService.class);
        FashionLearningRepository repository = mock(FashionLearningRepository.class);
        FashionKnowledgeRetriever retriever = mock(FashionKnowledgeRetriever.class);
        OutfitQualityReport lowScore = new OutfitQualityReport(
                AnalysisMode.MULTIMODAL_AI,
                true,
                false,
                69,
                69,
                69,
                69,
                true,
                "质量不足",
                List.of("服装差异明显"),
                List.of("发饰"),
                "重新生成");
        FashionExperienceLearningService service = new FashionExperienceLearningService(
                vision, repository, retriever, properties());

        ExperienceLearningResult result = service.learn(
                UUID.randomUUID(), "测试人物", pipelineResult(lowScore));

        assertEquals("SKIPPED", result.status());
        verifyNoInteractions(vision, repository, retriever);
    }

    private static PipelineResult pipelineResult(OutfitQualityReport report) {
        FashionReferenceSpec spec = new FashionReferenceSpec(
                AnalysisMode.MULTIMODAL_AI,
                "红色复古礼服和同色发带",
                List.of("礼服"),
                List.of("红色"),
                List.of("丝绒"),
                List.of("红色发带"),
                List.of("项链"),
                List.of("礼服", "红色发带"),
                "完整迁移红色礼服和发带");
        OutfitAttempt selected = new OutfitAttempt(
                1, Path.of("outfit.png"), "最终提示词", report, true);
        return new PipelineResult(
                Path.of("original.png"),
                Path.of("clothing.png"),
                Path.of("outfit.png"),
                null,
                List.of(),
                null,
                spec,
                List.of(selected),
                report,
                "完成");
    }

    private static OutfitQualityReport passedReport() {
        return new OutfitQualityReport(
                AnalysisMode.MULTIMODAL_AI,
                true,
                true,
                88,
                90,
                86,
                91,
                false,
                "服装、头饰和人物身份均保持良好",
                List.of(),
                List.of(),
                "");
    }

    private static FashionAiProperties properties() {
        FashionAiProperties properties = new FashionAiProperties();
        properties.setOutfitAcceptAndLearnScore(70);
        return properties;
    }
}
