package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorDetailResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorListResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorSearchResponse;
import dev.learning.stockanalyzer.data.SectorAnalysisModels.SectorSummary;
import dev.learning.stockanalyzer.data.SectorRankingLogModels.RankingLogResponse;
import dev.learning.stockanalyzer.service.SectorAnalysisService;
import dev.learning.stockanalyzer.service.SectorRankingLogService;
import dev.learning.stockanalyzer.service.SectorWatchlistService;
import dev.learning.stockanalyzer.service.PublicCleanModeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sectors")
public class SectorController {

    private final SectorAnalysisService sectorAnalysisService;
    private final SectorRankingLogService rankingLogService;
    private final SectorWatchlistService sectorWatchlistService;
    private final PublicCleanModeService publicCleanModeService;

    public SectorController(SectorAnalysisService sectorAnalysisService,
                            SectorRankingLogService rankingLogService,
                            SectorWatchlistService sectorWatchlistService,
                            PublicCleanModeService publicCleanModeService) {
        this.sectorAnalysisService = sectorAnalysisService;
        this.rankingLogService = rankingLogService;
        this.sectorWatchlistService = sectorWatchlistService;
        this.publicCleanModeService = publicCleanModeService;
    }

    @GetMapping
    public SectorListResponse list(@RequestParam(defaultValue = "false") boolean refresh) {
        requirePrivate();
        return sectorAnalysisService.list(refresh);
    }

    @GetMapping("/search")
    public SectorSearchResponse search(@RequestParam String keyword) {
        requirePrivate();
        return sectorAnalysisService.search(keyword);
    }

    @GetMapping("/stock/{fullCode}")
    public SectorDetailResponse stock(
            @PathVariable String fullCode,
            @RequestParam(defaultValue = "false") boolean refresh) {
        requirePrivate();
        return sectorAnalysisService.stock(fullCode, refresh);
    }

    @GetMapping("/stock-map/{fullCode}")
    public SectorSummary stockMap(@PathVariable String fullCode) {
        requirePrivate();
        return sectorAnalysisService.stockSector(fullCode);
    }

    @GetMapping("/watchlist")
    public List<SectorWatchlistService.SectorWatchlistItem> watchlist() {
        requirePrivate();
        return sectorWatchlistService.list();
    }

    @PostMapping("/watchlist")
    public SectorWatchlistService.SectorWatchlistItem addWatchlist(@RequestBody SectorWatchlistRequest request) {
        requirePrivate();
        return sectorWatchlistService.add(
                request.sectorId(), request.sectorName(), request.sectorType(), request.selectedCode());
    }

    @DeleteMapping("/watchlist/{sectorId}")
    public ResponseEntity<Void> removeWatchlist(@PathVariable String sectorId) {
        requirePrivate();
        sectorWatchlistService.remove(sectorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ranking-logs")
    public RankingLogResponse rankingLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String sectorId) {
        requirePrivate();
        return rankingLogService.logs(date, sectorId);
    }

    @GetMapping("/{sectorId}")
    public SectorDetailResponse detail(
            @PathVariable String sectorId,
            @RequestParam(required = false) String selected,
            @RequestParam(defaultValue = "false") boolean refresh) {
        requirePrivate();
        SectorDetailResponse response = sectorAnalysisService.detail(sectorId, selected, refresh);
        if (refresh && response.available() && response.sector() != null) {
            sectorWatchlistService.markRefreshed(response.sector().id());
        }
        return response;
    }

    private void requirePrivate() {
        publicCleanModeService.requirePrivateFeature("sector");
    }

    public record SectorWatchlistRequest(
            String sectorId,
            String sectorName,
            String sectorType,
            String selectedCode
    ) {
    }
}
