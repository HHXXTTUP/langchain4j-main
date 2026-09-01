package dev.learning.fashionagent.web;

import dev.learning.fashionagent.account.AccountService;
import dev.learning.fashionagent.account.AccountService.Account;
import dev.learning.fashionagent.account.AccountService.AccountView;
import dev.learning.fashionagent.account.AccountService.CreateAccount;
import dev.learning.fashionagent.account.AccountService.UpdateAccount;
import dev.learning.fashionagent.account.ApplicationPackageService;
import dev.learning.fashionagent.account.MenuCatalog;
import dev.learning.fashionagent.account.NativeDirectoryPicker;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accounts;
    private final ApplicationPackageService packages;
    private final NativeDirectoryPicker directoryPicker;
    public AccountController(AccountService accounts, ApplicationPackageService packages, NativeDirectoryPicker directoryPicker) {
        this.accounts = accounts;
        this.packages = packages;
        this.directoryPicker = directoryPicker;
    }

    @GetMapping
    List<AccountView> list(HttpServletRequest request) { return accounts.list(current(request)); }

    @GetMapping("/menus")
    List<MenuCatalog.MenuOption> menus() { return MenuCatalog.options(); }

    @PostMapping
    AccountView create(@RequestBody CreateAccount body, HttpServletRequest request) { requireAdmin(request); return accounts.create(body); }

    @PutMapping("/{id}")
    AccountView update(@PathVariable String id, @RequestBody UpdateAccount body, HttpServletRequest request) {
        requireAdmin(request); return accounts.updateAsAdmin(id, body);
    }

    @PutMapping("/me/settings")
    AccountView updateSelf(@RequestBody Map<String, String> body, HttpServletRequest request) {
        return accounts.updateSelf(current(request).id(), body);
    }

    @PostMapping("/select-directory")
    ResponseEntity<?> selectDirectory(@RequestBody(required = false) DirectoryRequest body) {
        String selected = directoryPicker.choose(body == null ? null : body.initialPath());
        return selected == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(Map.of("path", selected));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        Account operator = requireAdmin(request); accounts.delete(id, operator); return ResponseEntity.noContent().build();
    }

    @PostMapping("/package")
    ResponseEntity<byte[]> buildPackage(HttpServletRequest request) {
        requireAdmin(request); var result = packages.build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.name() + "\"")
                .body(result.bytes());
    }

    private Account requireAdmin(HttpServletRequest request) {
        Account account = current(request);
        if (!account.administrator()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可执行此操作");
        return account;
    }
    private Account current(HttpServletRequest request) { return (Account) request.getAttribute("currentAccount"); }
    record DirectoryRequest(String initialPath) {}
}
