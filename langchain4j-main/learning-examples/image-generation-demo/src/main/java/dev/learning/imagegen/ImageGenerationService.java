package dev.learning.imagegen;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import org.springframework.stereotype.Service;

@Service
class ImageGenerationService {

    private volatile ImageModel imageModel;
    private volatile AppConfig config;

    ImageGenerationResult generate(String prompt) {
        String normalizedPrompt = normalizePrompt(prompt);
        AppConfig currentConfig = config();
        Image image = imageModel(currentConfig).generate(normalizedPrompt).content();
        return ImageGenerationResult.from(image, currentConfig.outputFormat());
    }

    private ImageModel imageModel(AppConfig currentConfig) {
        if (imageModel == null) {
            synchronized (this) {
                if (imageModel == null) {
                    imageModel = ImageModelFactory.create(currentConfig);
                }
            }
        }
        return imageModel;
    }

    private AppConfig config() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    config = AppConfig.fromEnvironment();
                }
            }
        }
        return config;
    }

    private static String normalizePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }
        String normalized = prompt.trim();
        if (normalized.length() > 4000) {
            throw new IllegalArgumentException("提示词不能超过 4000 个字符");
        }
        return normalized;
    }
}
