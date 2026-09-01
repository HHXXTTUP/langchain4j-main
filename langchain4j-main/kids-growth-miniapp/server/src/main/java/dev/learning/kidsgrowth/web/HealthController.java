package dev.learning.kidsgrowth.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/", "/health"})
    Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "kids-growth-learning-server");
    }
}
