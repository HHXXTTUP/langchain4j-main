package dev.learning.stockanalyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sector_watchlist")
public class SectorWatchlistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sector_id", nullable = false, unique = true, length = 80)
    private String sectorId;

    @Column(name = "sector_name", nullable = false, length = 80)
    private String sectorName;

    @Column(name = "sector_type", nullable = false, length = 40)
    private String sectorType;

    @Column(name = "selected_code", length = 10)
    private String selectedCode;

    @Column(name = "added_time", nullable = false)
    private LocalDateTime addedTime;

    @Column(name = "last_refreshed_at")
    private LocalDateTime lastRefreshedAt;

    protected SectorWatchlistEntity() {
    }

    public SectorWatchlistEntity(String sectorId, String sectorName, String sectorType,
                                 String selectedCode, LocalDateTime addedTime) {
        this.sectorId = sectorId;
        this.sectorName = sectorName;
        this.sectorType = sectorType;
        this.selectedCode = selectedCode;
        this.addedTime = addedTime;
    }

    public Long getId() { return id; }
    public String getSectorId() { return sectorId; }
    public String getSectorName() { return sectorName; }
    public String getSectorType() { return sectorType; }
    public String getSelectedCode() { return selectedCode; }
    public LocalDateTime getAddedTime() { return addedTime; }
    public LocalDateTime getLastRefreshedAt() { return lastRefreshedAt; }

    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
    public void setSectorType(String sectorType) { this.sectorType = sectorType; }
    public void setSelectedCode(String selectedCode) { this.selectedCode = selectedCode; }
    public void setLastRefreshedAt(LocalDateTime lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }
}
