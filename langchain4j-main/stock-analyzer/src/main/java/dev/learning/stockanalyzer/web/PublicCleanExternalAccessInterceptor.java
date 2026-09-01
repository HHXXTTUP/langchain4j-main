package dev.learning.stockanalyzer.web;

import dev.learning.stockanalyzer.service.MonitorLicenseService;
import dev.learning.stockanalyzer.service.PublicCleanModeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PublicCleanExternalAccessInterceptor implements HandlerInterceptor {

    private final PublicCleanModeService publicCleanModeService;
    private final MonitorLicenseService monitorLicenseService;

    public PublicCleanExternalAccessInterceptor(PublicCleanModeService publicCleanModeService,
                                                MonitorLicenseService monitorLicenseService) {
        this.publicCleanModeService = publicCleanModeService;
        this.monitorLicenseService = monitorLicenseService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (publicCleanModeService.enabled()
                && requiresExternalAccess(request.getMethod(), request.getRequestURI())) {
            monitorLicenseService.assertMonitorAvailable();
        }
        return true;
    }

    static boolean requiresExternalAccess(String method, String path) {
        if (path == null || !path.startsWith("/api/")) return false;
        if ("/api/runtime".equals(path)) return false;
        if (path.startsWith("/api/watchlist/monitor/status")
                || path.startsWith("/api/watchlist/monitor/stop")) {
            return false;
        }
        if (path.startsWith("/api/watchlist/") && path.endsWith("/exists")) return false;
        if ("DELETE".equalsIgnoreCase(method) && path.startsWith("/api/watchlist/")) return false;
        return true;
    }
}
