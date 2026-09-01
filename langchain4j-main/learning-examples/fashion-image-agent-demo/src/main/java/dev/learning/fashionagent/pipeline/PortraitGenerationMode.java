package dev.learning.fashionagent.pipeline;

public enum PortraitGenerationMode {
    STANDARD,
    ENHANCED;

    public static PortraitGenerationMode defaultIfNull(PortraitGenerationMode mode) {
        return mode == null ? STANDARD : mode;
    }
}
