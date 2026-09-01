package dev.learning.fashionagent.ai;

import dev.langchain4j.data.message.ImageContent;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class VisionImageEncoder {

    private final int maxDimension;

    public VisionImageEncoder(int maxDimension) {
        this.maxDimension = Math.max(512, maxDimension);
    }

    public ImageContent encode(Path imagePath) {
        if (imagePath == null || !Files.isRegularFile(imagePath)) {
            throw new IllegalArgumentException("视觉分析图片不存在：" + imagePath);
        }
        try {
            BufferedImage source = ImageIO.read(imagePath.toFile());
            if (source == null) {
                return rawImage(imagePath);
            }
            BufferedImage normalized = resize(source);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(normalized, "jpg", output)) {
                return rawImage(imagePath);
            }
            return ImageContent.from(
                    Base64.getEncoder().encodeToString(output.toByteArray()),
                    "image/jpeg");
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取视觉分析图片失败：" + imagePath, exception);
        }
    }

    private BufferedImage resize(BufferedImage source) {
        double scale = Math.min(1D, (double) maxDimension / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static ImageContent rawImage(Path imagePath) throws IOException {
        String mimeType = Files.probeContentType(imagePath);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            mimeType = "image/png";
        }
        return ImageContent.from(
                Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath)),
                mimeType);
    }
}
