package dev.learning.fashionagent.learning;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record CatalogRefreshView(
        String status,
        String message,
        int processed,
        int total,
        List<String> errors,
        Instant updatedAt) {

    public CatalogRefreshView {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    @JsonProperty("running")
    public boolean running() {
        return "RUNNING".equals(status);
    }
}
