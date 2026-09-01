package dev.langchain4j.auditor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaywrightAuditRunnerTest {
    @Test
    void resolvesRelativeUrlsWithoutDoubleSlashes() {
        assertThat(PlaywrightAuditRunner.resolveUrl("https://example.com/", "/orders"))
                .isEqualTo("https://example.com/orders");
        assertThat(PlaywrightAuditRunner.resolveUrl("https://example.com", "https://other.test/a"))
                .isEqualTo("https://other.test/a");
    }

    @Test
    void createsStableSafeEvidenceNames() {
        assertThat(PlaywrightAuditRunner.slug("订单列表 / Mobile")).isEqualTo("mobile");
        assertThat(PlaywrightAuditRunner.slug("  ")).isEqualTo("module");
    }
}
