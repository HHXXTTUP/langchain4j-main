package dev.learning.fashionagent.service;

import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortraitImageFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortraitImageFormatter.class);

    private final int targetWidth;
    private final int targetHeight;
    private final int enhancedTargetWidth;
    private final int enhancedTargetHeight;

    public PortraitImageFormatter(FashionAiProperties properties) {
        this.targetWidth = requirePositive(properties.getPortraitOutputWidth(), "人物输出宽度");
        this.targetHeight = requirePositive(properties.getPortraitOutputHeight(), "人物输出高度");
        this.enhancedTargetWidth = requirePositive(
                properties.getEnhancedPortraitOutputWidth(), "增强版人物输出宽度");
        this.enhancedTargetHeight = requirePositive(
                properties.getEnhancedPortraitOutputHeight(), "增强版人物输出高度");
    }

    public Path format(Path source) {
        return format(source, PortraitGenerationMode.STANDARD);
    }

    public Path format(Path source, PortraitGenerationMode mode) {
        ImageSize targetSize = targetSizeFor(mode);
        BufferedImage original = read(source);
        if (original.getWidth() == targetSize.width() && original.getHeight() == targetSize.height()) {
            return source;
        }

        double scale = Math.max(
                (double) targetSize.width() / original.getWidth(),
                (double) targetSize.height() / original.getHeight());
        int scaledWidth = (int) Math.ceil(original.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(original.getHeight() * scale);
        BufferedImage output = new BufferedImage(targetSize.width(), targetSize.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetSize.width(), targetSize.height());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int x = (targetSize.width() - scaledWidth) / 2;
            int y = (targetSize.height() - scaledHeight) / 2;
            graphics.drawImage(original, x, y, scaledWidth, scaledHeight, null);
        } finally {
            graphics.dispose();
        }

        Path target = replaceExtension(source, ".png");
        writeAtomically(output, target);
        if (!source.equals(target)) {
            deleteSource(source);
        }
        LOGGER.info("人物底图尺寸已规范化 source={} target={} original={}x{} output={}x{}",
                source, target, original.getWidth(), original.getHeight(), targetSize.width(), targetSize.height());
        return target;
    }

    public String targetSize() {
        return targetWidth + "x" + targetHeight;
    }

    public String targetSize(PortraitGenerationMode mode) {
        ImageSize targetSize = targetSizeFor(mode);
        return targetSize.width() + "x" + targetSize.height();
    }

    private ImageSize targetSizeFor(PortraitGenerationMode mode) {
        return PortraitGenerationMode.defaultIfNull(mode) == PortraitGenerationMode.ENHANCED
                ? new ImageSize(enhancedTargetWidth, enhancedTargetHeight)
                : new ImageSize(targetWidth, targetHeight);
    }

    private static BufferedImage read(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("待处理的人物底图不存在：" + source);
        }
        try (InputStream input = Files.newInputStream(source)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalStateException("无法识别人物底图格式：" + source);
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("读取人物底图失败：" + source, exception);
        }
    }

    private static void writeAtomically(BufferedImage image, Path target) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), "portrait-format-", ".png");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                if (!ImageIO.write(image, "png", output)) {
                    throw new IllegalStateException("当前 Java 环境不支持 PNG 图片写入");
                }
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("保存1080x1920人物底图失败：" + target, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The completed target is authoritative; a leftover temp file is non-fatal.
                }
            }
        }
    }

    private static void deleteSource(Path source) {
        try {
            Files.deleteIfExists(source);
        } catch (IOException exception) {
            LOGGER.warn("规范化后未能删除旧人物底图 source={}", source, exception);
        }
    }

    private static Path replaceExtension(Path source, String extension) {
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        return source.resolveSibling(baseName + extension);
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
        return value;
    }

    private record ImageSize(int width, int height) {}
}
