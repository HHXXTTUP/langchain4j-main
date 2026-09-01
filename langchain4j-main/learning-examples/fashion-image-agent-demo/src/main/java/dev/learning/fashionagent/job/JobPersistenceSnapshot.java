package dev.learning.fashionagent.job;

import java.nio.file.Path;

record JobPersistenceSnapshot(
        JobView view,
        Path originalImage,
        Path clothingImage,
        Path finalImage) {}
