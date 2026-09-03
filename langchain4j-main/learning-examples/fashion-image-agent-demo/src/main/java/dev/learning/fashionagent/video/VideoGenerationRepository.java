package dev.learning.fashionagent.video;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VideoGenerationRepository {

    void save(VideoGenerationSnapshot snapshot);

    Optional<VideoGenerationView> find(UUID id);

    List<VideoGenerationView> list();

    List<VideoGenerationView> findBySourceJobId(UUID sourceJobId);

    void delete(UUID id);

    Optional<Path> finalVideo(UUID id);

    int recoverInterrupted();
}
