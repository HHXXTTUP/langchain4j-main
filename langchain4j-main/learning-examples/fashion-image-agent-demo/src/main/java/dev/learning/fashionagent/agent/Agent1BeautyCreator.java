package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import dev.learning.fashionagent.service.BeautyImageGenerationService;
import dev.learning.fashionagent.service.ImageTransferService;
import dev.learning.fashionagent.service.PortraitImageFormatter;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Agent1BeautyCreator {

    private final BeautyImageGenerationService imageGenerationService;
    private final ImageTransferService imageTransferService;
    private final PortraitImageFormatter imageFormatter;

    public Agent1BeautyCreator(
            BeautyImageGenerationService imageGenerationService,
            ImageTransferService imageTransferService,
            PortraitImageFormatter imageFormatter) {
        this.imageGenerationService = imageGenerationService;
        this.imageTransferService = imageTransferService;
        this.imageFormatter = imageFormatter;
    }

    public Path generateAttempt(
            UUID jobId,
            String generationPrompt,
            int attemptNumber,
            PipelineObserver observer) {
        return generateAttemptInternal(jobId, generationPrompt, attemptNumber, null, observer);
    }

    public Path generateAttempt(
            UUID jobId,
            String generationPrompt,
            int attemptNumber,
            PortraitGenerationMode mode,
            PipelineObserver observer) {
        return generateAttemptInternal(
                jobId,
                generationPrompt,
                attemptNumber,
                PortraitGenerationMode.defaultIfNull(mode),
                observer);
    }

    private Path generateAttemptInternal(
            UUID jobId,
            String generationPrompt,
            int attemptNumber,
            PortraitGenerationMode mode,
            PipelineObserver observer) {
        String prompt = normalizePrompt(generationPrompt);
        PortraitGenerationMode normalizedMode = PortraitGenerationMode.defaultIfNull(mode);
        observer.stage(
                PipelineStage.AGENT1_GENERATING_PERSON,
                "RunningHub " + modeLabel(normalizedMode) + "正在创建第 " + attemptNumber + " 张人物底图");
        java.util.function.Consumer<String> progress = status -> observer.stage(
                PipelineStage.AGENT1_GENERATING_PERSON,
                "第 " + attemptNumber + " 张人物图片任务：" + status);
        URI imageUrl = mode == null
                ? imageGenerationService.generate(prompt, progress)
                : imageGenerationService.generate(prompt, normalizedMode, progress);
        observer.stage(
                PipelineStage.AGENT1_GENERATING_PERSON,
                "第 " + attemptNumber + " 张人物底图已生成，正在下载到本地");
        Path localImage = imageTransferService.downloadRemote(
                imageUrl,
                jobId,
                "portrait-attempt-" + attemptNumber);
        String targetSize = mode == null
                ? imageFormatter.targetSize()
                : imageFormatter.targetSize(normalizedMode);
        observer.stage(
                PipelineStage.AGENT1_GENERATING_PERSON,
                "第 " + attemptNumber + " 张人物底图正在规范为" + targetSize + "竖版图片");
        Path formattedImage = mode == null
                ? imageFormatter.format(localImage)
                : imageFormatter.format(localImage, normalizedMode);
        observer.stage(
                PipelineStage.AGENT1_GENERATING_PERSON,
                "第 " + attemptNumber + " 张人物底图已保存为" + targetSize + "，准备进入人物质检");
        return formattedImage;
    }

    private static String modeLabel(PortraitGenerationMode mode) {
        return mode == PortraitGenerationMode.ENHANCED ? "增强版" : "普通版";
    }

    private static String normalizePrompt(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("描述词不能为空");
        }
        String prompt = description.trim();
        if (prompt.length() > 3500) {
            throw new IllegalArgumentException("扩写后的人物生成提示词不能超过 3500 个字符");
        }
        return prompt;
    }
}
