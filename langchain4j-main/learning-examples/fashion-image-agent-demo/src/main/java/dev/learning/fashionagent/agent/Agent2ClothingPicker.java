package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import dev.learning.fashionagent.learning.ClothingSemanticSelector;
import dev.learning.fashionagent.service.ImageTransferService;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Agent2ClothingPicker {

    private final ClothingSemanticSelector clothingSelector;
    private final ImageTransferService imageTransferService;

    public Agent2ClothingPicker(ClothingSemanticSelector clothingSelector, ImageTransferService imageTransferService) {
        this.clothingSelector = clothingSelector;
        this.imageTransferService = imageTransferService;
    }

    public ImagePair prepare(
            UUID jobId,
            Path personImage,
            String portraitDescription,
            PipelineObserver observer) {
        observer.stage(PipelineStage.AGENT2_SELECTING_CLOTHING, "Agent2 正在根据人物描述检索服装目录");
        ClothingSemanticSelector.Selection selection = clothingSelector.select(portraitDescription);
        observer.stage(PipelineStage.AGENT2_SELECTING_CLOTHING, selection.reason());
        Path selectedClothing = selection.image();
        Path clothingImage = imageTransferService.archiveLocal(selectedClothing, jobId, "clothing");
        observer.clothingSelection(selection.withImage(clothingImage));
        return new ImagePair(personImage, clothingImage);
    }

    public record ImagePair(Path personImage, Path clothingImage) {}
}
