package dev.learning.fashionagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.NodeInput;
import dev.learning.fashionagent.integration.runninghub.RunningHubTaskRunner;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BeautyImageGenerationServiceTest {

    @Test
    void shouldGeneratePortraitWithDocumentedRunningHubApplicationAndPromptNode() {
        RunningHubTaskRunner taskRunner = mock(RunningHubTaskRunner.class);
        RunningHubProperties properties = new RunningHubProperties();
        FashionAiProperties aiProperties = new FashionAiProperties();
        BeautyImageGenerationService service = new BeautyImageGenerationService(taskRunner, properties, aiProperties);
        List<String> progressMessages = new ArrayList<>();
        Consumer<String> progress = progressMessages::add;
        when(taskRunner.run(eq("2066795888403640322"), anyList(), eq(progress)))
                .thenReturn(new RunningHubTaskRunner.TaskOutput(
                        "https://example.com/runninghub-person.png", "png"));
        String prompt = "亚洲面孔，20到30岁的成年女性，正面站立，全身构图";

        URI result = service.generate(prompt, progress);

        assertEquals(URI.create("https://example.com/runninghub-person.png"), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NodeInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskRunner).run(eq("2066795888403640322"), inputsCaptor.capture(), eq(progress));
        assertEquals(new NodeInput("156", "value", "1080", "宽度"), inputsCaptor.getValue().get(0));
        assertEquals(new NodeInput("157", "value", "1920", "高度"), inputsCaptor.getValue().get(1));
        NodeInput promptInput = inputsCaptor.getValue().get(2);
        assertEquals("67", promptInput.nodeId());
        assertEquals("text", promptInput.fieldName());
        assertTrue(promptInput.fieldValue().startsWith(prompt));
        assertTrue(promptInput.fieldValue().contains("20到30岁的成年亚洲女性"));
        assertTrue(promptInput.fieldValue().contains("上身曲线明显丰满"));
        assertTrue(promptInput.fieldValue().contains("中日韩审美风格"));
        assertTrue(promptInput.fieldValue().contains("大腿和腿部线条自然清晰"));
        assertTrue(promptInput.fieldValue().contains("根据用户描述的环境和活动选择多样化的合身时尚服装"));
        assertTrue(promptInput.fieldValue().contains("不固定某一种领口或单一服装"));
        assertTrue(promptInput.fieldValue().contains("镜头视觉重点突出面部、肩颈和上半身服装轮廓"));
        assertTrue(promptInput.fieldValue().contains("全身完整入镜"));
        assertEquals(List.of("正在提交 RunningHub 人物生成任务"), progressMessages);
    }

    @Test
    void shouldUseEnhancedApplicationAndExactDocumentedNodes() {
        RunningHubTaskRunner taskRunner = mock(RunningHubTaskRunner.class);
        RunningHubProperties properties = new RunningHubProperties();
        FashionAiProperties aiProperties = new FashionAiProperties();
        aiProperties.setPortraitOutputWidth(720);
        aiProperties.setPortraitOutputHeight(1280);
        BeautyImageGenerationService service = new BeautyImageGenerationService(taskRunner, properties, aiProperties);
        List<String> progressMessages = new ArrayList<>();
        Consumer<String> progress = progressMessages::add;
        when(taskRunner.run(eq("2039516680887472130"), anyList(), eq(progress)))
                .thenReturn(new RunningHubTaskRunner.TaskOutput(
                        "https://example.com/enhanced-person.png", "png"));
        String prompt = "亚洲面孔，20到30岁的成年女性，正面站立，全身构图";

        URI result = service.generate(prompt, PortraitGenerationMode.ENHANCED, progress);

        assertEquals(URI.create("https://example.com/enhanced-person.png"), result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NodeInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskRunner).run(eq("2039516680887472130"), inputsCaptor.capture(), eq(progress));
        assertEquals(List.of(
                new NodeInput("57", "text", prompt, "提示词")), inputsCaptor.getValue());
        assertEquals(List.of("正在提交 RunningHub 增强版人物生成任务"), progressMessages);
    }
}
