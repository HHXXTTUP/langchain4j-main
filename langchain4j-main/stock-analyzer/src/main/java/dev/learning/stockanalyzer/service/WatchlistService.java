package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.ai.WatchlistAnalysisAgent;
import dev.learning.stockanalyzer.ai.WatchlistAnalysisResult;
import dev.learning.stockanalyzer.data.StockDataService;
import dev.learning.stockanalyzer.data.StockCodeUtils;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import dev.learning.stockanalyzer.entity.WatchlistEntity;
import dev.learning.stockanalyzer.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);
    public static final String MANUAL_SOURCE = "MANUAL";
    public static final String RANKING_TRACE_SOURCE = "RANKING_TRACE";
    public static final String BOTH_SOURCE = "MANUAL_AND_RANKING_TRACE";

    private final WatchlistRepository watchlistRepository;
    private final StockDataService stockDataService;
    private final StockSearchService stockSearchService;
    private final WatchlistAnalysisAgent analysisAgent;

    public WatchlistService(WatchlistRepository watchlistRepository,
                            StockDataService stockDataService,
                            StockSearchService stockSearchService,
                            WatchlistAnalysisAgent analysisAgent) {
        this.watchlistRepository = watchlistRepository;
        this.stockDataService = stockDataService;
        this.stockSearchService = stockSearchService;
        this.analysisAgent = analysisAgent;
    }

    public List<WatchlistItem> list() {
        List<WatchlistEntity> entities = watchlistRepository.findAllByOrderByAddedTimeDesc();
        if (entities.isEmpty()) return List.of();

        List<String> codes = entities.stream()
                .map(WatchlistEntity::getFullCode)
                .collect(Collectors.toList());

        List<StockQuote> quotes = stockDataService.getQuotes(codes);
        log.debug("自选股列表：共{}只，行情返回{}条", entities.size(), quotes.size());

        Map<String, StockQuote> quoteByCode = quotes.stream()
                .collect(Collectors.toMap(StockQuote::code, Function.identity(), (left, right) -> left));

        return entities.stream().map(entity -> {
            StockInfo info = resolveInfo(entity);
            StockQuote quote = quoteByCode.get(entity.getFullCode());
            return toItem(entity, info, quote);
        }).collect(Collectors.toList());
    }

    @Transactional
    public WatchlistItem add(String fullCode) {
        return add(fullCode, MANUAL_SOURCE);
    }

    @Transactional
    public WatchlistItem add(String fullCode, String sourceCategory) {
        String normalizedCode = StockCodeUtils.normalizeFullCode(fullCode);
        String requestedSource = normalizeSource(sourceCategory);

        if (watchlistRepository.existsByFullCode(normalizedCode)) {
            StockQuote quote = stockDataService.getQuote(normalizedCode);
            WatchlistEntity entity = watchlistRepository.findByFullCode(normalizedCode)
                    .orElseThrow(() -> new IllegalArgumentException("未找到自选股: " + normalizedCode));
            String mergedSource = mergeSource(entity.getSourceCategory(), requestedSource);
            if (!mergedSource.equals(entity.getSourceCategory())) {
                entity.setSourceCategory(mergedSource);
                watchlistRepository.save(entity);
            }
            return toItem(entity, resolveInfo(entity), quote);
        }

        StockQuote quote = stockDataService.getQuote(normalizedCode);
        StockInfo info = stockSearchService.findByCode(normalizedCode).orElse(null);

        String code = normalizedCode.substring(2);
        String market = normalizedCode.substring(0, 2);
        String name = info != null ? info.name() : (quote != null ? quote.name() : "");
        String industry = info != null ? info.industry() : "其他";

        WatchlistEntity entity = new WatchlistEntity(normalizedCode, code, market, name, industry);
        entity.setSourceCategory(requestedSource);
        watchlistRepository.save(entity);
        log.info("添加自选股: {} {} 来源={}", normalizedCode, name, requestedSource);

        if (info == null) {
            info = new StockInfo(code, market, name, industry);
        }
        return toItem(entity, info, quote);
    }

    @Transactional
    public void remove(String fullCode) {
        watchlistRepository.deleteByFullCode(fullCode);
        log.info("移除自选股: {}", fullCode);
    }

    /**
     * Removes only a source classification. A manually added watchlist item is
     * preserved when its ranking-trace classification is toggled off.
     */
    @Transactional
    public WatchlistItem removeSource(String fullCode, String sourceCategory) {
        String normalizedCode = StockCodeUtils.normalizeFullCode(fullCode);
        WatchlistEntity entity = watchlistRepository.findByFullCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("未找到自选股: " + normalizedCode));
        String requestedSource = normalizeSource(sourceCategory);
        String existingSource = normalizeSource(entity.getSourceCategory());
        if (!RANKING_TRACE_SOURCE.equals(requestedSource)) {
            return toItem(entity, resolveInfo(entity), stockDataService.getQuote(normalizedCode));
        }
        if (BOTH_SOURCE.equals(existingSource)) {
            entity.setSourceCategory(MANUAL_SOURCE);
            watchlistRepository.save(entity);
            return toItem(entity, resolveInfo(entity), stockDataService.getQuote(normalizedCode));
        }
        if (RANKING_TRACE_SOURCE.equals(existingSource)) {
            watchlistRepository.delete(entity);
            log.info("移除排名轨迹自选股: {}", normalizedCode);
            return null;
        }
        return toItem(entity, resolveInfo(entity), stockDataService.getQuote(normalizedCode));
    }

    public WatchlistAnalysisResult analyze(String fullCode) {
        WatchlistEntity entity = watchlistRepository.findByFullCode(fullCode)
                .orElseThrow(() -> new IllegalArgumentException("未找到自选股: " + fullCode));

        StockInfo info = new StockInfo(entity.getCode(), entity.getMarket(),
                entity.getName(), entity.getIndustry());
        StockQuote quote = stockDataService.getQuote(fullCode);

        String context = buildAnalysisContext(info, quote);
        log.debug("AI自选股分析请求: {}", info.name());
        return analysisAgent.analyze(context);
    }

    public boolean contains(String fullCode) {
        return watchlistRepository.existsByFullCode(StockCodeUtils.normalizeFullCode(fullCode));
    }

    public List<String> getAllFullCodes() {
        return watchlistRepository.findAllByOrderByAddedTimeDesc().stream()
                .map(WatchlistEntity::getFullCode)
                .collect(Collectors.toList());
    }

    public List<WatchlistEntity> getAllEntities() {
        return watchlistRepository.findAllByOrderByAddedTimeDesc();
    }

    private String buildAnalysisContext(StockInfo info, StockQuote quote) {
        return """
                分析目标：%s（%s），行业：%s
                实时行情：
                - 当前价格：%.2f元
                - 昨收：%.2f元
                - 涨跌幅：%+.2f%%
                - 成交量：%d手
                - 成交额：%.2f万元
                - 今日最高：%.2f元
                - 今日最低：%.2f元
                - 数据时间：%s

                请给出短期合理估值区间和资金流向预估。
                """.formatted(info.name(), info.fullCode(), info.industry(),
                quote.currentPrice(), quote.yesterdayClose(),
                quote.changePercent(), quote.volume() / 100,
                quote.turnover(),
                quote.highPrice(), quote.lowPrice(), quote.dateTime());
    }

    private StockInfo resolveInfo(WatchlistEntity entity) {
        StockInfo catalogInfo = stockSearchService.findByCode(entity.getFullCode()).orElse(null);
        if (catalogInfo == null) {
            return new StockInfo(entity.getCode(), entity.getMarket(),
                    entity.getName(), entity.getIndustry());
        }
        String industry = "其他".equals(catalogInfo.industry()) && entity.getIndustry() != null
                ? entity.getIndustry()
                : catalogInfo.industry();
        return new StockInfo(catalogInfo.code(), catalogInfo.market(), catalogInfo.name(), industry);
    }

    private WatchlistItem toItem(WatchlistEntity entity, StockInfo info, StockQuote quote) {
        return new WatchlistItem(info, quote, quote != null, entity.getAddedTime(),
                normalizeSource(entity.getSourceCategory()));
    }

    private String normalizeSource(String sourceCategory) {
        if (BOTH_SOURCE.equalsIgnoreCase(sourceCategory)) return BOTH_SOURCE;
        return RANKING_TRACE_SOURCE.equalsIgnoreCase(sourceCategory)
                ? RANKING_TRACE_SOURCE : MANUAL_SOURCE;
    }

    private String mergeSource(String existing, String requested) {
        if (BOTH_SOURCE.equals(existing) || BOTH_SOURCE.equals(requested)) return BOTH_SOURCE;
        if (existing == null || existing.isBlank()) return requested;
        if (existing.equals(requested)) return existing;
        return BOTH_SOURCE;
    }

    public record WatchlistItem(
            StockInfo stock,
            StockQuote quote,
            boolean quoteAvailable,
            LocalDateTime addedTime,
            String sourceCategory
    ) {
    }
}
