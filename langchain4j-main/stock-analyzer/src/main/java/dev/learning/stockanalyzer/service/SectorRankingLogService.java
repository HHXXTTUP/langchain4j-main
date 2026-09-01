package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.SectorAnalysisModels.IntradayStrength;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorDetailResponse;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingEntry;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingLogResponse;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingSnapshot;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.SectorRankingStatistics;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.StockRankingStatistics;
import dev.learning.stockanalyzer.entity.SectorRankingEntryEntity;
import dev.learning.stockanalyzer.entity.SectorRankingSnapshotEntity;
import dev.learning.stockanalyzer.repository.SectorRankingEntryRepository;
import dev.learning.stockanalyzer.repository.SectorRankingSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SectorRankingLogService {

    private static final DateTimeFormatter RANK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH.mm");
    private static final DateTimeFormatter QUOTE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final SectorRankingSnapshotRepository snapshotRepository;
    private final SectorRankingEntryRepository entryRepository;
    private final StockDataService stockDataService;

    public SectorRankingLogService(SectorRankingSnapshotRepository snapshotRepository,
                                   SectorRankingEntryRepository entryRepository,
                                   StockDataService stockDataService) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
        this.stockDataService = stockDataService;
    }

    @Transactional
    public void record(SectorDetailResponse response) {
        if (response == null || !response.available() || response.sector() == null
                || response.stocks() == null || response.stocks().isEmpty()) {
            return;
        }
        List<IntradayStrength> topTen = response.stocks().stream()
                .filter(stock -> stock.fullCode() != null && stock.name() != null)
                .sorted(Comparator.comparingInt(IntradayStrength::rank))
                .limit(10)
                .toList();
        if (topTen.isEmpty()) return;
        Map<String, StockQuote> liveQuotes = loadLiveQuotes(topTen.stream()
                .map(IntradayStrength::fullCode)
                .toList());

        SectorRankingSnapshotEntity snapshot = snapshotRepository.save(new SectorRankingSnapshotEntity(
                response.sector().id(),
                response.sector().name(),
                LocalDateTime.now(),
                response.fetchedAt(),
                topTen.size()
        ));
        List<SectorRankingEntryEntity> entries = new ArrayList<>(topTen.size());
        for (int index = 0; index < topTen.size(); index++) {
            IntradayStrength stock = topTen.get(index);
            StockQuote liveQuote = liveQuotes.get(stock.fullCode());
            int rank = stock.rank() > 0 ? stock.rank() : index + 1;
            entries.add(new SectorRankingEntryEntity(
                    snapshot, rank, stock.fullCode(), stock.name(), stock.score(),
                    liveQuote == null ? stock.currentPrice() : liveQuote.currentPrice(),
                    liveQuote == null ? stock.dailyChangePercent() : liveQuote.changePercent(), stock.return1m(),
                    stock.return3m(), stock.return5m(), stock.limitUp(), stock.sealAmount(),
                    stock.breakoutCount(), stock.limitQualityScore(), stock.volumeRatio(),
                    stock.mainNetInflow(), stock.mainNetRatio(), stock.relativeStrength(),
                    stock.rankingReason()));
        }
        entryRepository.saveAll(entries);
    }

    @Transactional(readOnly = true)
    public RankingLogResponse logs(LocalDate date, String sectorId) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();
        List<SectorRankingSnapshotEntity> snapshots = sectorId == null || sectorId.isBlank()
                ? snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(start, end)
                : snapshotRepository.findBySectorIdAndCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(
                        sectorId, start, end);
        if (snapshots.isEmpty()) return new RankingLogResponse(targetDate, 0, List.of(), List.of());

        List<Long> snapshotIds = snapshots.stream().map(SectorRankingSnapshotEntity::getId).toList();
        List<SectorRankingEntryEntity> entries = entryRepository
                .findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(snapshotIds);
        Map<Long, List<SectorRankingEntryEntity>> entriesBySnapshot = new HashMap<>();
        entries.forEach(entry -> entriesBySnapshot
                .computeIfAbsent(entry.getSnapshot().getId(), ignored -> new ArrayList<>())
                .add(entry));
        Map<String, StockQuote> liveQuotes = targetDate.equals(LocalDate.now())
                ? loadLiveQuotes(entries.stream().map(SectorRankingEntryEntity::getFullCode).distinct().toList())
                : Map.of();

        Map<Long, Map<String, String>> signalsBySnapshot = calculateSignals(snapshots, entriesBySnapshot);
        List<RankingSnapshot> snapshotResults = snapshots.stream()
                .map(snapshot -> toSnapshot(
                        snapshot,
                        entriesBySnapshot.getOrDefault(snapshot.getId(), List.of()),
                        signalsBySnapshot.getOrDefault(snapshot.getId(), Map.of())))
                .toList();
        Map<String, List<SectorRankingSnapshotEntity>> bySector = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> bySector
                .computeIfAbsent(snapshot.getSectorId(), ignored -> new ArrayList<>())
                .add(snapshot));
        List<SectorRankingStatistics> statistics = bySector.values().stream()
                .map(group -> aggregateSector(group, entriesBySnapshot, liveQuotes))
                .sorted(Comparator.comparingInt(SectorRankingStatistics::refreshCount).reversed()
                        .thenComparing(SectorRankingStatistics::sectorName))
                .toList();
        return new RankingLogResponse(targetDate, snapshots.size(), statistics, snapshotResults);
    }

    private RankingSnapshot toSnapshot(SectorRankingSnapshotEntity snapshot,
                                       List<SectorRankingEntryEntity> entries,
                                       Map<String, String> signals) {
        List<RankingEntry> stocks = entries.stream()
                .sorted(Comparator.comparingInt(SectorRankingEntryEntity::getRank))
                .map(entry -> new RankingEntry(
                        entry.getRank(), entry.getFullCode(), entry.getStockName(), entry.getScore(),
                        entry.getCurrentPrice(), entry.getDailyChangePercent(), entry.getReturn1m(),
                        entry.getReturn3m(), entry.getReturn5m(), entry.getLimitUp(), entry.getSealAmount(),
                        entry.getBreakoutCount(), entry.getLimitQualityScore(), entry.getVolumeRatio(),
                        entry.getMainNetInflow(), entry.getMainNetRatio(), entry.getRelativeStrength(),
                        rankReason(entry),
                        signals.get(entry.getFullCode())))
                .toList();
        return new RankingSnapshot(snapshot.getId(), snapshot.getSectorId(), snapshot.getSectorName(),
                snapshot.getCapturedAt(), snapshot.getSourceFetchedAt(), stocks);
    }

    private Map<Long, Map<String, String>> calculateSignals(
            List<SectorRankingSnapshotEntity> snapshots,
            Map<Long, List<SectorRankingEntryEntity>> entriesBySnapshot) {
        Map<Long, Map<String, String>> result = new HashMap<>();
        Map<String, List<SectorRankingSnapshotEntity>> snapshotsBySector = snapshots.stream()
                .collect(Collectors.groupingBy(SectorRankingSnapshotEntity::getSectorId));

        snapshotsBySector.values().forEach(sectorSnapshots -> {
            Map<String, SignalStreak> streaks = new HashMap<>();
            sectorSnapshots.stream()
                    .sorted(Comparator.comparing(SectorRankingSnapshotEntity::getCapturedAt))
                    .forEach(snapshot -> {
                        List<SectorRankingEntryEntity> currentEntries = entriesBySnapshot
                                .getOrDefault(snapshot.getId(), List.of());
                        Set<String> presentCodes = currentEntries.stream()
                                .map(SectorRankingEntryEntity::getFullCode)
                                .collect(Collectors.toSet());
                        streaks.forEach((code, streak) -> {
                            if (!presentCodes.contains(code)) streak.reset();
                        });

                        int bottomRankStart = Math.max(1, currentEntries.size() - 2);
                        Map<String, String> snapshotSignals = new HashMap<>();
                        currentEntries.forEach(entry -> {
                            SignalStreak streak = streaks.computeIfAbsent(
                                    entry.getFullCode(), ignored -> new SignalStreak());
                            if (entry.getRank() <= 3) {
                                streak.topThreeCount++;
                                streak.bottomThreeCount = 0;
                                if (streak.topThreeCount == 11) snapshotSignals.put(entry.getFullCode(), "B");
                            } else if (entry.getRank() >= bottomRankStart) {
                                streak.bottomThreeCount++;
                                streak.topThreeCount = 0;
                                if (streak.bottomThreeCount == 6) snapshotSignals.put(entry.getFullCode(), "S");
                            } else {
                                streak.reset();
                            }
                        });
                        if (!snapshotSignals.isEmpty()) result.put(snapshot.getId(), snapshotSignals);
                    });
        });
        return result;
    }

    private SectorRankingStatistics aggregateSector(
            List<SectorRankingSnapshotEntity> snapshots,
            Map<Long, List<SectorRankingEntryEntity>> entriesBySnapshot,
            Map<String, StockQuote> liveQuotes) {
        Map<String, MutableStockStatistics> byStock = new LinkedHashMap<>();
        List<SectorRankingSnapshotEntity> orderedSnapshots = snapshots.stream()
                .sorted(Comparator.comparing(SectorRankingSnapshotEntity::getCapturedAt))
                .toList();
        orderedSnapshots.forEach(snapshot -> entriesBySnapshot.getOrDefault(snapshot.getId(), List.of()).forEach(entry ->
                byStock.computeIfAbsent(entry.getFullCode(), ignored ->
                        new MutableStockStatistics(entry.getFullCode(), entry.getStockName()))));
        orderedSnapshots.forEach(snapshot -> {
            Map<String, SectorRankingEntryEntity> entries = entriesBySnapshot
                    .getOrDefault(snapshot.getId(), List.of()).stream()
                    .collect(Collectors.toMap(SectorRankingEntryEntity::getFullCode, entry -> entry, (left, right) -> left));
            String time = snapshot.getCapturedAt().format(RANK_TIME_FORMAT);
            byStock.values().forEach(stock -> stock.addAt(entries.get(stock.fullCode), time));
        });
        List<StockRankingStatistics> stocks = byStock.values().stream()
                .map(stock -> stock.toResult(liveQuotes.get(stock.fullCode)))
                .sorted(Comparator.comparingDouble(StockRankingStatistics::totalScore).reversed()
                        .thenComparing(Comparator.comparingInt(StockRankingStatistics::topThreeCount).reversed())
                        .thenComparing(StockRankingStatistics::stockName))
                .toList();
        SectorRankingSnapshotEntity first = snapshots.get(0);
        return new SectorRankingStatistics(first.getSectorId(), first.getSectorName(), snapshots.size(), stocks);
    }

    private static final class MutableStockStatistics {
        private final String fullCode;
        private final String stockName;
        private final List<Integer> ranks = new ArrayList<>();
        private final List<Integer> rankTimeline = new ArrayList<>();
        private final List<String> rankTimeTimeline = new ArrayList<>();
        private final List<String> rankingReasonTimeline = new ArrayList<>();
        private double totalScore;
        private Double latestReturn1m;
        private Double latestReturn3m;
        private Double latestReturn5m;
        private Double latestDailyChangePercent;
        private boolean latestLimitUp;
        private String latestRankingReason;

        private MutableStockStatistics(String fullCode, String stockName) {
            this.fullCode = fullCode;
            this.stockName = stockName;
        }

        private void addAt(SectorRankingEntryEntity entry, String time) {
            rankTimeTimeline.add(time);
            if (entry == null) {
                rankTimeline.add(null);
                rankingReasonTimeline.add(null);
                return;
            }
            int rank = entry.getRank();
            ranks.add(rank);
            rankTimeline.add(rank);
            totalScore += rankingContribution(entry);
            latestReturn1m = entry.getReturn1m();
            latestReturn3m = entry.getReturn3m();
            latestReturn5m = entry.getReturn5m();
            latestDailyChangePercent = entry.getDailyChangePercent();
            latestLimitUp = Boolean.TRUE.equals(entry.getLimitUp());
            latestRankingReason = rankReason(entry);
            rankingReasonTimeline.add(latestRankingReason);
        }

        private double rankingContribution(SectorRankingEntryEntity entry) {
            double rankPoints = Math.max(1, 11 - entry.getRank());
            if (Boolean.TRUE.equals(entry.getLimitUp())) {
                return rankPoints + limitUpPoints(entry);
            }
            double momentumPoints = positive(entry.getReturn1m()) * 0.35
                    + positive(entry.getReturn3m()) * 0.25
                    + positive(entry.getReturn5m()) * 0.20;
            return rankPoints + Math.min(3.0, momentumPoints);
        }

        private double limitUpPoints(SectorRankingEntryEntity entry) {
            double quality = entry.getLimitQualityScore() == null ? 50 : entry.getLimitQualityScore();
            double sealAmountPoints = entry.getSealAmount() == null
                    ? 0
                    : Math.min(3.0, Math.max(0, entry.getSealAmount()) / 200_000_000.0);
            Integer breakoutCount = entry.getBreakoutCount();
            double breakoutPoints = breakoutCount == null
                    ? -1.0
                    : breakoutCount == 0 ? 3.0 : breakoutCount == 1 ? 0 : -Math.min(6.0, (breakoutCount - 1) * 2.0);
            return Math.max(8.0, 6.0 + quality / 12.5 + sealAmountPoints + breakoutPoints);
        }

        private double positive(Double value) {
            return value == null ? 0 : Math.max(0, Math.min(5, value));
        }

        private StockRankingStatistics toResult(StockQuote liveQuote) {
            int first = (int) ranks.stream().filter(rank -> rank == 1).count();
            int second = (int) ranks.stream().filter(rank -> rank == 2).count();
            int third = (int) ranks.stream().filter(rank -> rank == 3).count();
            int topThree = (int) ranks.stream().filter(rank -> rank <= 3).count();
            int best = ranks.stream().mapToInt(Integer::intValue).min().orElse(0);
            double average = ranks.stream().mapToInt(Integer::intValue).average().orElse(0);
            return new StockRankingStatistics(fullCode, stockName, ranks.size(), topThree,
                    first, second, third, best, Math.round(average * 100.0) / 100.0, new ArrayList<>(rankTimeline),
                    Math.round(totalScore * 100.0) / 100.0, latestReturn1m, latestReturn3m,
                    latestReturn5m,
                    liveQuote == null ? latestDailyChangePercent : liveQuote.changePercent(),
                    latestLimitUp, new ArrayList<>(rankTimeTimeline),
                    latestRankingReason, new ArrayList<>(rankingReasonTimeline));
        }
    }

    private Map<String, StockQuote> loadLiveQuotes(List<String> fullCodes) {
        if (fullCodes == null || fullCodes.isEmpty()) return Map.of();
        List<String> distinctCodes = fullCodes.stream().distinct().toList();
        List<StockQuote> quotes = new ArrayList<>();
        for (int start = 0; start < distinctCodes.size(); start += 80) {
            List<StockQuote> batch = stockDataService.getQuotes(
                    distinctCodes.subList(start, Math.min(start + 80, distinctCodes.size())));
            if (batch != null) quotes.addAll(batch);
        }
        if (quotes.isEmpty()) return Map.of();
        String today = LocalDate.now().format(QUOTE_DATE_FORMAT);
        return quotes.stream()
                .filter(quote -> quote.currentPrice() > 0)
                .filter(quote -> isQuoteFromToday(quote.dateTime(), today))
                .collect(Collectors.toMap(
                StockQuote::code, quote -> quote, (left, right) -> left));
    }

    private boolean isQuoteFromToday(String dateTime, String basicToday) {
        if (dateTime == null || dateTime.isBlank()) return false;
        String compact = dateTime.replaceAll("[^0-9]", "");
        return compact.startsWith(basicToday);
    }

    private static String rankReason(SectorRankingEntryEntity entry) {
        if (entry.getRankingReason() != null && !entry.getRankingReason().isBlank()) {
            return entry.getRankingReason();
        }
        List<String> reasons = new ArrayList<>();
        reasons.add("第" + entry.getRank() + "名"
                + (entry.getScore() == null ? "" : "，强度" + String.format("%.1f", entry.getScore()) + "分"));
        if (Boolean.TRUE.equals(entry.getLimitUp())) {
            reasons.add("涨停封板");
            if (entry.getLimitQualityScore() != null) {
                reasons.add("封板质量" + String.format("%.1f", entry.getLimitQualityScore()));
            }
            if (entry.getBreakoutCount() != null) {
                reasons.add(entry.getBreakoutCount() == 0 ? "零炸板" : "炸板" + entry.getBreakoutCount() + "次");
            }
        } else if (entry.getReturn1m() != null || entry.getReturn3m() != null || entry.getReturn5m() != null) {
            reasons.add("1/3/5分 " + percent(entry.getReturn1m()) + "/"
                    + percent(entry.getReturn3m()) + "/" + percent(entry.getReturn5m()));
        }
        if (entry.getVolumeRatio() != null) {
            reasons.add("5分钟量比" + String.format("%.2f", entry.getVolumeRatio()));
        }
        if (entry.getMainNetInflow() != null) {
            reasons.add("主力" + (entry.getMainNetInflow() >= 0 ? "净流入" : "净流出")
                    + amount(entry.getMainNetInflow()));
        }
        if (entry.getRelativeStrength() != null) {
            reasons.add((entry.getRelativeStrength() >= 0 ? "领先" : "落后") + "板块均值"
                    + String.format("%.2f", Math.abs(entry.getRelativeStrength())) + "个百分点");
        }
        return String.join("；", reasons);
    }

    private static String percent(Double value) {
        return value == null ? "--" : String.format("%+.2f%%", value);
    }

    private static String amount(Double value) {
        double absolute = Math.abs(value);
        if (absolute >= 100_000_000) return String.format("%.2f亿元", absolute / 100_000_000);
        if (absolute >= 10_000) return String.format("%.1f万元", absolute / 10_000);
        return String.format("%.0f元", absolute);
    }

    private static final class SignalStreak {
        private int topThreeCount;
        private int bottomThreeCount;

        private void reset() {
            topThreeCount = 0;
            bottomThreeCount = 0;
        }
    }
}
