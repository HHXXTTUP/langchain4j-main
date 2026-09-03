package dev.learning.fashionagent.video;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QwenVideoScriptRepository {
    void save(QwenVideoScriptSnapshot snapshot);
    List<QwenVideoScriptSnapshot> list();
    Optional<QwenVideoScriptSnapshot> find(UUID id);
}
