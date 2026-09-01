package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.entity.SectorWatchlistEntity;
import dev.learning.stockanalyzer.repository.SectorWatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SectorWatchlistService {

    private final SectorWatchlistRepository repository;

    public SectorWatchlistService(SectorWatchlistRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SectorWatchlistItem> list() {
        return repository.findAllByOrderByAddedTimeAsc().stream().map(this::toItem).toList();
    }

    @Transactional
    public SectorWatchlistItem add(String sectorId, String sectorName, String sectorType, String selectedCode) {
        String normalizedId = requireText(sectorId, "板块ID不能为空", 80);
        String normalizedName = requireText(sectorName, "板块名称不能为空", 80);
        String normalizedType = optionalText(sectorType, "板块", 40);
        String normalizedSelectedCode = optionalText(selectedCode, null, 10);
        SectorWatchlistEntity entity = repository.findBySectorId(normalizedId)
                .orElseGet(() -> new SectorWatchlistEntity(
                        normalizedId, normalizedName, normalizedType, normalizedSelectedCode, LocalDateTime.now()));
        entity.setSectorName(normalizedName);
        entity.setSectorType(normalizedType);
        entity.setSelectedCode(normalizedSelectedCode);
        return toItem(repository.save(entity));
    }

    @Transactional
    public void remove(String sectorId) {
        repository.deleteBySectorId(sectorId);
    }

    @Transactional
    public void markRefreshed(String sectorId) {
        if (sectorId == null || sectorId.isBlank()) return;
        repository.findBySectorId(sectorId).ifPresent(entity -> {
            entity.setLastRefreshedAt(LocalDateTime.now());
            repository.save(entity);
        });
    }

    private SectorWatchlistItem toItem(SectorWatchlistEntity entity) {
        return new SectorWatchlistItem(entity.getSectorId(), entity.getSectorName(), entity.getSectorType(),
                entity.getSelectedCode(), entity.getAddedTime(), entity.getLastRefreshedAt());
    }

    private String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String optionalText(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    public record SectorWatchlistItem(
            String sectorId,
            String sectorName,
            String sectorType,
            String selectedCode,
            LocalDateTime addedTime,
            LocalDateTime lastRefreshedAt
    ) {
    }
}
