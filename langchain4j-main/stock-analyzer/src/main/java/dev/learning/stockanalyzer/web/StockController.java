package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.ai.PeerRecommendation;
import dev.learning.stockanalyzer.ai.StockDetailResult;
import dev.learning.stockanalyzer.ai.StockScoreResult;
import dev.learning.stockanalyzer.data.StockInfo;
import dev.learning.stockanalyzer.data.StockFundamentalsSnapshot;
import dev.learning.stockanalyzer.data.StockQuote;
import dev.learning.stockanalyzer.data.StockSearchService;
import dev.learning.stockanalyzer.service.StockFundamentalsService;
import dev.learning.stockanalyzer.service.StockScoringService;
import dev.learning.stockanalyzer.service.StockInsightService;
import dev.learning.stockanalyzer.service.StockOverviewService;
import dev.learning.stockanalyzer.service.PublicCleanModeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockSearchService searchService;
    private final StockScoringService scoringService;
    private final StockOverviewService overviewService;
    private final StockInsightService insightService;
    private final StockFundamentalsService fundamentalsService;
    private final PublicCleanModeService publicCleanModeService;

    public StockController(StockSearchService searchService,
                           StockScoringService scoringService,
                           StockOverviewService overviewService,
                           StockInsightService insightService,
                           StockFundamentalsService fundamentalsService,
                           PublicCleanModeService publicCleanModeService) {
        this.searchService = searchService;
        this.scoringService = scoringService;
        this.overviewService = overviewService;
        this.insightService = insightService;
        this.fundamentalsService = fundamentalsService;
        this.publicCleanModeService = publicCleanModeService;
    }

    @GetMapping("/search")
    public List<StockInfo> search(@RequestParam String keyword) {
        List<StockInfo> results = searchService.search(keyword);
        if (!results.isEmpty() || keyword == null || !keyword.trim().matches("(?i)(sh|sz)?\\d{6}")) {
            return results;
        }
        try {
            return List.of(overviewService.getOverview(keyword).stock());
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    @GetMapping("/{fullCode}/overview")
    public StockOverviewService.StockOverview overview(@PathVariable String fullCode) {
        return overviewService.getOverview(fullCode);
    }

    @GetMapping("/{fullCode}/fundamentals")
    public StockFundamentalsSnapshot fundamentals(
            @PathVariable String fullCode,
            @RequestParam(defaultValue = "false") boolean refresh) {
        StockFundamentalsSnapshot snapshot = fundamentalsService.get(fullCode, refresh);
        if (!publicCleanModeService.enabled() || snapshot.researchReports() == null) return snapshot;
        List<StockFundamentalsSnapshot.ResearchReport> publicReports = snapshot.researchReports().stream()
                .map(report -> new StockFundamentalsSnapshot.ResearchReport(
                        report.title(), null, report.institution(), report.date(), report.industry(),
                        report.pdfUrl(), report.epsForecasts(), report.peForecasts()))
                .toList();
        return new StockFundamentalsSnapshot(
                snapshot.fullCode(), snapshot.available(), snapshot.profile(), snapshot.performance(),
                snapshot.valuation(), publicReports, snapshot.industryPosition(), snapshot.concepts(),
                snapshot.sources(), snapshot.warnings(), snapshot.fetchedAt());
    }

    @PostMapping("/insight")
    public StockInsightService.InsightResponse insight(@RequestBody InsightRequest request) {
        publicCleanModeService.requirePrivateFeature("insight");
        return insightService.analyze(request.code(), request.focus(), request.question());
    }

    @PostMapping("/score")
    public ScoreResponse score(@RequestBody ScoreRequest request) {
        publicCleanModeService.requirePrivateFeature("score");
        StockScoringService.ScoreResponse result = scoringService.scoreByKeyword(request.keyword());
        return new ScoreResponse(result.stock(), result.quote(), result.score());
    }

    @PostMapping("/peers")
    public PeerRecommendation peers(@RequestBody PeerRequest request) {
        publicCleanModeService.requirePrivateFeature("peers");
        return scoringService.recommendPeers(request.code());
    }

    @PostMapping("/detail")
    public DetailResponse detail(@RequestBody DetailRequest request) {
        publicCleanModeService.requirePrivateFeature("detail");
        StockScoringService.DetailResponse result = scoringService.getStockDetail(request.code());
        return new DetailResponse(result.stock(), result.quote(), result.detail());
    }

    public record ScoreRequest(String keyword) {
    }

    public record PeerRequest(String code) {
    }

    public record DetailRequest(String code) {
    }

    public record InsightRequest(String code, String focus, String question) {
    }

    public record ScoreResponse(StockInfo stock, StockQuote quote, StockScoreResult score) {
    }

    public record DetailResponse(StockInfo stock, StockQuote quote, StockDetailResult detail) {
    }
}
