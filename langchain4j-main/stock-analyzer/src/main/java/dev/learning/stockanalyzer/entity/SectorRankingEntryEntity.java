package dev.learning.stockanalyzer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sector_ranking_entry",
        uniqueConstraints = @UniqueConstraint(name = "uk_sector_snapshot_rank", columnNames = {"snapshot_id", "rank_no"}),
        indexes = {
                @Index(name = "idx_sector_entry_snapshot", columnList = "snapshot_id"),
                @Index(name = "idx_sector_entry_stock", columnList = "full_code")
        })
public class SectorRankingEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private SectorRankingSnapshotEntity snapshot;

    @Column(name = "rank_no", nullable = false)
    private int rank;

    @Column(name = "full_code", nullable = false, length = 12)
    private String fullCode;

    @Column(name = "stock_name", nullable = false, length = 80)
    private String stockName;

    @Column(name = "score")
    private Double score;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(name = "daily_change_percent")
    private Double dailyChangePercent;

    @Column(name = "return_1m")
    private Double return1m;

    @Column(name = "return_3m")
    private Double return3m;

    @Column(name = "return_5m")
    private Double return5m;

    @Column(name = "limit_up")
    private Boolean limitUp;

    @Column(name = "seal_amount")
    private Double sealAmount;

    @Column(name = "breakout_count")
    private Integer breakoutCount;

    @Column(name = "limit_quality_score")
    private Double limitQualityScore;

    @Column(name = "volume_ratio")
    private Double volumeRatio;

    @Column(name = "main_net_inflow")
    private Double mainNetInflow;

    @Column(name = "main_net_ratio")
    private Double mainNetRatio;

    @Column(name = "relative_strength")
    private Double relativeStrength;

    @Column(name = "ranking_reason", length = 600)
    private String rankingReason;

    protected SectorRankingEntryEntity() {
    }

    public SectorRankingEntryEntity(SectorRankingSnapshotEntity snapshot, int rank, String fullCode,
                                    String stockName, Double score, Double currentPrice,
                                    Double dailyChangePercent) {
        this(snapshot, rank, fullCode, stockName, score, currentPrice, dailyChangePercent,
                null, null, null, false, null, null, null);
    }

    public SectorRankingEntryEntity(SectorRankingSnapshotEntity snapshot, int rank, String fullCode,
                                    String stockName, Double score, Double currentPrice,
                                    Double dailyChangePercent, Double return1m, Double return3m,
                                    Double return5m, Boolean limitUp, Double sealAmount,
                                    Integer breakoutCount, Double limitQualityScore) {
        this(snapshot, rank, fullCode, stockName, score, currentPrice, dailyChangePercent,
                return1m, return3m, return5m, limitUp, sealAmount, breakoutCount,
                limitQualityScore, null, null, null, null, null);
    }

    public SectorRankingEntryEntity(SectorRankingSnapshotEntity snapshot, int rank, String fullCode,
                                    String stockName, Double score, Double currentPrice,
                                    Double dailyChangePercent, Double return1m, Double return3m,
                                    Double return5m, Boolean limitUp, Double sealAmount,
                                    Integer breakoutCount, Double limitQualityScore, Double volumeRatio,
                                    Double mainNetInflow, Double mainNetRatio, Double relativeStrength,
                                    String rankingReason) {
        this.snapshot = snapshot;
        this.rank = rank;
        this.fullCode = fullCode;
        this.stockName = stockName;
        this.score = score;
        this.currentPrice = currentPrice;
        this.dailyChangePercent = dailyChangePercent;
        this.return1m = return1m;
        this.return3m = return3m;
        this.return5m = return5m;
        this.limitUp = limitUp;
        this.sealAmount = sealAmount;
        this.breakoutCount = breakoutCount;
        this.limitQualityScore = limitQualityScore;
        this.volumeRatio = volumeRatio;
        this.mainNetInflow = mainNetInflow;
        this.mainNetRatio = mainNetRatio;
        this.relativeStrength = relativeStrength;
        this.rankingReason = rankingReason;
    }

    public Long getId() { return id; }
    public SectorRankingSnapshotEntity getSnapshot() { return snapshot; }
    public int getRank() { return rank; }
    public String getFullCode() { return fullCode; }
    public String getStockName() { return stockName; }
    public Double getScore() { return score; }
    public Double getCurrentPrice() { return currentPrice; }
    public Double getDailyChangePercent() { return dailyChangePercent; }
    public Double getReturn1m() { return return1m; }
    public Double getReturn3m() { return return3m; }
    public Double getReturn5m() { return return5m; }
    public Boolean getLimitUp() { return limitUp; }
    public Double getSealAmount() { return sealAmount; }
    public Integer getBreakoutCount() { return breakoutCount; }
    public Double getLimitQualityScore() { return limitQualityScore; }
    public Double getVolumeRatio() { return volumeRatio; }
    public Double getMainNetInflow() { return mainNetInflow; }
    public Double getMainNetRatio() { return mainNetRatio; }
    public Double getRelativeStrength() { return relativeStrength; }
    public String getRankingReason() { return rankingReason; }
}
