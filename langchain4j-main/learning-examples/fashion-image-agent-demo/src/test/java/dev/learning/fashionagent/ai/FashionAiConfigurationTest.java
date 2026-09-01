package dev.learning.fashionagent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;

class FashionAiConfigurationTest {

    @Test
    void shouldBuildLangChain4jMultimodalAiServiceWithoutCallingRemoteApi() {
        FashionAiProperties properties = new FashionAiProperties();
        properties.setApiKey("test-key");

        FashionAiConfiguration configuration = new FashionAiConfiguration();
        FashionAiConfiguration.FashionAiModelRuntime runtime = configuration.fashionAiModelRuntime(properties);
        FashionVisionService service = configuration.fashionVisionService(properties, runtime);
        PortraitAiService portraitService = configuration.portraitAiService(properties, runtime);

        assertInstanceOf(OpenAiChatModel.class, runtime.model().orElseThrow());
        assertTrue(service.aiEnabled());
        assertTrue(portraitService.aiEnabled());
    }

    @Test
    void shouldUseFreeGlmVisionModelDefaults() {
        FashionAiProperties properties = new FashionAiProperties();

        assertEquals("glm-4.6v-flash", properties.getModelName());
        assertEquals(1080, properties.getPortraitOutputWidth());
        assertEquals(1920, properties.getPortraitOutputHeight());
        assertTrue(properties.getPortraitPreset().contains("亚洲面孔"));
        assertTrue(properties.getPortraitPreset().contains("身材匀称健美"));
        assertTrue(properties.getPortraitPreset().contains("适合公开展示"));
        assertTrue(properties.getPortraitPreset().contains("正面面向镜头自然站立"));
        assertEquals(2, properties.getPortraitAuditMaxRetries());
        assertEquals(15, properties.getBusyMaxAttempts());
        assertEquals("https://open.bigmodel.cn/api/paas/v4", properties.getBaseUrl());
        assertFalse(properties.isProxyConfigured());
    }

    @Test
    void shouldExposeUnavailableServiceInsteadOfRuleFallbackWhenModelIsNotConfigured() {
        FashionAiProperties properties = new FashionAiProperties();
        FashionAiConfiguration configuration = new FashionAiConfiguration();
        FashionAiConfiguration.FashionAiModelRuntime runtime = configuration.fashionAiModelRuntime(properties);

        PortraitAiService portraitService = configuration.portraitAiService(properties, runtime);
        FashionVisionService visionService = configuration.fashionVisionService(properties, runtime);

        assertFalse(portraitService.aiEnabled());
        assertFalse(visionService.aiEnabled());
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> portraitService.enhancePrompt("一个美女"));
        assertTrue(exception.getMessage().contains("不会使用默认提示词"));
    }

    @Test
    void shouldFailFastWhenLangChain4jCannotEnhancePortraitPrompt() {
        ModelPortraitAgent agent = mock(ModelPortraitAgent.class);
        when(agent.enhance(anyString())).thenThrow(new IllegalStateException("AI connection failed"));
        LangChain4jPortraitAiService service = new LangChain4jPortraitAiService(
                agent,
                new VisionImageEncoder(512),
                new FashionAiProperties());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.enhancePrompt("一个美女"));

        assertTrue(exception.getMessage().contains("任务已停止且不会降级执行"));
        assertTrue(exception.getMessage().contains("AI connection failed"));
    }

    @Test
    void shouldUseDedicatedSafePresetWhenRewritingAfterRunningHubAudit() {
        ModelPortraitAgent agent = mock(ModelPortraitAgent.class);
        when(agent.rewriteAfterAudit(anyString())).thenReturn(new ModelPortraitAgent.ModelPortraitPrompt(
                "20到30岁的成年女性，自然面容",
                "正面自然站立",
                "现代艺术馆",
                "柔和自然光",
                "9:16全身居中构图",
                "商业人像摄影",
                "一位成年女性在现代艺术馆中自然站立，穿着日常时尚套装"));
        LangChain4jPortraitAiService service = new LangChain4jPortraitAiService(
                agent,
                new VisionImageEncoder(512),
                new FashionAiProperties());

        PortraitPromptSpec result = service.rewritePromptAfterAudit(
                PortraitPromptSpec.fallback("艺术馆美女", "测试"),
                1);

        assertTrue(result.aiEnhanced());
        assertEquals("艺术馆美女", result.originalDescription());
        assertTrue(result.generationPrompt().contains("完整得体的日常时尚服装"));
        assertTrue(result.generationPrompt().contains("1080x1920"));
        assertTrue(result.generationPrompt().contains("胸部丰满"));
        assertFalse(result.generationPrompt().contains("性感清凉"));
    }

    @Test
    void shouldSkipPortraitQualityCheckWhenModelCallFails() {
        ModelPortraitAgent agent = mock(ModelPortraitAgent.class);
        VisionImageEncoder imageEncoder = mock(VisionImageEncoder.class);
        when(imageEncoder.encode(any())).thenReturn(mock(ImageContent.class));
        when(agent.inspect(anyString(), any(ImageContent.class)))
                .thenThrow(new IllegalStateException("quality model unavailable"));
        LangChain4jPortraitAiService service = new LangChain4jPortraitAiService(
                agent,
                imageEncoder,
                new FashionAiProperties());

        PortraitQualityReport report = service.inspectPortrait(
                java.nio.file.Path.of("portrait.png"),
                PortraitPromptSpec.fallback("测试人物", "测试"),
                "测试提示词",
                1);

        assertFalse(report.evaluated());
        assertTrue(report.passed());
        assertTrue(report.summary().contains("已跳过质检并继续流程"));
    }

    @Test
    void shouldSkipOutfitQualityCheckWhenModelCallFails() {
        ModelFashionVisionAgent agent = mock(ModelFashionVisionAgent.class);
        VisionImageEncoder imageEncoder = mock(VisionImageEncoder.class);
        when(imageEncoder.encode(any())).thenReturn(mock(ImageContent.class));
        when(agent.inspect(anyString(), anyList()))
                .thenThrow(new IllegalStateException("quality model unavailable"));
        LangChain4jFashionVisionService service = new LangChain4jFashionVisionService(
                agent,
                imageEncoder,
                new FashionAiProperties());

        OutfitQualityReport report = service.inspectResult(
                java.nio.file.Path.of("original.png"),
                java.nio.file.Path.of("clothing.png"),
                java.nio.file.Path.of("result.png"),
                FashionReferenceSpec.fallback("测试"),
                "测试换装提示词",
                1);

        assertFalse(report.evaluated());
        assertTrue(report.passed());
        assertTrue(report.summary().contains("已跳过质检并继续流程"));
    }
}
