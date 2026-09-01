package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.service.PublicCleanModeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeConfigController {

    private final PublicCleanModeService modeService;

    public RuntimeConfigController(PublicCleanModeService modeService) {
        this.modeService = modeService;
    }

    @GetMapping
    public Map<String, Object> config() {
        return Map.of("publicClean", modeService.enabled());
    }
}
