package dev.learning.fashionagent.comfyui;

import java.nio.file.Path;

public record ComfyUiVideoSnapshot(String accountId, ComfyUiVideoView view, Path inputDirectory, Path finalVideo) {}
