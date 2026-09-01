package dev.learning.imagegen;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;

public final class ImageGenerationCli {

    private ImageGenerationCli() {}

    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.fromEnvironment();
            String prompt = readPrompt(args);
            ImageModel imageModel = ImageModelFactory.create(config);

            System.out.println("Generating image with model " + config.modelName() + "...");
            Image image = imageModel.generate(prompt).content();
            Path outputFile = new GeneratedImageWriter(config.timeout()).write(image, config.outputDirectory());

            System.out.println("Image saved to: " + outputFile.toAbsolutePath());
            if (image.revisedPrompt() != null && !image.revisedPrompt().isBlank()) {
                System.out.println("Revised prompt: " + image.revisedPrompt());
            }
        } catch (Exception e) {
            System.err.println("Image generation failed: " + rootMessage(e));
            System.exit(1);
        }
    }

    private static String readPrompt(String[] args) throws Exception {
        String prompt = Arrays.stream(args)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .reduce((a, b) -> a + " " + b)
                .orElse(null);
        if (prompt == null) {
            System.out.print("Describe the image you want to generate: ");
            prompt = new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }
        return prompt.trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
