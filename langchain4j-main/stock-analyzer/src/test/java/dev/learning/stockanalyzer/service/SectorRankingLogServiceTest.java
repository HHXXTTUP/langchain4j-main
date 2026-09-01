package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingLogResponse;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.StockRankingStatistics;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.entity.SectorRankingEntryEntity;
import dev.learning.stockanalyzer.entity.SectorRankingSnapshotEntity;
import dev.learning.stockanalyzer.repository.SectorRankingEntryRepository;
import dev.learning.stockanalyzer.repository.SectorRankingSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectorRankingLogServiceTest {

    private final SectorRankingSnapshotRepository snapshotRepository = mock(SectorRankingSnapshotRepository.class);
    private final SectorRankingEntryRepository entryRepository = mock(SectorRankingEntryRepository.class);
    private final StockDataService stockDataService = mock(StockDataService.class);
    private final SectorRankingLogService service = new SectorRankingLogService(
            snapshotRepository, entryRepository, stockDataService);

    @Test
    void shouldAggregatePodiumCountsAndChronologicalRankHistory() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        SectorRankingSnapshotEntity first = snapshot(1L, date.atTime(9, 35));
        SectorRankingSnapshotEntity second = snapshot(2L, date.atTime(10, 5));
        SectorRankingSnapshotEntity third = snapshot(3L, date.atTime(10, 35));
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(List.of(third, second, first));
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(List.of(
                        entry(third, 3),
                        entry(second, 1),
                        entry(first, 2)
                ));

        RankingLogResponse response = service.logs(date, null);
        StockRankingStatistics stock = response.sectors().get(0).stocks().get(0);

        assertThat(response.refreshCount()).isEqualTo(3);
        assertThat(stock.appearances()).isEqualTo(3);
        assertThat(stock.topThreeCount()).isEqualTo(3);
        assertThat(stock.firstCount()).isEqualTo(1);
        assertThat(stock.secondCount()).isEqualTo(1);
        assertThat(stock.thirdCount()).isEqualTo(1);
        assertThat(stock.rankHistory()).containsExactly(2, 1, 3);
        assertThat(stock.rankTimeHistory()).containsExactly("09.35", "10.05", "10.35");
        assertThat(stock.averageRank()).isEqualTo(2.0);
        assertThat(stock.latestRankingReason()).contains("第3名", "强度80.0分");
        assertThat(stock.rankingReasonHistory()).hasSize(3);
    }

    @Test
    void shouldKeepMissingStockAsEmptyPositionOnSectorTimeline() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        SectorRankingSnapshotEntity first = snapshot(1L, date.atTime(9, 35));
        SectorRankingSnapshotEntity second = snapshot(2L, date.atTime(9, 40));
        SectorRankingSnapshotEntity third = snapshot(3L, date.atTime(9, 45));
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(List.of(third, second, first));
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(List.of(
                        entry(third, 1),
                        entry(second, 1, "sh600000", "鍏朵粬鑲＄エ"),
                        entry(first, 3)
                ));

        StockRankingStatistics stock = service.logs(date, null).sectors().get(0).stocks().stream()
                .filter(item -> item.fullCode().equals("sh600584"))
                .findFirst()
                .orElseThrow();

        assertThat(stock.appearances()).isEqualTo(2);
        assertThat(stock.rankHistory()).containsExactly(3, null, 1);
        assertThat(stock.rankTimeHistory()).containsExactly("09.35", "09.40", "09.45");
    }

    @Test
    void shouldScoreSealedLimitUpWithoutMinuteReturnsBySealQualityAndBreakouts() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        SectorRankingSnapshotEntity snapshot = snapshot(1L, date.atTime(14, 26));
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(List.of(snapshot));
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(List.of(
                        limitEntry(snapshot, "sz000001", "clean", 800_000_000.0, 0, 95.0),
                        limitEntry(snapshot, "sz000002", "broken", 50_000_000.0, 3, 55.0),
                        entry(snapshot, 5, "sz000003", "normal")
                ));

        RankingLogResponse response = service.logs(date, null);
        double clean = totalScore(response, "sz000001");
        double broken = totalScore(response, "sz000002");
        double normal = totalScore(response, "sz000003");

        assertThat(clean).isGreaterThan(broken);
        assertThat(broken).isGreaterThan(normal);
    }

    @Test
    void shouldMarkBuyAndSellSignalsOnlyOnThresholdRefresh() {
        LocalDate date = LocalDate.of(2026, 8, 14);
        List<SectorRankingSnapshotEntity> chronological = IntStream.rangeClosed(1, 12)
                .mapToObj(index -> snapshot(index, date.atTime(9, 30).plusMinutes(index)))
                .toList();
        List<SectorRankingSnapshotEntity> descending = new ArrayList<>(chronological);
        Collections.reverse(descending);
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(descending);

        List<SectorRankingEntryEntity> entries = new ArrayList<>();
        chronological.forEach(snapshot -> IntStream.rangeClosed(1, 10).forEach(rank -> {
            if (rank == 2) {
                entries.add(entry(snapshot, rank, "sh600001", "前排测试股"));
            } else if (rank == 9) {
                entries.add(entry(snapshot, rank, "sh600009", "后排测试股"));
            } else {
                entries.add(entry(snapshot, rank, "sh6000" + rank, "测试股" + rank));
            }
        }));
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(entries);

        RankingLogResponse response = service.logs(date, null);

        assertThat(signal(response, 11L, "sh600001")).isEqualTo("B");
        assertThat(signal(response, 6L, "sh600009")).isEqualTo("S");
        assertThat(signal(response, 12L, "sh600001")).isNull();
        assertThat(signal(response, 7L, "sh600009")).isNull();
    }

    @Test
    void shouldExposePersistedRankingReasonForHistoricalHover() {
        LocalDate date = LocalDate.of(2026, 8, 19);
        SectorRankingSnapshotEntity snapshot = snapshot(1L, date.atTime(10, 15));
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(List.of(snapshot));
        SectorRankingEntryEntity entry = new SectorRankingEntryEntity(
                snapshot, 2, "sh600584", "长电科技", 82.5, 36.0, 4.2,
                0.3, 0.8, 1.2, false, null, 0, null,
                1.8, 250_000_000.0, 6.2, 0.7,
                "第2名，强度82.5分；1/3/5分 +0.30%/+0.80%/+1.20%；明显放量；主力净流入2.50亿元；领先板块均值0.70个百分点");
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(List.of(entry));

        RankingLogResponse response = service.logs(date, null);
        StockRankingStatistics stock = response.sectors().get(0).stocks().get(0);

        assertThat(stock.latestRankingReason()).contains("主力净流入2.50亿元", "领先板块均值");
        assertThat(stock.rankingReasonHistory()).containsExactly(stock.latestRankingReason());
        assertThat(response.snapshots().get(0).stocks().get(0).rankingReason())
                .isEqualTo(stock.latestRankingReason());
    }

    @Test
    void shouldOverrideTodaysLatestChangeWithLiveQuote() {
        LocalDate date = LocalDate.now();
        SectorRankingSnapshotEntity snapshot = snapshot(1L, date.atTime(10, 15));
        when(snapshotRepository.findByCapturedAtGreaterThanEqualAndCapturedAtLessThanOrderByCapturedAtDesc(any(), any()))
                .thenReturn(List.of(snapshot));
        when(entryRepository.findBySnapshotIdInOrderBySnapshotCapturedAtDescRankAsc(any()))
                .thenReturn(List.of(entry(snapshot, 2)));
        String quoteTime = date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "101530";
        when(stockDataService.getQuotes(any())).thenReturn(List.of(new StockQuote(
                "sh600584", "长电科技", 38.5, 36.0, 36.5, 38.8, 36.4,
                1_000_000, 300_000_000, 6.94, quoteTime)));

        StockRankingStatistics stock = service.logs(date, null).sectors().get(0).stocks().get(0);

        assertThat(stock.latestDailyChangePercent()).isEqualTo(6.94);
    }

    private SectorRankingSnapshotEntity snapshot(long id, LocalDateTime capturedAt) {
        SectorRankingSnapshotEntity snapshot = new SectorRankingSnapshotEntity(
                "gn_bdt", "半导体", capturedAt, capturedAt.toString(), 10);
        ReflectionTestUtils.setField(snapshot, "id", id);
        return snapshot;
    }

    private SectorRankingEntryEntity entry(SectorRankingSnapshotEntity snapshot, int rank) {
        return entry(snapshot, rank, "sh600584", "长电科技");
    }

    private SectorRankingEntryEntity entry(SectorRankingSnapshotEntity snapshot, int rank,
                                           String fullCode, String stockName) {
        return new SectorRankingEntryEntity(snapshot, rank, fullCode, stockName,
                80.0, 36.0, 2.0);
    }

    private SectorRankingEntryEntity limitEntry(SectorRankingSnapshotEntity snapshot, String fullCode,
                                                String stockName, double sealAmount,
                                                int breakoutCount, double limitQualityScore) {
        return new SectorRankingEntryEntity(snapshot, 5, fullCode, stockName,
                90.0, 36.0, 10.0, null, null, null, true,
                sealAmount, breakoutCount, limitQualityScore);
    }

    private double totalScore(RankingLogResponse response, String fullCode) {
        return response.sectors().get(0).stocks().stream()
                .filter(stock -> stock.fullCode().equals(fullCode))
                .findFirst()
                .orElseThrow()
                .totalScore();
    }

    private String signal(RankingLogResponse response, long snapshotId, String fullCode) {
        return response.snapshots().stream()
                .filter(snapshot -> snapshot.id() == snapshotId)
                .flatMap(snapshot -> snapshot.stocks().stream())
                .filter(stock -> stock.fullCode().equals(fullCode))
                .findFirst()
                .orElseThrow()
                .signal();
    }
}
