package dev.learning.stockanalyzer.repository;

import dev.learning.stockanalyzer.entity.SectorWatchlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectorWatchlistRepository extends JpaRepository<SectorWatchlistEntity, Long> {

    Optional<SectorWatchlistEntity> findBySectorId(String sectorId);

    List<SectorWatchlistEntity> findAllByOrderByAddedTimeAsc();

    void deleteBySectorId(String sectorId);
}
