package dev.learning.fashionagent.director;

import java.util.Locale;

public enum ShortDramaDirectorMode {
    FULL_EPISODE("整集创作"), SCREENPLAY("写剧本"), DIALOGUE_DOCTOR("台词诊断"),
    ASSET_BREAKDOWN("拆资产"), STORYBOARD("做分镜"), SPEECH_SPEED("语速自检"),
    VIDEO_PROMPT("生成视频提示词"), QUALITY_REVIEW("审查");

    private final String label;
    ShortDramaDirectorMode(String label) { this.label = label; }
    public String label() { return label; }
    public static ShortDramaDirectorMode parse(String value) {
        if (value == null || value.isBlank()) return FULL_EPISODE;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { throw new IllegalArgumentException("不支持的短剧导演模式：" + value); }
    }
}
