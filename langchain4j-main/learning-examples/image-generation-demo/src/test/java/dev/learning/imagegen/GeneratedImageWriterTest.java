package dev.learning.imagegen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.image.Image;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratedImageWriterTest {

    Path tempDirectory;

    @BeforeEach
    void createTempDirectory() throws IOException {
        Path testTempRoot = Path.of("target", "test-tmp");
        Files.createDirectories(testTempRoot);
        tempDirectory = Files.createTempDirectory(testTempRoot, "image-writer-");
    }

    @AfterEach
    void deleteTempDirectory() throws IOException {
        try (var paths = Files.walk(tempDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void shouldDecodeAndWriteBase64Image() throws Exception {
        byte[] expected = "fake-png-content".getBytes(StandardCharsets.UTF_8);
        Image image = Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(expected))
                .mimeType("image/png")
                .build();

        Path output = new GeneratedImageWriter(HttpClient.newHttpClient()).write(image, tempDirectory);

        assertEquals("png", extension(output));
        assertArrayEquals(expected, Files.readAllBytes(output));
    }

    @Test
    void shouldAcceptDataUrlBase64() throws Exception {
        byte[] expected = "fake-webp-content".getBytes(StandardCharsets.UTF_8);
        Image image = Image.builder()
                .base64Data("data:image/webp;base64," + Base64.getEncoder().encodeToString(expected))
                .mimeType("image/webp")
                .build();

        Path output = new GeneratedImageWriter(HttpClient.newHttpClient()).write(image, tempDirectory);

        assertEquals("webp", extension(output));
        assertArrayEquals(expected, Files.readAllBytes(output));
    }

    @Test
    void shouldRejectImageWithoutContent() {
        Image image = Image.builder().build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new GeneratedImageWriter(HttpClient.newHttpClient()).write(image, tempDirectory));
    }

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
