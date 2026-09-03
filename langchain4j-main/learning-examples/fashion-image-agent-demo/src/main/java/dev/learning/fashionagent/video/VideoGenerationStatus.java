package dev.learning.fashionagent.video;

public enum VideoGenerationStatus {
    QUEUED,
    SPLITTING,
    GENERATING,
    DOWNLOADING,
    MERGING,
    QUALITY_CHECKING,
    SUCCESS,
    FAILED
}
