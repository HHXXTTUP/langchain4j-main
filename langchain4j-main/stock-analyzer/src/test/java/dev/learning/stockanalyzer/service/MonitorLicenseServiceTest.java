package dev.learning.stockanalyzer.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorLicenseServiceTest {

    private static final String VALID_UNTIL_2026_08_12 =
            "v2.7bLmwaySSZQ9nA0RIg81zxnURmyFvBQ5_nh4vgXk9G33XULuD9gEldiYp-t7mw.HGlV9cg6BMpGmkds7MSkeOFMIwTuq97KFy-a1o1V22CpvBJQt2YDlW77WoTIN845cZdgy7egQh8gviO70tJ2DA";

    @Test
    void shouldAllowThroughExpiryDateAndRejectAfterIt() {
        MonitorLicenseService service = new MonitorLicenseService(VALID_UNTIL_2026_08_12);

        assertThat(MonitorLicenseService.decryptExpiry(VALID_UNTIL_2026_08_12))
                .isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(service.isMonitorAvailable(LocalDate.of(2026, 8, 12))).isTrue();
        assertThat(service.isMonitorAvailable(LocalDate.of(2026, 8, 13))).isFalse();
    }

    @Test
    void shouldRejectMissingOrModifiedTokenWithGenericMessage() {
        MonitorLicenseService missing = new MonitorLicenseService("");
        MonitorLicenseService modified = new MonitorLicenseService(VALID_UNTIL_2026_08_12 + "x");

        assertThat(missing.isMonitorAvailable(LocalDate.of(2026, 1, 1))).isFalse();
        assertThat(modified.isMonitorAvailable(LocalDate.of(2026, 1, 1))).isFalse();
        assertThatThrownBy(missing::assertMonitorAvailable)
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessage("通道积分已耗尽，暂无法提供服务");
    }

    @Test
    void shouldTolerateAnExtraEqualsSignFromConfigPasting() {
        MonitorLicenseService service = new MonitorLicenseService("=" + VALID_UNTIL_2026_08_12);

        assertThat(service.isMonitorAvailable(LocalDate.of(2026, 8, 12))).isTrue();
    }
}
