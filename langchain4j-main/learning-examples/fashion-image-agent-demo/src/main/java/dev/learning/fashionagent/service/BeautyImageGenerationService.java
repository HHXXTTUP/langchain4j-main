package dev.learning.fashionagent.service;

import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.NodeInput;
import dev.learning.fashionagent.integration.runninghub.RunningHubTaskRunner;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BeautyImageGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeautyImageGenerationService.class);
    private static final String STANDARD_GLAMOUR_PRESET = "明确为20到30岁的成年亚洲女性，采用中日韩审美风格，脸型和五官清晰自然，成熟优雅，"
            + "身材曲线匀称健美，上身曲线明显丰满，腰臀比例协调；"
            + "根据用户描述的环境和活动选择多样化的合身时尚服装，例如修身连衣裙、针织上衣、衬衫、轻便外套或通勤套装，"
            + "服装保持完整、不透明、适合公开展示，不固定某一种领口或单一服装；突出腰线、肩颈和成熟自然的身体比例；"
            + "正面自然站立，镜头视觉重点突出面部、肩颈和上半身服装轮廓，同时保持全身完整入镜，"
            + "大腿和腿部线条自然清晰，适合后续服装替换和公开展示";

    private final RunningHubTaskRunner taskRunner;
    private final RunningHubProperties properties;
    private final FashionAiProperties aiProperties;

    public BeautyImageGenerationService(
            RunningHubTaskRunner taskRunner,
            RunningHubProperties properties,
            FashionAiProperties aiProperties) {
        this.taskRunner = taskRunner;
        this.properties = properties;
        this.aiProperties = aiProperties;
    }

    public URI generate(String prompt, Consumer<String> progress) {
        return generate(prompt, PortraitGenerationMode.STANDARD, progress);
    }

    public URI generate(
            String prompt,
            PortraitGenerationMode mode,
            Consumer<String> progress) {
        String normalizedPrompt = requirePrompt(prompt);
        PortraitWorkflow workflow = workflowFor(mode);
        if (workflow.mode() == PortraitGenerationMode.STANDARD) {
            normalizedPrompt = appendStandardGlamourPreset(normalizedPrompt);
        }
        String width = workflow.mode() == PortraitGenerationMode.ENHANCED
                ? Integer.toString(aiProperties.getEnhancedPortraitOutputWidth())
                : Integer.toString(aiProperties.getPortraitOutputWidth());
        String height = workflow.mode() == PortraitGenerationMode.ENHANCED
                ? Integer.toString(aiProperties.getEnhancedPortraitOutputHeight())
                : Integer.toString(aiProperties.getPortraitOutputHeight());
        LOGGER.info("RunningHub 人物生成请求 mode={} appId={} width={} height={} promptNode={}/{} promptLength={}",
                workflow.mode(), workflow.appId(), width, height,
                workflow.promptNodeId(), workflow.promptFieldName(), normalizedPrompt.length());
        progress.accept(workflow.mode() == PortraitGenerationMode.ENHANCED
                ? "正在提交 RunningHub 增强版人物生成任务"
                : "正在提交 RunningHub 人物生成任务");
        List<NodeInput> inputs = workflow.mode() == PortraitGenerationMode.ENHANCED
                ? List.of(new NodeInput(workflow.promptNodeId(), workflow.promptFieldName(), normalizedPrompt, "提示词"))
                : List.of(
                        new NodeInput(workflow.widthNodeId(), workflow.widthFieldName(), width, "宽度"),
                        new NodeInput(workflow.heightNodeId(), workflow.heightFieldName(), height, "高度"),
                        new NodeInput(workflow.promptNodeId(), workflow.promptFieldName(), normalizedPrompt, "提示词"));
        RunningHubTaskRunner.TaskOutput output = taskRunner.run(
                workflow.appId(), inputs, progress);
        URI imageUrl = URI.create(output.url());
        LOGGER.info("RunningHub 人物生成完成 mode={} appId={} imageUrl={} outputType={}",
                workflow.mode(), workflow.appId(), imageUrl, output.outputType());
        return imageUrl;
    }

    private PortraitWorkflow workflowFor(PortraitGenerationMode requestedMode) {
        PortraitGenerationMode mode = PortraitGenerationMode.defaultIfNull(requestedMode);
        if (mode == PortraitGenerationMode.ENHANCED) {
            return new PortraitWorkflow(
                    mode,
                    "增强版",
                    properties.getEnhancedBeautyAppId(),
                    "57",
                    "text",
                    null,
                    null,
                    null,
                    null);
        }
        return new PortraitWorkflow(
                mode,
                "普通版",
                properties.getBeautyAppId(),
                "67",
                "text",
                "156",
                "value",
                "157",
                "value");
    }

    private static String requirePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("人物生成提示词不能为空");
        }
        return prompt.trim();
    }

    private static String appendStandardGlamourPreset(String prompt) {
        return prompt + "。普通版人物风格补充：" + STANDARD_GLAMOUR_PRESET + "。";
    }

    private record PortraitWorkflow(
            PortraitGenerationMode mode,
            String displayName,
            String appId,
            String promptNodeId,
            String promptFieldName,
            String widthNodeId,
            String widthFieldName,
            String heightNodeId,
            String heightFieldName) {}
}
