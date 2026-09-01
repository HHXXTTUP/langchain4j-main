package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import dev.learning.fashionagent.service.ClothingReplacementService;
import dev.learning.fashionagent.service.ImageTransferService;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Agent3OutfitStylist {

    private final ClothingReplacementService clothingReplacementService;
    private final ImageTransferService imageTransferService;

    public Agent3OutfitStylist(
            ClothingReplacementService clothingReplacementService,
            ImageTransferService imageTransferService) {
        this.clothingReplacementService = clothingReplacementService;
        this.imageTransferService = imageTransferService;
    }

    public ClothingReplacementService.UploadedImages upload(
            Agent2ClothingPicker.ImagePair images, PipelineObserver observer) {
        observer.stage(PipelineStage.AGENT3_REPLACING_OUTFIT, "换装工具正在上传人物图和服装图");
        return clothingReplacementService.upload(
                images.personImage(),
                images.clothingImage(),
                status -> observer.stage(
                        PipelineStage.AGENT3_REPLACING_OUTFIT,
                        statusMessage(status)));
    }

    public Path replaceAttempt(
            UUID jobId,
            ClothingReplacementService.UploadedImages uploadedImages,
            String replacementPrompt,
            int attemptNumber,
            PipelineObserver observer) {
        observer.stage(
                PipelineStage.AGENT3_REPLACING_OUTFIT,
                "换装工具正在执行第 " + attemptNumber + " 次生成");
        URI remoteImage = clothingReplacementService.replace(
                uploadedImages,
                replacementPrompt,
                status -> observer.stage(
                        PipelineStage.AGENT3_REPLACING_OUTFIT,
                        "第 " + attemptNumber + " 次换装：" + statusMessage(status)));
        observer.stage(PipelineStage.AGENT3_REPLACING_OUTFIT, "第 " + attemptNumber + " 次换装完成，正在下载结果");
        return imageTransferService.downloadRemote(remoteImage, jobId, "outfit-attempt-" + attemptNumber);
    }

    private static String statusMessage(String status) {
        return switch (status) {
            case "UPLOADING_PERSON_IMAGE" -> "正在上传人物底图";
            case "UPLOADING_CLOTHING_IMAGE" -> "正在上传服装图";
            case "SUBMITTING_OUTFIT_TASK" -> "图片已就绪，正在提交换装工作流";
            default -> "换装任务状态：" + status;
        };
    }
}
