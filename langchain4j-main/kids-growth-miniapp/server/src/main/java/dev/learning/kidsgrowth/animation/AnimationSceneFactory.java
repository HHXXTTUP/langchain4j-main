package dev.learning.kidsgrowth.animation;

import java.util.Locale;

final class AnimationSceneFactory {

    private AnimationSceneFactory() {}

    static AnimationScene create(String chineseText, String englishText, String style) {
        String normalized = englishText == null
                ? ""
                : englishText.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim();
        Template template = templateFor(normalized);
        String safeStyle = style == null || style.isBlank() ? "温柔日常" : style.trim();
        return new AnimationScene(
                chineseText,
                englishText,
                template.templateName(),
                chineseText,
                template.emoji(),
                template.caption(),
                template.accentColor(),
                template.backgroundColor(),
                template.motion(),
                safeStyle,
                3200);
    }

    private static Template templateFor(String englishText) {
        if (containsAny(englishText, "apple", "fruit")) {
            return Template.APPLE;
        }
        if (containsAny(englishText, "cat", "kitten")) {
            return Template.CAT;
        }
        if (containsAny(englishText, "moon", "night")) {
            return Template.MOON;
        }
        if (containsAny(englishText, "sun", "sunshine")) {
            return Template.SUN;
        }
        if (containsAny(englishText, "star")) {
            return Template.STAR;
        }
        if (containsAny(englishText, "bird", "birdie")) {
            return Template.BIRD;
        }
        return Template.GENERIC;
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.equals(word) || value.contains(word + " ") || value.startsWith(word + " ")) {
                return true;
            }
        }
        return false;
    }

    private enum Template {
        APPLE("apple", "🍎", "Apple 在和你打招呼！", "#ef5b61", "#fff2ed", "bounce"),
        CAT("cat", "🐱", "Cat 伸了个懒腰，准备和你玩！", "#ec9b58", "#fff7df", "wiggle"),
        MOON("moon", "🌙", "Moon 悄悄照亮了夜空。", "#6e72c7", "#eef0ff", "float"),
        SUN("sun", "☀️", "Sun 把暖暖的光送给你！", "#f4ae3f", "#fff5d8", "pulse"),
        STAR("star", "⭐", "Star 一闪一闪，送你一颗勇气！", "#d89b25", "#fff5dc", "twinkle"),
        BIRD("bird", "🐦", "Bird 飞过天空，和你说 hello！", "#5f9fc8", "#e8f7ff", "fly"),
        GENERIC("generic", "✨", "这个小伙伴正在和你打招呼！", "#8a78bd", "#f4efff", "bounce");

        private final String name;
        private final String emoji;
        private final String caption;
        private final String accentColor;
        private final String backgroundColor;
        private final String motion;

        Template(String name, String emoji, String caption, String accentColor,
                String backgroundColor, String motion) {
            this.name = name;
            this.emoji = emoji;
            this.caption = caption;
            this.accentColor = accentColor;
            this.backgroundColor = backgroundColor;
            this.motion = motion;
        }

        String templateName() {
            return name;
        }

        String emoji() {
            return emoji;
        }

        String caption() {
            return caption;
        }

        String accentColor() {
            return accentColor;
        }

        String backgroundColor() {
            return backgroundColor;
        }

        String motion() {
            return motion;
        }
    }
}
