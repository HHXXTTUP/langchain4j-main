package dev.learning.fashionagent.web;

import dev.learning.fashionagent.account.AccountService;
import dev.learning.fashionagent.account.AccountService.Account;
import dev.learning.fashionagent.account.MenuCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AccountService accounts;
    public AuthenticationController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Account account = accounts.authenticate(request.username(), request.password());
        if (account == null) return ResponseEntity.status(401).body(Map.of("message", "用户名、密码错误，或账号已停用/过期"));
        HttpSession session = servletRequest.getSession(true);
        session.setMaxInactiveInterval((int) Duration.ofHours(12).toSeconds());
        session.setAttribute(AccountService.SESSION_ACCOUNT_ID, account.id());
        return ResponseEntity.ok(sessionView(account));
    }

    @GetMapping("/session")
    SessionView session(HttpServletRequest request) { return sessionView(current(request)); }

    @GetMapping("/authorize")
    ResponseEntity<?> authorize(@RequestParam String menu, HttpServletRequest request) {
        Account account = current(request);
        if (!MenuCatalog.ALL.contains(menu) || !account.allows(menu)) {
            return ResponseEntity.status(403).body(Map.of("message", "当前账号没有该菜单权限"));
        }
        return ResponseEntity.ok(Map.of("allowed", true, "menu", menu));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return ResponseEntity.noContent().build();
    }

    private Account current(HttpServletRequest request) {
        Object value = request.getSession(false).getAttribute(AccountService.SESSION_ACCOUNT_ID);
        return accounts.require(value.toString());
    }

    private SessionView sessionView(Account account) {
        String username = account.administrator() ? account.username() : mask(account.username());
        return new SessionView(account.id(), username, account.administrator(), account.expiresAt(),
                account.administrator() ? MenuCatalog.ALL : account.allowedMenus(), MenuCatalog.options());
    }

    private static String mask(String value) {
        if (value == null || value.length() < 3) return "***";
        return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
    }

    public record LoginRequest(String username, String password) {}
    public record SessionView(String id, String username, boolean administrator, java.time.Instant expiresAt,
                              java.util.Set<String> allowedMenus, List<MenuCatalog.MenuOption> menuOptions) {}
}
