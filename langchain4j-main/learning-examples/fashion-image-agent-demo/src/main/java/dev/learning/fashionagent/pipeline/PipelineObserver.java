package dev.learning.fashionagent.pipeline;

import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.learning.ClothingSemanticSelector;
import dev.learning.fashionagent.rag.FashionKnowledgeContext;
import java.nio.file.Path;

public interface PipelineObserver {

    void stage(PipelineStage stage, String message);

    void originalImage(Path imagePath);

    void portraitPrompt(PortraitPromptSpec promptSpec);

    void portraitAttempt(PortraitAttempt attempt);

    void clothingImage(Path imagePath);

    default void clothingSelection(ClothingSemanticSelector.Selection selection) {
        clothingImage(selection.image());
    }

    void fashionAnalysis(FashionReferenceSpec analysis);

    default void fashionKnowledge(FashionKnowledgeContext context) {}

    void outfitAttempt(OutfitAttempt attempt);

    void finalImage(Path imagePath);
}
