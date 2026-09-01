package dev.learning.fashionagent.comfyui;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComfyUiVideoRepository {
    void save(ComfyUiVideoSnapshot snapshot);
    List<ComfyUiVideoView> list(String accountId);
    Optional<ComfyUiVideoView> find(UUID id, String accountId);
    Optional<Path> finalVideo(UUID id, String accountId);
    void delete(UUID id, String accountId);
}
