package dev.learning.imagegen;

import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;

final class ImageModelFactory {

    private ImageModelFactory() {}

    static ImageModel create(AppConfig config) {
        OpenAiImageModel.OpenAiImageModelBuilder builder = OpenAiImageModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.modelName())
                .outputFormat(config.outputFormat())
                .timeout(config.timeout())
                .logRequests(false)
                .logResponses(false);

        if (config.baseUrl() != null) {
            builder.baseUrl(config.baseUrl());
        }
        if (config.size() != null) {
            builder.size(config.size());
        }
        if (config.quality() != null) {
            builder.quality(config.quality());
        }
        return builder.build();
    }
}
