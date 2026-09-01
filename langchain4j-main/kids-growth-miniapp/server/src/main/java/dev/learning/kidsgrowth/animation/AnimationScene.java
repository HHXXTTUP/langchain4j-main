package dev.learning.kidsgrowth.animation;

public record AnimationScene(
        String chineseText,
        String englishText,
        String template,
        String objectLabel,
        String emoji,
        String caption,
        String accentColor,
        String backgroundColor,
        String motion,
        String style,
        int durationMs) {}
