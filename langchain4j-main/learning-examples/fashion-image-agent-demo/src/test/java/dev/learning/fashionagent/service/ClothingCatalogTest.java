package dev.learning.fashionagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.learning.fashionagent.config.RunningHubProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClothingCatalogTest {

    private Path testDirectory;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Path.of("target", "test-data");
        Files.createDirectories(root);
        testDirectory = Files.createTempDirectory(root, "clothing-");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var paths = Files.walk(testDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void shouldSelectSupportedImageAndIgnoreOtherFiles() throws Exception {
        Path expected = Files.write(testDirectory.resolve("dress.webp"), new byte[] {1, 2, 3});
        Files.writeString(testDirectory.resolve("notes.txt"), "ignore");
        RunningHubProperties properties = new RunningHubProperties();
        properties.setClothingDirectory(testDirectory);

        Path selected = new ClothingCatalog(properties).randomImage();

        assertEquals(expected.toAbsolutePath().normalize(), selected);
    }

    @Test
    void shouldRejectEmptyClothingDirectory() {
        RunningHubProperties properties = new RunningHubProperties();
        properties.setClothingDirectory(testDirectory);

        assertThrows(IllegalStateException.class, () -> new ClothingCatalog(properties).randomImage());
    }
}
