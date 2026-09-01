package dev.langchain4j.auditor;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ScreenshotType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.langchain4j.auditor.AuditModels.*;

final class PlaywrightAuditRunner implements AuditRunner {
    @Override
    public List<ModuleResult> run(String auditId, AuditRequest request, Path evidenceDirectory) throws Exception {
        Files.createDirectories(evidenceDirectory);
        List<ModuleResult> results = new ArrayList<>();
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(request.headless == null || request.headless))) {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setViewportSize(request.viewportWidth == null ? 1440 : request.viewportWidth,
                            request.viewportHeight == null ? 900 : request.viewportHeight);
            try (BrowserContext context = browser.newContext(contextOptions)) {
                Page page = context.newPage();
                List<String> consoleErrors = new ArrayList<>();
                page.onConsoleMessage(message -> {
                    if ("error".equalsIgnoreCase(message.type())) {
                        consoleErrors.add(message.text());
                    }
                });
                Exception loginFailure = null;
                try {
                    login(page, request.baseUrl, request.login);
                } catch (Exception e) {
                    // Keep a report when credentials or selectors are wrong; this is useful evidence too.
                    loginFailure = e;
                }
                for (ModuleConfig module : request.modules) {
                    if (loginFailure != null) {
                        results.add(new ModuleResult(module.name, resolveUrl(request.baseUrl, module.path), "",
                                evidenceReference(module), 0,
                                List.of(), "Login failed: " + loginFailure.getMessage()));
                    } else {
                        results.add(auditModule(page, request.baseUrl, module, evidenceDirectory, consoleErrors));
                    }
                    consoleErrors.clear();
                }
            }
        }
        return results;
    }

    private void login(Page page, String baseUrl, LoginConfig login) {
        if (login == null || login.username == null || login.password == null) {
            return;
        }
        page.navigate(resolveUrl(baseUrl, login.path));
        page.locator(login.usernameSelector).first().fill(login.username);
        page.locator(login.passwordSelector).first().fill(login.password);
        page.locator(login.submitSelector).first().click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        if (login.successUrlContains != null && !page.url().contains(login.successUrlContains)) {
            throw new IllegalStateException("Login did not reach the expected URL: " + login.successUrlContains);
        }
    }

    private ModuleResult auditModule(Page page, String baseUrl, ModuleConfig module, Path evidenceDirectory,
                                     List<String> consoleErrors) {
        long started = System.nanoTime();
        String url = resolveUrl(baseUrl, module.path);
        Path screenshotPath = evidenceDirectory.resolve(slug(module.name) + ".png");
        String screenshot = evidenceReference(module);
        try {
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true)
                    .setType(ScreenshotType.PNG));
            List<Finding> findings = inspect(page, consoleErrors);
            return new ModuleResult(module.name, page.url(), page.title(), screenshot,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), findings, null);
        } catch (Exception e) {
            return new ModuleResult(module.name, url, "", screenshot,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), List.of(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Finding> inspect(Page page, List<String> consoleErrors) {
        List<Finding> findings = new ArrayList<>();
        String title = page.title();
        if (title == null || title.isBlank()) {
            findings.add(new Finding("medium", "content", "页面缺少有效的 title。",
                    "为页面提供简短、唯一且能描述当前模块的标题。"));
        }
        Object values = page.evaluate("() => ({overflow: document.documentElement.scrollWidth > window.innerWidth, "
                + "missingAlt: [...document.images].filter(i => !i.alt).length, "
                + "unnamedButtons: [...document.querySelectorAll('button,[role=button]')].filter(b => !(b.innerText || b.getAttribute('aria-label') || b.getAttribute('title'))).length})");
        if (values instanceof java.util.Map<?, ?> metrics) {
            if (Boolean.TRUE.equals(metrics.get("overflow"))) {
                findings.add(new Finding("high", "responsive", "页面存在横向溢出。",
                        "检查固定宽度元素、表格和弹窗在当前视口下的响应式布局。"));
            }
            if (number(metrics.get("missingAlt")) > 0) {
                findings.add(new Finding("medium", "accessibility", "存在缺少 alt 文本的图片。",
                        "为有信息量的图片添加准确 alt；装饰图使用空 alt。"));
            }
            if (number(metrics.get("unnamedButtons")) > 0) {
                findings.add(new Finding("high", "accessibility", "存在没有可访问名称的按钮。",
                        "为按钮添加可见文本或 aria-label，并确保键盘可操作。"));
            }
        }
        for (String error : consoleErrors.stream().limit(10).toList()) {
            findings.add(new Finding("high", "runtime", "浏览器控制台错误：" + error,
                    "定位并修复该页面加载或交互触发的 JavaScript 错误。"));
        }
        return findings;
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    static String resolveUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) return baseUrl;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return baseUrl.replaceAll("/$", "") + "/" + path.replaceAll("^/", "");
    }

    static String slug(String value) {
        String result = value == null ? "module" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return result.isBlank() ? "module" : result;
    }

    private static String evidenceReference(ModuleConfig module) {
        return "evidence/" + slug(module.name) + ".png";
    }
}
