package dev.learning.imagegen;

import dev.langchain4j.data.image.Image;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

final class GeneratedImageWriter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final HttpClient httpClient;

    GeneratedImageWriter(Duration timeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GeneratedImageWriter(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    Path write(Image image, Path outputDirectory) throws IOException, InterruptedException {
        if (image == null) {
            throw new IllegalArgumentException("Generated image must not be null");
        }

        Files.createDirectories(outputDirectory);
        String extension = extensionOf(image);
        Path outputFile = outputDirectory.resolve("generated-" + TIMESTAMP.format(LocalDateTime.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8) + "." + extension);

        if (image.base64Data() != null && !image.base64Data().isBlank()) {
            Files.write(outputFile, decodeBase64(image.base64Data()));
            return outputFile;
        }

        if (image.url() != null) {
            download(image.url(), outputFile);
            return outputFile;
        }

        throw new IllegalArgumentException("The image response contains neither base64 data nor a URL");
    }

    private void download(URI uri, Path outputFile) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Image download failed with HTTP status " + response.statusCode());
        }
        Files.write(outputFile, response.body());
    }

    private static byte[] decodeBase64(String base64Data) {
        int comma = base64Data.indexOf(',');
        String encoded = base64Data.startsWith("data:") && comma >= 0 ? base64Data.substring(comma + 1) : base64Data;
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The image response contains invalid base64 data", e);
        }
    }

    private static String extensionOf(Image image) {
        if (image.mimeType() != null) {
            return switch (image.mimeType().toLowerCase(Locale.ROOT)) {
                case "image/jpeg", "image/jpg" -> "jpg";
                case "image/webp" -> "webp";
                default -> "png";
            };
        }

        if (image.url() != null) {
            String path = image.url().getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                return "jpg";
            }
            if (path.endsWith(".webp")) {
                return "webp";
            }
        }
        return "png";
    }
}
