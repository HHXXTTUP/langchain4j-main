package dev.learning.fashionagent.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.account.AccountService.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationInterceptor implements HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationInterceptor.class);
    private final AccountService accounts;
    private final ObjectMapper mapper;

    public AuthenticationInterceptor(AccountService accounts, ObjectMapper mapper) {
        this.accounts = accounts; this.mapper = mapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object id = request.getSession(false) == null ? null : request.getSession(false).getAttribute(AccountService.SESSION_ACCOUNT_ID);
        if (id == null) {
            if (request.getRequestURI().startsWith("/api/qwen-video-scripts")) {
                LOGGER.warn("千问视频脚本请求被鉴权拦截 uri={} method={}，当前会话不存在", request.getRequestURI(), request.getMethod());
            }
            return reject(request, response, 401, "请先登录");
        }
        Account account;
        try { account = accounts.require(id.toString()); }
        catch (RuntimeException e) { request.getSession(false).invalidate(); return reject(request, response, 401, "登录账号不存在"); }
        if (!account.enabled()) return reject(request, response, 403, "账号已停用");
        if (account.expired()) {
            request.getSession(false).invalidate();
            return reject(request, response, 403, "账号已过有效期，请联系管理员");
        }
        Set<String> requiredMenus = menusFor(request.getRequestURI());
        if (!requiredMenus.isEmpty() && requiredMenus.stream().noneMatch(account::allows)) {
            if (request.getRequestURI().startsWith("/api/qwen-video-scripts")) {
                LOGGER.warn("千问视频脚本请求被菜单权限拦截 uri={} accountId={}", request.getRequestURI(), account.id());
            }
            return reject(request, response, 403, "当前账号没有该功能权限");
        }
        request.setAttribute("currentAccount", account);
        AccountContext.set(accounts.snapshot(account));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AccountContext.clear();
    }

    private boolean reject(HttpServletRequest request, HttpServletResponse response, int status, String message) throws Exception {
        if (!request.getRequestURI().startsWith("/api/")) {
            response.sendRedirect("/login.html"); return false;
        }
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), Map.of("message", message, "loginRequired", status == 401));
        return false;
    }

    private static Set<String> menusFor(String path) {
        if (matches(path, "/api/generations")) return Set.of("workbench", "tasks");
        if (matches(path, "/api/video-generations")) return Set.of("tasks");
        if (matches(path, "/api/clothing-catalog", "/api/video-catalog", "/api/fashion-knowledge")) return Set.of("knowledge");
        if (matches(path, "/api/qwen-video-scripts")) return Set.of("video-script", "video-canvas");
        if (matches(path, "/api/short-drama-director")) return Set.of("short-drama-director");
        if (matches(path, "/api/my-scripts")) return Set.of("my-scripts", "script-replication");
        if (matches(path, "/api/video-bgm-compositions")) return Set.of("video-bgm", "video-canvas");
        if (matches(path, "/api/direct-outfit-replacements")) return Set.of("direct-outfit");
        if (matches(path, "/api/audit-redraw")) return Set.of("audit-redraw", "video-canvas");
        if (matches(path, "/api/system/logs")) return Set.of("logs");
        if (matches(path, "/api/comfyui-video-generations", "/api/comfyui-video-plans")) return Set.of("video-canvas");
        if (matches(path, "/api/story-video-replications")) return Set.of("dialogue-extraction");
        return Set.of();
    }

    private static boolean matches(String path, String... prefixes) {
        return List.of(prefixes).stream().anyMatch(path::startsWith);
    }
}
