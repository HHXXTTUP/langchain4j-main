package dev.langchain4j.auditor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static dev.langchain4j.auditor.AuditModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReportWriterTest {
    @Test
    void writesHtmlAndMarkdownWithoutCredentials() throws Exception {
        Path directory = Files.createTempDirectory("ai-web-auditor-");
        AuditResult result = new AuditResult("audit-1", Instant.EPOCH.toString(), Instant.EPOCH.toString(),
                "https://example.test", List.of(new ModuleResult("Orders", "https://example.test/orders", "Orders",
                "evidence/orders.png", 12, List.of(new Finding("high", "accessibility", "Unnamed button", "Add a label")), null)), null, null);

        ReportWriter.ReportFiles files = new ReportWriter().write(result, directory);

        String markdown = Files.readString(Path.of(files.markdown()));
        String html = Files.readString(Path.of(files.html()));
        assertThat(markdown).contains("Unnamed button").doesNotContain("password");
        assertThat(html).contains("<table>").contains("Open screenshot evidence").contains("Add a label");
    }
}
