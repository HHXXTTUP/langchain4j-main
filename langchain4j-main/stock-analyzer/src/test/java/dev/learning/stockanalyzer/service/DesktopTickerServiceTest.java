package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.data.StockDataService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DesktopTickerServiceTest {

    @Test
    void shouldValidateRefreshInterval() {
        assertThat(DesktopTickerService.normalizeIntervalSeconds(null)).isEqualTo(5);
        assertThat(DesktopTickerService.normalizeIntervalSeconds(1)).isEqualTo(1);
        assertThat(DesktopTickerService.normalizeIntervalSeconds(300)).isEqualTo(300);
        assertThatThrownBy(() -> DesktopTickerService.normalizeIntervalSeconds(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DesktopTickerService.normalizeIntervalSeconds(301))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCheckMonitorEntitlementBeforeStartingDesktopUi() {
        MonitorLicenseService licenseService = new MonitorLicenseService("");
        DesktopTickerService service = new DesktopTickerService(mock(StockDataService.class), licenseService);

        assertThatThrownBy(() -> service.start(java.util.List.of("sh600000"), 5))
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessage(MonitorLicenseService.UNAVAILABLE_MESSAGE);
    }
}
