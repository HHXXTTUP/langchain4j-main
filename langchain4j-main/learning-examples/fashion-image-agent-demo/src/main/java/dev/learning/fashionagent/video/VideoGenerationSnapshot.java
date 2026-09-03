package dev.learning.fashionagent.video;

import java.nio.file.Path;

record VideoGenerationSnapshot(VideoGenerationView view, Path sourceVideo, Path finalVideo) {}
