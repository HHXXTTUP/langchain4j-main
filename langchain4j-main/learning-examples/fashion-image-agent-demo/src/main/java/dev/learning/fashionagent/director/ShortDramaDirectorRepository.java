package dev.learning.fashionagent.director;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShortDramaDirectorRepository {
    void save(ShortDramaDirectorSnapshot snapshot);
    List<ShortDramaDirectorSnapshot> list();
    Optional<ShortDramaDirectorSnapshot> find(UUID id);
}
