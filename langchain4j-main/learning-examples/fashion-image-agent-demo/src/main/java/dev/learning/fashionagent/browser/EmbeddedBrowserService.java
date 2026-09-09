package dev.learning.fashionagent.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.ServerSocket;
import org.springframework.stereotype.Service;

/** Embedded browser bridge based on Chrome DevTools Protocol. */
@Service
public class EmbeddedBrowserService implements AutoCloseable {
    private final VideoBrowserProfileService profiles;
    private final VideoBrowserProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public EmbeddedBrowserService(VideoBrowserProfileService profiles, VideoBrowserProperties properties) { this.profiles = profiles; this.properties = properties; }

    public synchronized SessionInfo open(UUID id) {
        VideoBrowserProfileView profile = profiles.get(id);
        Session current = sessions.get(id);
        if (current != null && current.process.isAlive()) return current.info(profile);
        closeQuietly(id);
        Path profileDirectory = Path.of(profile.profileDirectory()).toAbsolutePath().normalize();
        try { Files.createDirectories(profileDirectory); } catch (IOException e) { throw new IllegalStateException("无法创建浏览器 Profile 目录", e); }
        Path executable = resolveBrowser(profile.browserExecutable());
        int port = freePort();
        // Use a real headed Chrome renderer. Google blocks OAuth in headless or
        // automation-looking webviews; keeping the window off-screen preserves
        // the embedded UI while allowing normal browser sign-in flows.
        List<String> command = new ArrayList<>(List.of(executable.toString(), "--window-size=1280,820", "--window-position=-32000,-32000", "--start-minimized", "--disable-blink-features=AutomationControlled", "--disable-backgrounding-occluded-windows", "--disable-renderer-backgrounding", "--disable-features=CalculateNativeWinOcclusion", "--hide-scrollbars", "--force-dark-mode", "--enable-features=WebContentsForceDark", "--remote-debugging-address=127.0.0.1", "--remote-debugging-port=" + port, "--user-data-dir=" + profileDirectory, "--no-first-run", "--no-default-browser-check"));
        String configuredExtension = profile.extensionDirectory();
        if (configuredExtension == null || configuredExtension.isBlank()) configuredExtension = properties.getExtensionDirectory();
        if (configuredExtension != null && !configuredExtension.isBlank()) {
            Path extension = Path.of(configuredExtension).toAbsolutePath().normalize();
            if (!Files.isDirectory(extension) || !Files.isRegularFile(extension.resolve("manifest.json"))) throw new IllegalArgumentException("插件目录无效，必须包含 manifest.json: " + extension);
            command.add("--disable-extensions-except=" + extension); command.add("--load-extension=" + extension);
        }
        command.add(profile.startUrl());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String wsUrl = waitForWebSocketUrl(port);
            Listener listener = new Listener();
            WebSocket socket = http.newWebSocketBuilder().buildAsync(URI.create(wsUrl), listener).join();
            Session session = new Session(process, socket);
            listener.session = session;
            sessions.put(id, session);
            session.command("Page.enable", Map.of()); session.command("Runtime.enable", Map.of());
            session.command("Page.setDeviceMetricsOverride", Map.of("width", 1280, "height", 820, "deviceScaleFactor", 1, "mobile", false));
            String hideScrollbars = "(() => { const style = document.createElement('style'); style.textContent = 'html,body,*{scrollbar-width:none!important;}::-webkit-scrollbar{display:none!important;width:0!important;height:0!important;}'; (document.head || document.documentElement).appendChild(style); })()";
            session.command("Page.addScriptToEvaluateOnNewDocument", Map.of("source", hideScrollbars));
            session.command("Runtime.evaluate", Map.of("expression", hideScrollbars));
            hideTaskbarWindow(process);
            return session.info(profile);
        } catch (Exception e) { throw new IllegalStateException("无法启动嵌入式 Chrome，请检查浏览器路径或关闭占用的 Chrome Profile", e); }
    }

    private void hideTaskbarWindow(Process process) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        String command = "$ErrorActionPreference='SilentlyContinue'; Add-Type @'\nusing System; using System.Runtime.InteropServices; public static class Win32 { [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow); }\n'@; for($i=0;$i -lt 20;$i++){ $p=Get-Process -Id " + process.pid() + "; if($p.MainWindowHandle -ne 0){ [Win32]::ShowWindow($p.MainWindowHandle,0); break }; Start-Sleep -Milliseconds 100 }";
        try { new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", command).start(); } catch (Exception ignored) { }
    }

    public SessionInfo info(UUID id) { Session session = sessions.get(id); return session != null && session.process.isAlive() ? session.info(profiles.get(id)) : stopped(profiles.get(id)); }
    public byte[] screenshot(UUID id) { JsonNode result = require(id).command("Page.captureScreenshot", Map.of("format", "jpeg", "quality", 72, "fromSurface", true)); return Base64.getDecoder().decode(result.path("data").asText()); }
    public void click(UUID id, double x, double y) { Session s = require(id); s.command("Input.dispatchMouseEvent", Map.of("type", "mousePressed", "x", x, "y", y, "button", "left", "clickCount", 1)); s.command("Input.dispatchMouseEvent", Map.of("type", "mouseReleased", "x", x, "y", y, "button", "left", "clickCount", 1)); }
    public void type(UUID id, String text) { if (text != null && !text.isEmpty()) require(id).command("Input.insertText", Map.of("text", text)); }
    public String selectedText(UUID id) {
        String expression = "(() => { const el = document.activeElement; if (el && typeof el.selectionStart === 'number' && typeof el.selectionEnd === 'number') return String(el.value || '').slice(el.selectionStart, el.selectionEnd); return String(window.getSelection ? window.getSelection() : ''); })()";
        JsonNode result = require(id).command("Runtime.evaluate", Map.of("expression", expression, "returnByValue", true));
        return result.path("result").path("value").asText("");
    }
    public void press(UUID id, String key, String code) { press(id, key, code, 0); }
    public void press(UUID id, String key, String code, int modifiers) {
        if (key != null && !key.isBlank()) {
            Session s = require(id);
            Map<String, Object> down = new java.util.HashMap<>();
            down.put("type", "keyDown");
            down.put("key", key);
            down.put("code", code == null || code.isBlank() ? key : code);
            Integer virtualKey = virtualKeyCode(key);
            if (virtualKey != null) {
                down.put("windowsVirtualKeyCode", virtualKey);
                down.put("nativeVirtualKeyCode", virtualKey);
            }
            if (modifiers != 0) down.put("modifiers", modifiers);
            s.command("Input.dispatchKeyEvent", down);
            down.put("type", "keyUp");
            s.command("Input.dispatchKeyEvent", down);
        }
    }
    private static Integer virtualKeyCode(String key) {
        return switch (key) {
            case "Backspace" -> 8; case "Tab" -> 9; case "Enter" -> 13; case "Escape" -> 27;
            case "Space" -> 32; case "PageUp" -> 33; case "PageDown" -> 34; case "End" -> 35; case "Home" -> 36;
            case "ArrowLeft" -> 37; case "ArrowUp" -> 38; case "ArrowRight" -> 39; case "ArrowDown" -> 40;
            case "Insert" -> 45; case "Delete" -> 46; case "Control" -> 17; case "Shift" -> 16;
            case "Alt" -> 18; case "Meta" -> 91; default -> key.length() == 1 ? (int) Character.toUpperCase(key.charAt(0)) : null;
        };
    }
    public void scroll(UUID id, double x, double y, double deltaY) { require(id).command("Input.dispatchMouseEvent", Map.of("type", "mouseWheel", "x", x, "y", y, "deltaY", deltaY, "deltaX", 0)); }
    public synchronized SessionInfo close(UUID id) { closeQuietly(id); return stopped(profiles.get(id)); }
    private void closeQuietly(UUID id) { Session session = sessions.remove(id); if (session != null) { try { session.socket.sendClose(WebSocket.NORMAL_CLOSURE, "close").join(); } catch (Exception ignored) {} session.process.destroy(); } }
    private Session require(UUID id) { Session session = sessions.get(id); if (session == null || !session.process.isAlive()) throw new IllegalStateException("该账号尚未打开嵌入式浏览器"); return session; }

    private String waitForWebSocketUrl(int port) {
        for (int i = 0; i < 80; i++) {
            try {
                String json = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
                JsonNode list = mapper.readTree(json); for (JsonNode item : list) if (item.path("type").asText().equals("page")) return item.path("webSocketDebuggerUrl").asText();
            } catch (Exception ignored) {}
            try { Thread.sleep(125); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        throw new IllegalStateException("Chrome DevTools 端口未就绪");
    }
    private Path resolveBrowser(String configured) { List<String> candidates = new ArrayList<>(); if (configured != null && !configured.isBlank()) candidates.add(configured); String local = System.getenv("LOCALAPPDATA"), pf = System.getenv("PROGRAMFILES"), pf86 = System.getenv("PROGRAMFILES(X86)"); if (local != null) { candidates.add(local + "\\Google\\Chrome\\Application\\chrome.exe"); candidates.add(local + "\\Microsoft\\Edge\\Application\\msedge.exe"); } if (pf != null) candidates.add(pf + "\\Google\\Chrome\\Application\\chrome.exe"); if (pf86 != null) candidates.add(pf86 + "\\Google\\Chrome\\Application\\chrome.exe"); return candidates.stream().map(Path::of).map(Path::toAbsolutePath).filter(Files::isRegularFile).findFirst().orElseThrow(() -> new IllegalStateException("未找到 Chrome/Edge，请填写浏览器可执行文件路径")); }
    private static int freePort() { try (ServerSocket socket = new ServerSocket()) { socket.bind(new InetSocketAddress("127.0.0.1", 0)); return socket.getLocalPort(); } catch (IOException e) { throw new IllegalStateException("无法分配浏览器调试端口", e); } }
    private static SessionInfo stopped(VideoBrowserProfileView p) { return new SessionInfo(p.id(), p.name(), "STOPPED", p.startUrl(), null, 1280, 820); }
    @Override public void close() { sessions.keySet().forEach(this::closeQuietly); }

    private final class Session {
        private final Process process; private final WebSocket socket; private final AtomicInteger ids = new AtomicInteger(); private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        Session(Process process, WebSocket socket) { this.process = process; this.socket = socket; }
        JsonNode command(String method, Object params) { int id = ids.incrementAndGet(); CompletableFuture<JsonNode> future = new CompletableFuture<>(); pending.put(id, future); try { socket.sendText(mapper.writeValueAsString(Map.of("id", id, "method", method, "params", params)), true).join(); return future.get(15, TimeUnit.SECONDS); } catch (Exception e) { pending.remove(id); throw new IllegalStateException("浏览器命令失败: " + method, e); } }
        SessionInfo info(VideoBrowserProfileView p) { return new SessionInfo(p.id(), p.name(), "RUNNING", p.startUrl(), null, 1280, 820); }
    }
    private final class Listener implements WebSocket.Listener {
        private volatile Session session;
        private StringBuilder text = new StringBuilder();
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) { text.append(data); if (last) { try { JsonNode node = mapper.readTree(text.toString()); if (node.has("id") && session != null) { CompletableFuture<JsonNode> f = session.pending.remove(node.path("id").asInt()); if (f != null) f.complete(node.path("result")); } } catch (Exception ignored) {} text = new StringBuilder(); } webSocket.request(1); return CompletableFuture.completedFuture(null); }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
    }
    public record SessionInfo(UUID id, String name, String status, String url, String title, int width, int height) {}
}
