package dev.learning.fashionagent.director;

import java.time.Instant;
import java.util.UUID;

public record ShortDramaDirectorSnapshot(UUID id, String mode, String sourceType, String sourceFileName,
                                         String sourceText, String actionTier, String platform, String aspectRatio,
                                         String status, String message, String result, String error,
                                         Instant createdAt, Instant updatedAt) {}
