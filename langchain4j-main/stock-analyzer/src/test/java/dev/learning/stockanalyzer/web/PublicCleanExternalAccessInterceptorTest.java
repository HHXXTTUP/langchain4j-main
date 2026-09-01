package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.config.PublicCleanProperties;
import dev.learning.stockanalyzer.service.FeatureUnavailableException;
import dev.learning.stockanalyzer.service.MonitorLicenseService;
import dev.learning.stockanalyzer.service.PublicCleanModeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicCleanExternalAccessInterceptorTest {

    private static final String VALID_UNTIL_2026_09_01 =
            "v2.iRYNWOeR53sXo4cKqRNscoWxcfuND_3MLbzlNVSph-93bO7ZrT-E_Pu6AcPthQ.5ZlilfvkSS3b-FlSlpLKF0LF83mo0NZ_3SCEHkV0uDdWQQKZM43hMAuEHP4y65g9IT_OhcivxH0_DdHbUTwDAQ";

    @Test
    void shouldRejectExternalDataRequestsInExpiredPublicCleanMode() {
        PublicCleanExternalAccessInterceptor interceptor = interceptor(true, "");
        HttpServletRequest request = request("GET", "/api/stock/search");

        assertThatThrownBy(() -> interceptor.preHandle(
                request, mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessage("通道积分已耗尽，暂无法提供服务");
    }

    @Test
    void shouldNotRestrictLocalFullMode() {
        PublicCleanExternalAccessInterceptor interceptor = interceptor(false, "");

        assertThat(interceptor.preHandle(
                request("GET", "/api/stock/search"),
                mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void shouldAllowExternalDataRequestsBeforePublicCleanExpiry() {
        PublicCleanExternalAccessInterceptor interceptor = interceptor(
                true, VALID_UNTIL_2026_09_01);

        assertThat(interceptor.preHandle(
                request("GET", "/api/stock/search"),
                mock(HttpServletResponse.class), new Object())).isTrue();
    }

    @Test
    void shouldAllowApiCallsThatDoNotAccessExternalData() {
        PublicCleanExternalAccessInterceptor interceptor = interceptor(true, "");

        assertThat(interceptor.preHandle(
                request("GET", "/api/runtime"),
                mock(HttpServletResponse.class), new Object())).isTrue();
        assertThat(interceptor.preHandle(
                request("POST", "/api/watchlist/monitor/stop"),
                mock(HttpServletResponse.class), new Object())).isTrue();
        assertThat(interceptor.preHandle(
                request("DELETE", "/api/watchlist/sh600519"),
                mock(HttpServletResponse.class), new Object())).isTrue();
    }

    private PublicCleanExternalAccessInterceptor interceptor(boolean publicClean, String token) {
        PublicCleanProperties properties = new PublicCleanProperties();
        properties.setEnabled(publicClean);
        return new PublicCleanExternalAccessInterceptor(
                new PublicCleanModeService(properties), new MonitorLicenseService(token));
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }
}
