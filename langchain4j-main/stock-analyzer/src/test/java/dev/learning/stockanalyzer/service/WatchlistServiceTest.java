package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.ai.WatchlistAnalysisAgent;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import dev.learning.stockanalyzer.entity.WatchlistEntity;
import dev.learning.stockanalyzer.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private final WatchlistRepository repository = mock(WatchlistRepository.class);
    private final StockDataService dataService = mock(StockDataService.class);
    private final StockSearchService searchService = mock(StockSearchService.class);
    private final WatchlistAnalysisAgent analysisAgent = mock(WatchlistAnalysisAgent.class);
    private final WatchlistService service = new WatchlistService(
            repository, dataService, searchService, analysisAgent);

    @Test
    void shouldEnrichStoredMetadataAndExposeQuoteStatus() {
        WatchlistEntity entity = new WatchlistEntity("sh600519", "600519", "sh", "贵州茅台", "其他");
        StockQuote quote = new StockQuote("sh600519", "贵州茅台", 1500, 1480, 1490,
                1510, 1475, 2_000_000, 3_000_000_000d, 1.35, "20260811150000");
        when(repository.findAllByOrderByAddedTimeDesc()).thenReturn(List.of(entity));
        when(dataService.getQuotes(List.of("sh600519"))).thenReturn(List.of(quote));
        when(searchService.findByCode("sh600519"))
                .thenReturn(Optional.of(new StockInfo("600519", "sh", "贵州茅台", "白酒")));

        WatchlistService.WatchlistItem item = service.list().get(0);

        assertThat(item.stock().industry()).isEqualTo("白酒");
        assertThat(item.quoteAvailable()).isTrue();
        assertThat(item.quote()).isSameAs(quote);
    }

    @Test
    void shouldKeepWatchlistItemWhenQuoteIsUnavailable() {
        WatchlistEntity entity = new WatchlistEntity("sz000858", "000858", "sz", "五粮液", "白酒");
        when(repository.findAllByOrderByAddedTimeDesc()).thenReturn(List.of(entity));
        when(dataService.getQuotes(List.of("sz000858"))).thenReturn(List.of());
        when(searchService.findByCode("sz000858")).thenReturn(Optional.empty());

        WatchlistService.WatchlistItem item = service.list().get(0);

        assertThat(item.quoteAvailable()).isFalse();
        assertThat(item.quote()).isNull();
        assertThat(item.stock().name()).isEqualTo("五粮液");
    }

    @Test
    void shouldKeepBothSourcesWhenRankingTraceAddsAnExistingManualStock() {
        WatchlistEntity entity = new WatchlistEntity("sz002747", "002747", "sz", "埃斯顿", "机器人");
        StockQuote quote = new StockQuote("sz002747", "埃斯顿", 36.12, 34.63, 35.00,
                36.62, 33.90, 970_399, 343_862_000d, 4.30, "20260811150000");
        when(repository.existsByFullCode("sz002747")).thenReturn(true);
        when(repository.findByFullCode("sz002747")).thenReturn(Optional.of(entity));
        when(dataService.getQuote("sz002747")).thenReturn(quote);
        when(searchService.findByCode("sz002747")).thenReturn(Optional.empty());

        WatchlistService.WatchlistItem item = service.add("sz002747", WatchlistService.RANKING_TRACE_SOURCE);

        assertThat(item.sourceCategory()).isEqualTo(WatchlistService.BOTH_SOURCE);
        assertThat(entity.getSourceCategory()).isEqualTo(WatchlistService.BOTH_SOURCE);
    }
}
