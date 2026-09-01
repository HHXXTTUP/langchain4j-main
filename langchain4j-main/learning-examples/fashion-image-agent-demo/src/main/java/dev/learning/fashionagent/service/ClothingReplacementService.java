package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.NodeInput;
import dev.learning.fashionagent.integration.runninghub.RunningHubTaskRunner;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class ClothingReplacementService {

    private final ImageTransferService imageTransferService;
    private final RunningHubTaskRunner taskRunner;
    private final RunningHubProperties properties;

    public ClothingReplacementService(
            ImageTransferService imageTransferService,
            RunningHubTaskRunner taskRunner,
            RunningHubProperties properties) {
        this.imageTransferService = imageTransferService;
        this.taskRunner = taskRunner;
        this.properties = properties;
    }

    public UploadedImages upload(Path personImage, Path clothingImage, Consumer<String> progress) {
        progress.accept("UPLOADING_PERSON_IMAGE");
        String personFileName = imageTransferService.uploadLocal(personImage);
        progress.accept("UPLOADING_CLOTHING_IMAGE");
        String clothingFileName = imageTransferService.uploadLocal(clothingImage);
        return new UploadedImages(personFileName, clothingFileName);
    }

    public URI replace(UploadedImages images, String replacementPrompt, Consumer<String> progress) {
        if (images == null) {
            throw new IllegalArgumentException("换装前必须先上传人物图和服装图");
        }
        if (replacementPrompt == null || replacementPrompt.isBlank()) {
            throw new IllegalArgumentException("换装提示词不能为空");
        }
        progress.accept("SUBMITTING_OUTFIT_TASK");

        List<NodeInput> inputs = List.of(
                new NodeInput("107", "image", images.personFileName(), "上传底图"),
                new NodeInput("285", "image", images.clothingFileName(), "上传服装图"),
                new NodeInput("223", "value", replacementPrompt.trim(), "换装提示词"));
        return URI.create(taskRunner.run(properties.getOutfitAppId(), inputs, progress).url());
    }

    public record UploadedImages(String personFileName, String clothingFileName) {}
}
