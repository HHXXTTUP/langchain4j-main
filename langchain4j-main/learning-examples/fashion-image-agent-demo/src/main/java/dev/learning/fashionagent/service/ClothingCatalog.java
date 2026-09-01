package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

@Service
public class ClothingCatalog {

    private final RunningHubProperties properties;
    private final RandomGenerator random = RandomGenerator.getDefault();

    public ClothingCatalog(RunningHubProperties properties) {
        this.properties = properties;
    }

    public Path randomImage() {
        Path directory = properties.getClothingDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            List<Path> images = imagesIn(directory);
            if (images.isEmpty()) {
                throw new IllegalStateException("服装目录中没有图片：" + directory);
            }
            return images.get(random.nextInt(images.size()));
        } catch (IOException exception) {
            throw new IllegalStateException("读取服装目录失败：" + directory, exception);
        }
    }

    public List<Path> images() {
        Path directory = properties.getClothingDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            return imagesIn(directory).stream()
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取服装目录失败：" + directory, exception);
        }
    }

    public int availableImageCount() {
        Path directory = properties.getClothingDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            return imagesIn(directory).size();
        } catch (IOException exception) {
            throw new IllegalStateException("读取服装目录失败：" + directory, exception);
        }
    }

    public void requireImages() {
        if (availableImageCount() == 0) {
            throw new IllegalStateException(
                    "请先把服装图片放入目录：" + properties.getClothingDirectory().toAbsolutePath().normalize());
        }
    }

    private static List<Path> imagesIn(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(ClothingCatalog::isSupportedImage)
                    .toList();
        }
    }

    private static boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg")
                || name.endsWith(".jpeg") || name.endsWith(".webp");
    }
}
