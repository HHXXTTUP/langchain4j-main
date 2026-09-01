package dev.learning.fashionagent.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogRefreshViewTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldExposeRunningStateToTheWebClient() throws Exception {
        CatalogRefreshView view = new CatalogRefreshView(
                "RUNNING", "正在分析服装图片", 1, 7, List.of(), Instant.parse("2026-08-05T00:00:00Z"));

        String json = objectMapper.writeValueAsString(view);

        assertThat(json).contains("\"running\":true");
    }
}
