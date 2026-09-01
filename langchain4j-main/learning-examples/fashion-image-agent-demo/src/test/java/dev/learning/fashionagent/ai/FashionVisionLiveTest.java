package dev.learning.fashionagent.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FashionVisionLiveTest {

    @Test
    void shouldAnalyzeRealClothingImageWhenLiveProbeIsEnabled() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("FASHION_AI_LIVE_TEST")));
        String apiKey = System.getenv("ZHIPU_API_KEY");
        String imagePath = System.getProperty("fashion.ai.live-image");
        assumeTrue(apiKey != null && !apiKey.isBlank());
        assumeTrue(imagePath != null && !imagePath.isBlank());

        FashionAiProperties properties = new FashionAiProperties();
        properties.setApiKey(apiKey);
        FashionAiConfiguration configuration = new FashionAiConfiguration();
        FashionAiConfiguration.FashionAiModelRuntime runtime = configuration.fashionAiModelRuntime(properties);
        FashionVisionService service = configuration.fashionVisionService(properties, runtime);

        FashionReferenceSpec result = service.analyzeClothing(Path.of(imagePath));

        assertTrue(result.aiAnalyzed(), result.summary());
        assertTrue(!result.replacementPrompt().isBlank());
    }

    @Test
    void shouldEnhanceSimplePortraitPromptWhenLiveProbeIsEnabled() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("FASHION_AI_LIVE_TEST")));
        String apiKey = System.getenv("ZHIPU_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank());

        FashionAiProperties properties = new FashionAiProperties();
        properties.setApiKey(apiKey);
        FashionAiConfiguration configuration = new FashionAiConfiguration();
        FashionAiConfiguration.FashionAiModelRuntime runtime = configuration.fashionAiModelRuntime(properties);
        PortraitAiService service = configuration.portraitAiService(properties, runtime);

        PortraitPromptSpec result = service.enhancePrompt("一个美女");

        assertTrue(result.aiEnhanced(), result.generationPrompt());
        assertTrue(result.generationPrompt().contains("成年"));
        assertTrue(result.generationPrompt().length() > "一个美女".length());
    }
}
