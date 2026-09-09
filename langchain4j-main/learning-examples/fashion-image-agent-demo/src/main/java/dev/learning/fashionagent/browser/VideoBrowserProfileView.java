package dev.learning.fashionagent.browser;

import java.time.Instant;
import java.util.UUID;

public record VideoBrowserProfileView(
        UUID id,
        String name,
        String accountHint,
        String profileDirectory,
        String extensionDirectory,
        String browserExecutable,
        String startUrl,
        String status,
        Long processId,
        Instant createdAt,
        Instant updatedAt) {}
