package dev.learning.imagegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.image.Image;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ImageGenerationResultTest {

    @Test
    void shouldCreateDataUrlFromBase64() {
        Image image = Image.builder().base64Data("ZmFrZQ==").mimeType("image/webp").build();

        ImageGenerationResult result = ImageGenerationResult.from(image, "png");

        assertEquals("data:image/webp;base64,ZmFrZQ==", result.imageSrc());
    }

    @Test
    void shouldKeepExistingDataUrl() {
        Image image = Image.builder().base64Data("data:image/png;base64,ZmFrZQ==").build();

        ImageGenerationResult result = ImageGenerationResult.from(image, "png");

        assertEquals("data:image/png;base64,ZmFrZQ==", result.imageSrc());
    }

    @Test
    void shouldUseRemoteUrl() {
        Image image = Image.builder().url(URI.create("https://example.com/image.png")).build();

        ImageGenerationResult result = ImageGenerationResult.from(image, "png");

        assertEquals("https://example.com/image.png", result.imageSrc());
    }

    @Test
    void shouldRejectEmptyImage() {
        assertThrows(IllegalArgumentException.class, () -> ImageGenerationResult.from(Image.builder().build(), "png"));
    }
}
