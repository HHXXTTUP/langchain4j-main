package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.ai.WatchlistAnalysisResult;
import dev.learning.stockanalyzer.service.CapitalFlowService;
import dev.learning.stockanalyzer.service.DesktopTickerService;
import dev.learning.stockanalyzer.service.WatchlistService;
import dev.learning.stockanalyzer.service.PublicCleanModeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final CapitalFlowService capitalFlowService;
    private final DesktopTickerService desktopTickerService;
    private final PublicCleanModeService publicCleanModeService;

    public WatchlistController(WatchlistService watchlistService,
                               CapitalFlowService capitalFlowService,
                               DesktopTickerService desktopTickerService,
                               PublicCleanModeService publicCleanModeService) {
        this.watchlistService = watchlistService;
        this.capitalFlowService = capitalFlowService;
        this.desktopTickerService = desktopTickerService;
        this.publicCleanModeService = publicCleanModeService;
    }

    @GetMapping
    public List<WatchlistService.WatchlistItem> list() {
        return watchlistService.list();
    }

    @PostMapping
    public WatchlistService.WatchlistItem add(@RequestBody AddRequest request) {
        return watchlistService.add(request.code(), request.sourceCategory());
    }

    @DeleteMapping("/{fullCode}")
    public ResponseEntity<Void> remove(@PathVariable String fullCode) {
        watchlistService.remove(fullCode);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{fullCode}/source/{sourceCategory}")
    public ResponseEntity<WatchlistService.WatchlistItem> removeSource(
            @PathVariable String fullCode,
            @PathVariable String sourceCategory) {
        WatchlistService.WatchlistItem item = watchlistService.removeSource(fullCode, sourceCategory);
        return item == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(item);
    }

    @PostMapping("/{fullCode}/analyze")
    public WatchlistAnalysisResult analyze(@PathVariable String fullCode) {
        publicCleanModeService.requirePrivateFeature("watchlist-analysis");
        return watchlistService.analyze(fullCode);
    }

    @GetMapping("/{fullCode}/exists")
    public ExistsResponse exists(@PathVariable String fullCode) {
        return new ExistsResponse(watchlistService.contains(fullCode));
    }

    @GetMapping("/capital-flow")
    public CapitalFlowService.CapitalFlowResponse capitalFlow() {
        return capitalFlowService.getWatchlistCapitalFlows();
    }

    @PostMapping("/monitor/start")
    public DesktopTickerService.MonitorStatus startMonitor(@RequestBody MonitorRequest request) {
        return desktopTickerService.start(request.codes(), request.intervalSeconds());
    }

    @PostMapping("/monitor/stop")
    public DesktopTickerService.MonitorStatus stopMonitor() {
        return desktopTickerService.stop();
    }

    @GetMapping("/monitor/status")
    public DesktopTickerService.MonitorStatus monitorStatus() {
        return desktopTickerService.status();
    }

    public record AddRequest(String code, String sourceCategory) {
    }

    public record ExistsResponse(boolean exists) {
    }

    public record MonitorRequest(List<String> codes, Integer intervalSeconds) {
    }
}
