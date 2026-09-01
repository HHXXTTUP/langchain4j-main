package dev.learning.imagegen;

import dev.langchain4j.data.image.Image;

record ImageGenerationResult(String imageSrc, String revisedPrompt) {

    static ImageGenerationResult from(Image image, String outputFormat) {
        if (image == null) {
            throw new IllegalArgumentException("Generated image must not be null");
        }

        if (image.base64Data() != null && !image.base64Data().isBlank()) {
            String base64Data = image.base64Data();
            String imageSrc = base64Data.startsWith("data:")
                    ? base64Data
                    : "data:" + mimeType(image, outputFormat) + ";base64," + base64Data;
            return new ImageGenerationResult(imageSrc, image.revisedPrompt());
        }

        if (image.url() != null) {
            return new ImageGenerationResult(image.url().toString(), image.revisedPrompt());
        }

        throw new IllegalArgumentException("The image response contains neither base64 data nor a URL");
    }

    private static String mimeType(Image image, String outputFormat) {
        if (image.mimeType() != null && !image.mimeType().isBlank()) {
            return image.mimeType();
        }
        return switch (outputFormat) {
            case "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }
}
