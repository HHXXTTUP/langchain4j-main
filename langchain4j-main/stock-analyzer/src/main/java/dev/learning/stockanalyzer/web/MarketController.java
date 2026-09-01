package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.ai.MarketDailyResult;
import dev.learning.stockanalyzer.service.MarketDailyService;
import dev.learning.stockanalyzer.service.PublicCleanModeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDailyService marketDailyService;
    private final PublicCleanModeService publicCleanModeService;

    public MarketController(MarketDailyService marketDailyService, PublicCleanModeService publicCleanModeService) {
        this.marketDailyService = marketDailyService;
        this.publicCleanModeService = publicCleanModeService;
    }

    @PostMapping("/daily-picks")
    public MarketDailyResult dailyPicks() {
        publicCleanModeService.requirePrivateFeature("daily-picks");
        return marketDailyService.getDailyPicks();
    }
}
