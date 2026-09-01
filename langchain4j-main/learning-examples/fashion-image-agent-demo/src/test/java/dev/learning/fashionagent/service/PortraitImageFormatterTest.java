package dev.learning.fashionagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortraitImageFormatterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldCenterCropAndResizeRunningHubPortraitTo1080x1920() throws Exception {
        Path source = temporaryDirectory.resolve("portrait.png");
        BufferedImage input = new BufferedImage(768, 1344, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = input.createGraphics();
        try {
            graphics.setColor(new Color(40, 120, 200));
            graphics.fillRect(0, 0, input.getWidth(), input.getHeight());
        } finally {
            graphics.dispose();
        }
        try (OutputStream outputStream = Files.newOutputStream(source)) {
            assertTrue(ImageIO.write(input, "png", outputStream));
        }

        PortraitImageFormatter formatter = new PortraitImageFormatter(new FashionAiProperties());
        Path result = formatter.format(source);

        BufferedImage output;
        try (InputStream inputStream = Files.newInputStream(result)) {
            output = ImageIO.read(inputStream);
        }
        assertEquals(source, result);
        assertEquals("1080x1920", formatter.targetSize());
        assertEquals(1080, output.getWidth());
        assertEquals(1920, output.getHeight());
        assertEquals(new Color(40, 120, 200).getRGB(), output.getRGB(540, 960));
        Files.delete(result);
        Files.delete(temporaryDirectory);
    }

    @Test
    void shouldResizeEnhancedPortraitTo756x1344() throws Exception {
        Path source = temporaryDirectory.resolve("enhanced-portrait.png");
        BufferedImage input = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        try (OutputStream outputStream = Files.newOutputStream(source)) {
            assertTrue(ImageIO.write(input, "png", outputStream));
        }

        PortraitImageFormatter formatter = new PortraitImageFormatter(new FashionAiProperties());
        Path result = formatter.format(source, PortraitGenerationMode.ENHANCED);

        BufferedImage output;
        try (InputStream inputStream = Files.newInputStream(result)) {
            output = ImageIO.read(inputStream);
        }
        assertEquals("756x1344", formatter.targetSize(PortraitGenerationMode.ENHANCED));
        assertEquals(756, output.getWidth());
        assertEquals(1344, output.getHeight());
        Files.delete(result);
        Files.delete(temporaryDirectory);
    }
}
