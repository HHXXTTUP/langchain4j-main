package dev.learning.fashionagent.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.learning.fashionagent.config.SnapAnyProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Uses an isolated headless Chromium page to fetch byte ranges and streams them back to Java. */
@Component
public class ChromiumSegmentedMediaDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChromiumSegmentedMediaDownloader.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)",
            Pattern.CASE_INSENSITIVE);
    private static final int MIN_CHUNK_BYTES = 64 * 1024;
    private static final int MAX_CHUNK_BYTES = 4 * 1024 * 1024;

    private final SnapAnyProperties properties;

    public ChromiumSegmentedMediaDownloader(SnapAnyProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isChromiumFallbackEnabled() && locateExecutable().isPresent();
    }

    public void download(URI mediaUri, Map<String, String> sourceHeaders, Path target) throws IOException {
        Objects.requireNonNull(mediaUri, "mediaUri");
        Objects.requireNonNull(target, "target");
        if (!properties.isChromiumFallbackEnabled()) {
            throw new IOException("Chromium segmented download is disabled");
        }
        Path executable = locateExecutable()
                .orElseThrow(() -> new IOException("Microsoft Edge or Chromium executable was not found"));
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) throw new IOException("Download target has no parent directory: " + target);
        Files.createDirectories(parent);

        Path profile = Files.createTempDirectory(parent, ".snapany-edge-");
        Path temporary = target.resolveSibling(target.getFileName() + ".edge.part");
        Files.deleteIfExists(temporary);
        Map<String, String> headers = sanitizeHeaders(sourceHeaders);
        int chunkBytes = Math.max(MIN_CHUNK_BYTES,
                Math.min(MAX_CHUNK_BYTES, properties.getChromiumChunkBytes()));

        Process edge = null;
        RawCdpClient cdp = null;
        boolean completed = false;
        try (ChunkReceiver receiver = new ChunkReceiver(temporary, properties.getMaxDownloadBytes())) {
            receiver.start(mediaUri, chunkBytes);
            edge = launchEdge(executable, profile);
            int port = awaitDevToolsPort(profile, edge);
            cdp = RawCdpClient.connect(findPageWebSocket(port), properties.getChromiumStartupTimeout());
            applyExactHeaders(cdp, headers, mediaUri);
            cdp.command("Page.navigate", Map.of("url", receiver.pageUri().toASCIIString()));
            cdp.startRequestHeaderInterceptor(headers, exception -> receiver.fail(exception.getMessage()));
            long size = receiver.await(properties.getReadTimeout().plus(properties.getChromiumStartupTimeout()));
            if (size <= 0) throw new IOException("Chromium segmented download returned an empty file");
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            completed = true;
            LOGGER.info("SnapAny Chromium segmented download completed host={} file={} size={} bytes",
                    mediaUri.getHost(), target, size);
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
            if (cdp != null) cdp.close();
            stopEdgeProcesses(edge, profile);
            deleteTree(profile);
        }
    }

    private Process launchEdge(Path executable, Path profile) throws IOException {
        List<String> command = new ArrayList<>(List.of(
                executable.toString(),
                "--headless=new",
                "--disable-gpu",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-sync",
                "--disable-web-security",
                "--disable-features=OutOfBlinkCors",
                "--autoplay-policy=no-user-gesture-required",
                "--remote-debugging-port=0",
                "--remote-allow-origins=*",
                "--user-data-dir=" + profile.toAbsolutePath(),
                "about:blank"));
        return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private int awaitDevToolsPort(Path profile, Process process) throws IOException {
        Path activePort = profile.resolve("DevToolsActivePort");
        long deadline = System.nanoTime() + properties.getChromiumStartupTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(activePort)) {
                List<String> lines = Files.readAllLines(activePort, StandardCharsets.UTF_8);
                if (!lines.isEmpty() && !lines.get(0).isBlank()) {
                    try {
                        return Integer.parseInt(lines.get(0).trim());
                    } catch (NumberFormatException exception) {
                        throw new IOException("Chromium returned an invalid DevTools port", exception);
                    }
                }
            }
            if (!process.isAlive() && !Files.exists(activePort)) {
                throw new IOException("Chromium exited before DevTools became ready, exitCode=" + process.exitValue());
            }
            sleep(Duration.ofMillis(50));
        }
        throw new IOException("Timed out waiting for Chromium DevTools");
    }

    private URI findPageWebSocket(int port) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + "/json/list").toURL().openConnection();
        connection.setConnectTimeout((int) properties.getChromiumStartupTimeout().toMillis());
        connection.setReadTimeout((int) properties.getChromiumStartupTimeout().toMillis());
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("Chromium target list returned HTTP " + connection.getResponseCode());
            }
            JsonNode targets = JSON.readTree(connection.getInputStream());
            for (JsonNode target : targets) {
                String websocketUrl = target.path("webSocketDebuggerUrl").asText("");
                if ("page".equals(target.path("type").asText()) && !websocketUrl.isBlank()) {
                    URI websocket = URI.create(websocketUrl);
                    return URI.create("ws://127.0.0.1:" + websocket.getPort() + websocket.getRawPath());
                }
            }
            throw new IOException("Chromium did not expose a page DevTools target");
        } finally {
            connection.disconnect();
        }
    }

    private void applyExactHeaders(RawCdpClient cdp, Map<String, String> headers, URI mediaUri) throws IOException {
        cdp.command("Network.setExtraHTTPHeaders", Map.of("headers", headers));
        String userAgent = valueIgnoreCase(headers, "User-Agent");
        if (userAgent != null) {
            cdp.command("Network.setUserAgentOverride", Map.of("userAgent", userAgent));
        }
        cdp.command("Fetch.enable", Map.of("patterns", List.of(Map.of(
                "urlPattern", mediaUri.getScheme() + "://" + mediaUri.getRawAuthority() + "/*",
                "requestStage", "Request"))));
        LOGGER.info("SnapAny Chromium exact headers applied names={} emptyHeaders={}",
                headers.keySet(), headers.entrySet().stream()
                        .filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList());
    }

    private static String valueIgnoreCase(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static void sleep(Duration duration) throws IOException {
        try {
            Thread.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Chromium", exception);
        }
    }

    static ContentRange parseContentRange(String value) throws IOException {
        if (value == null) throw new IOException("Chromium response did not contain Content-Range");
        Matcher matcher = CONTENT_RANGE.matcher(value.trim());
        if (!matcher.matches() || "*".equals(matcher.group(3))) {
            throw new IOException("Invalid Content-Range: " + value);
        }
        long start = Long.parseLong(matcher.group(1));
        long end = Long.parseLong(matcher.group(2));
        long total = Long.parseLong(matcher.group(3));
        if (start < 0 || end < start || total <= end) throw new IOException("Invalid Content-Range: " + value);
        return new ContentRange(start, end, total);
    }

    private Optional<Path> locateExecutable() {
        if (properties.getChromiumExecutable() != null && !properties.getChromiumExecutable().isBlank()) {
            Path configured = Path.of(properties.getChromiumExecutable().trim()).toAbsolutePath().normalize();
            return Files.isRegularFile(configured) ? Optional.of(configured) : Optional.empty();
        }
        List<Path> candidates = new ArrayList<>();
        addCandidate(candidates, System.getenv("ProgramFiles(x86)"), "Microsoft/Edge/Application/msedge.exe");
        addCandidate(candidates, System.getenv("ProgramFiles"), "Microsoft/Edge/Application/msedge.exe");
        addCandidate(candidates, System.getenv("LOCALAPPDATA"), "Microsoft/Edge/Application/msedge.exe");
        addCandidate(candidates, System.getenv("ProgramFiles"), "Google/Chrome/Application/chrome.exe");
        addCandidate(candidates, System.getenv("ProgramFiles(x86)"), "Google/Chrome/Application/chrome.exe");
        addCandidate(candidates, System.getenv("LOCALAPPDATA"), "Google/Chrome/Application/chrome.exe");
        return candidates.stream().filter(Files::isRegularFile).findFirst();
    }

    private static void addCandidate(List<Path> candidates, String root, String relative) {
        if (root != null && !root.isBlank()) candidates.add(Path.of(root).resolve(relative));
    }

    private static Map<String, String> sanitizeHeaders(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) return result;
        source.forEach((name, value) -> {
            if (name == null || value == null || name.isBlank() || name.startsWith(":")) return;
            if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return;
            result.put(name, value);
        });
        return result;
    }

    private static void stopEdgeProcesses(Process process, Path profile) {
        String marker = profile.toAbsolutePath().toString().toLowerCase(Locale.ROOT);
        ProcessHandle.allProcesses()
                .filter(handle -> handle.info().commandLine()
                        .map(command -> command.toLowerCase(Locale.ROOT).contains(marker))
                        .orElse(false))
                .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
                .forEach(handle -> {
                    if (!handle.destroy()) handle.destroyForcibly();
                });
        if (process != null && process.isAlive()) process.destroy();
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
            if (!Files.exists(root)) return;
            try {
                Thread.sleep(100L * attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        LOGGER.debug("Could not fully remove temporary Chromium profile path={}", root);
    }

    record ContentRange(long start, long end, long total) {}

    private static final class ChunkReceiver implements AutoCloseable {
        private final Path target;
        private final long maxBytes;
        private final String token = UUID.randomUUID().toString().replace("-", "");
        private final CountDownLatch finished = new CountDownLatch(1);
        private final OutputStream output;
        private final HttpServer server;
        private volatile String failure;
        private volatile boolean done;
        private long received;
        private long expectedTotal = -1;
        private String page;

        private ChunkReceiver(Path target, long maxBytes) throws IOException {
            this.target = target;
            this.maxBytes = maxBytes;
            this.output = Files.newOutputStream(target);
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "snapany-edge-receiver");
                thread.setDaemon(true);
                return thread;
            }));
        }

        private void start(URI mediaUri, int chunkBytes) throws IOException {
            String root = "/" + token;
            String callback = "http://127.0.0.1:" + server.getAddress().getPort() + root;
            page = buildPage(mediaUri, chunkBytes, maxBytes, callback);
            server.createContext(root + "/page", this::servePage);
            server.createContext(root + "/chunk", this::receiveChunk);
            server.createContext(root + "/done", this::receiveDone);
            server.createContext(root + "/fail", this::receiveFailure);
            server.start();
        }

        private URI pageUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + token + "/page");
        }

        private long await(Duration timeout) throws IOException {
            boolean signaled;
            try {
                signaled = finished.await(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Chromium segmented download was interrupted", exception);
            }
            if (!signaled) throw new IOException("Chromium segmented download timed out");
            if (failure != null) throw new IOException("Chromium segmented download failed: " + failure);
            if (!done || received <= 0 || expectedTotal != received) {
                throw new IOException("Chromium segmented download was incomplete, received=" + received
                        + " expected=" + expectedTotal);
            }
            return received;
        }

        private void servePage(HttpExchange exchange) throws IOException {
            LOGGER.info("SnapAny Chromium capture page opened");
            byte[] body = page.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private void receiveChunk(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                long offset = Long.parseLong(query.getOrDefault("offset", "-1"));
                long total = Long.parseLong(query.getOrDefault("total", "-1"));
                synchronized (this) {
                    if (done || failure != null) throw new IOException("receiver is already closed");
                    if (offset != received) throw new IOException("unexpected chunk offset " + offset + ", expected " + received);
                    if (total > maxBytes) throw new IOException("media exceeds configured limit: " + total);
                    if (total >= 0) {
                        if (expectedTotal >= 0 && expectedTotal != total) throw new IOException("media size changed");
                        expectedTotal = total;
                    }
                    try (InputStream input = exchange.getRequestBody()) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            if (received + read > maxBytes) throw new IOException("media exceeds configured limit");
                            output.write(buffer, 0, read);
                            received += read;
                        }
                    }
                    LOGGER.debug("SnapAny Chromium chunk received offset={} received={} total={}",
                            offset, received, expectedTotal);
                }
                respond(exchange, 200, "ok");
            } catch (Exception exception) {
                fail(exception.getMessage());
                respond(exchange, 409, exception.getMessage());
            }
        }

        private void receiveDone(HttpExchange exchange) throws IOException {
            try {
                Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
                long total = Long.parseLong(query.getOrDefault("total", "-1"));
                synchronized (this) {
                    if (total < 0 || total != received) throw new IOException("final size mismatch");
                    if (expectedTotal >= 0 && expectedTotal != total) throw new IOException("expected size mismatch");
                    expectedTotal = total;
                    output.flush();
                    output.close();
                    done = true;
                }
                respond(exchange, 200, "ok");
                finished.countDown();
            } catch (Exception exception) {
                fail(exception.getMessage());
                respond(exchange, 409, exception.getMessage());
            }
        }

        private void receiveFailure(HttpExchange exchange) throws IOException {
            String message;
            try (InputStream input = exchange.getRequestBody()) {
                message = new String(input.readNBytes(8192), StandardCharsets.UTF_8);
            }
            fail(message.isBlank() ? "browser script failed" : message);
            respond(exchange, 200, "recorded");
        }

        private synchronized void fail(String message) {
            if (failure == null) failure = message == null ? "unknown browser error" : message;
            finished.countDown();
        }

        @Override
        public void close() {
            server.stop(0);
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }

        private static void respond(HttpExchange exchange, int status, String text) throws IOException {
            byte[] body = text.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private static Map<String, String> query(String rawQuery) {
            Map<String, String> values = new LinkedHashMap<>();
            if (rawQuery == null || rawQuery.isBlank()) return values;
            for (String pair : rawQuery.split("&")) {
                String[] parts = pair.split("=", 2);
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
            }
            return values;
        }

        private static String buildPage(
                URI mediaUri, int chunkBytes, long maxBytes, String callback)
                throws IOException {
            String mediaJson = JSON.writeValueAsString(mediaUri.toASCIIString());
            String callbackJson = JSON.writeValueAsString(callback);
            return """
                    <!doctype html><meta charset="utf-8"><title>SnapAny media transfer</title>
                    <script>
                    (async()=>{
                      const mediaUrl=%s, callback=%s;
                      const chunkBytes=%d, maxBytes=%d;
                      const post=async(path, body)=>{
                        const response=await fetch(callback+path,{method:'POST',body});
                        if(!response.ok)throw new Error(await response.text());
                      };
                      try{
                        let offset=0,total=-1,fullResponse=false;
                        while(total<0||offset<total){
                          const end=Math.min(maxBytes-1,offset+chunkBytes-1);
                          if(end<offset)throw new Error('media exceeds configured limit');
                          const requestHeaders=new Headers();
                          requestHeaders.set('Range','bytes='+offset+'-'+end);
                          const response=await fetch(mediaUrl,{headers:requestHeaders,cache:'no-store',credentials:'omit'});
                          let responseStart=offset,responseEnd=-1,responseTotal=-1;
                          if(response.status===206){
                            const match=/bytes\\s+(\\d+)-(\\d+)\\/(\\d+)/i.exec(response.headers.get('content-range')||'');
                            if(!match)throw new Error('missing Content-Range');
                            responseStart=Number(match[1]);responseEnd=Number(match[2]);responseTotal=Number(match[3]);
                            if(responseStart!==offset)throw new Error('unexpected range start '+responseStart);
                          }else if(response.status===200&&offset===0){
                            fullResponse=true;
                            responseTotal=Number(response.headers.get('content-length')||'-1');
                          }else{
                            throw new Error('media request returned HTTP '+response.status);
                          }
                          if(responseTotal>maxBytes)throw new Error('media exceeds configured limit: '+responseTotal);
                          if(total>=0&&responseTotal>=0&&total!==responseTotal)throw new Error('media size changed');
                          if(responseTotal>=0)total=responseTotal;
                          const reader=response.body.getReader();let responseBytes=0;
                          while(true){
                            const part=await reader.read();if(part.done)break;
                            await post('/chunk?offset='+(offset+responseBytes)+'&total='+total,part.value);
                            responseBytes+=part.value.byteLength;
                          }
                          if(response.status===206&&responseBytes!==responseEnd-responseStart+1)
                            throw new Error('incomplete range response');
                          offset+=responseBytes;
                          if(fullResponse){total=offset;break;}
                        }
                        await post('/done?total='+offset,new Uint8Array());
                        document.title='completed '+offset;
                      }catch(error){
                        try{await post('/fail',String(error&&error.stack||error));}catch(ignored){}
                        document.title='failed';
                      }
                    })();
                    </script>
                    """.formatted(mediaJson, callbackJson, chunkBytes, maxBytes);
        }
    }

    /** Minimal local WebSocket client for Chromium DevTools; it intentionally sends no Origin header. */
    private static final class RawCdpClient implements AutoCloseable {
        private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        private final Socket socket;
        private final InputStream input;
        private final OutputStream output;
        private final AtomicInteger sequence = new AtomicInteger();
        private final java.security.SecureRandom random = new java.security.SecureRandom();
        private final ArrayDeque<JsonNode> queuedMessages = new ArrayDeque<>();

        private RawCdpClient(Socket socket) throws IOException {
            this.socket = socket;
            this.input = socket.getInputStream();
            this.output = socket.getOutputStream();
        }

        static RawCdpClient connect(URI websocketUri, Duration timeout) throws IOException {
            if (!"ws".equalsIgnoreCase(websocketUri.getScheme())) {
                throw new IOException("Unsupported Chromium DevTools scheme: " + websocketUri.getScheme());
            }
            int port = websocketUri.getPort() > 0 ? websocketUri.getPort() : 80;
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(websocketUri.getHost(), port),
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, timeout.toMillis())));
            socket.setSoTimeout(Math.toIntExact(Math.min(Integer.MAX_VALUE, timeout.toMillis())));
            RawCdpClient client = new RawCdpClient(socket);
            client.handshake(websocketUri);
            return client;
        }

        private void handshake(URI websocketUri) throws IOException {
            byte[] nonce = new byte[16];
            random.nextBytes(nonce);
            String key = Base64.getEncoder().encodeToString(nonce);
            String path = websocketUri.getRawPath();
            if (websocketUri.getRawQuery() != null) path += "?" + websocketUri.getRawQuery();
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + websocketUri.getHost() + ":" + websocketUri.getPort() + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String response = readHttpHeaders(input);
            if (!response.startsWith("HTTP/1.1 101")) {
                throw new IOException("Chromium DevTools WebSocket handshake failed: "
                        + response.lines().findFirst().orElse("empty response"));
            }
            String expected = websocketAccept(key);
            boolean validAccept = response.lines()
                    .map(String::trim)
                    .anyMatch(line -> line.equalsIgnoreCase("Sec-WebSocket-Accept: " + expected));
            if (!validAccept) throw new IOException("Chromium DevTools returned an invalid WebSocket accept key");
        }

        JsonNode command(String method, Map<String, ?> params) throws IOException {
            int id = sequence.incrementAndGet();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("id", id);
            request.put("method", method);
            request.put("params", params);
            sendFrame(0x1, JSON.writeValueAsBytes(request));
            while (true) {
                JsonNode response = JSON.readTree(readMessage());
                if (response.path("id").asInt(-1) != id) {
                    synchronized (queuedMessages) {
                        queuedMessages.addLast(response);
                    }
                    continue;
                }
                if (response.has("error")) {
                    throw new IOException("Chromium DevTools " + method + " failed: " + response.path("error"));
                }
                return response.path("result");
            }
        }

        void startRequestHeaderInterceptor(
                Map<String, String> exactHeaders, Consumer<IOException> failureHandler) {
            Thread interceptor = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        JsonNode message;
                        synchronized (queuedMessages) {
                            message = queuedMessages.pollFirst();
                        }
                        if (message == null) message = JSON.readTree(readMessage());
                        if (!"Fetch.requestPaused".equals(message.path("method").asText())) continue;
                        JsonNode params = message.path("params");
                        String requestId = params.path("requestId").asText("");
                        if (requestId.isBlank()) continue;
                        Map<String, String> merged = new LinkedHashMap<>();
                        JsonNode originalHeaders = params.path("request").path("headers");
                        if (originalHeaders.isObject()) {
                            originalHeaders.fields().forEachRemaining(entry ->
                                    merged.put(entry.getKey(), entry.getValue().asText("")));
                        } else if (originalHeaders.isArray()) {
                            originalHeaders.forEach(header -> merged.put(
                                    header.path("name").asText(), header.path("value").asText("")));
                        }
                        exactHeaders.forEach((name, value) -> {
                            merged.keySet().removeIf(existing -> existing.equalsIgnoreCase(name));
                            merged.put(name, value);
                        });
                        List<Map<String, String>> headerList = merged.entrySet().stream()
                                .map(entry -> Map.of("name", entry.getKey(), "value", entry.getValue()))
                                .toList();
                        Map<String, Object> continueRequest = new LinkedHashMap<>();
                        continueRequest.put("requestId", requestId);
                        continueRequest.put("headers", headerList);
                        sendCommandWithoutWaiting("Fetch.continueRequest", continueRequest);
                    }
                } catch (IOException exception) {
                    if (!socket.isClosed()) failureHandler.accept(exception);
                }
            }, "snapany-edge-header-interceptor");
            interceptor.setDaemon(true);
            interceptor.start();
        }

        private void sendCommandWithoutWaiting(String method, Map<String, ?> params) throws IOException {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("id", sequence.incrementAndGet());
            request.put("method", method);
            request.put("params", params);
            sendFrame(0x1, JSON.writeValueAsBytes(request));
        }

        private byte[] readMessage() throws IOException {
            synchronized (queuedMessages) {
                JsonNode queued = queuedMessages.pollFirst();
                if (queued != null) return JSON.writeValueAsBytes(queued);
            }
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            boolean started = false;
            while (true) {
                int first = readUnsignedByte(input);
                int second = readUnsignedByte(input);
                boolean finalFrame = (first & 0x80) != 0;
                int opcode = first & 0x0f;
                boolean masked = (second & 0x80) != 0;
                long length = second & 0x7f;
                if (length == 126) length = readUnsignedShort(input);
                else if (length == 127) length = readUnsignedLong(input);
                if (length > 16L * 1024 * 1024) throw new IOException("Chromium DevTools frame is too large");
                byte[] mask = masked ? readExactly(input, 4) : null;
                byte[] payload = readExactly(input, Math.toIntExact(length));
                if (mask != null) {
                    for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index % 4];
                }
                if (opcode == 0x8) throw new IOException("Chromium closed the DevTools WebSocket");
                if (opcode == 0x9) {
                    sendFrame(0xA, payload);
                    continue;
                }
                if (opcode == 0x1) started = true;
                else if (opcode != 0x0 || !started) continue;
                message.write(payload);
                if (finalFrame) return message.toByteArray();
            }
        }

        private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
            output.write(0x80 | opcode);
            int length = payload.length;
            if (length <= 125) {
                output.write(0x80 | length);
            } else if (length <= 0xffff) {
                output.write(0x80 | 126);
                output.write((length >>> 8) & 0xff);
                output.write(length & 0xff);
            } else {
                output.write(0x80 | 127);
                long longLength = length;
                for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (longLength >>> shift) & 0xff);
            }
            byte[] mask = new byte[4];
            random.nextBytes(mask);
            output.write(mask);
            for (int index = 0; index < payload.length; index++) output.write(payload[index] ^ mask[index % 4]);
            output.flush();
        }

        @Override
        public void close() {
            try {
                sendFrame(0x8, new byte[0]);
            } catch (IOException ignored) {
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private static String readHttpHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int state = 0;
            while (bytes.size() < 16 * 1024) {
                int value = input.read();
                if (value < 0) throw new IOException("Chromium closed the WebSocket handshake");
                bytes.write(value);
                state = switch (state) {
                    case 0 -> value == '\r' ? 1 : 0;
                    case 1 -> value == '\n' ? 2 : 0;
                    case 2 -> value == '\r' ? 3 : 0;
                    case 3 -> value == '\n' ? 4 : 0;
                    default -> state;
                };
                if (state == 4) return bytes.toString(StandardCharsets.US_ASCII);
            }
            throw new IOException("Chromium WebSocket handshake headers are too large");
        }

        private static String websocketAccept(String key) throws IOException {
            try {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                        .digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII));
                return Base64.getEncoder().encodeToString(digest);
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IOException("SHA-1 is unavailable", exception);
            }
        }

        private static int readUnsignedByte(InputStream input) throws IOException {
            int value = input.read();
            if (value < 0) throw new IOException("Unexpected end of Chromium WebSocket stream");
            return value;
        }

        private static int readUnsignedShort(InputStream input) throws IOException {
            return (readUnsignedByte(input) << 8) | readUnsignedByte(input);
        }

        private static long readUnsignedLong(InputStream input) throws IOException {
            long value = 0;
            for (int index = 0; index < 8; index++) value = (value << 8) | readUnsignedByte(input);
            if (value < 0) throw new IOException("Invalid Chromium WebSocket frame length");
            return value;
        }

        private static byte[] readExactly(InputStream input, int length) throws IOException {
            byte[] result = input.readNBytes(length);
            if (result.length != length) throw new IOException("Unexpected end of Chromium WebSocket frame");
            return result;
        }
    }
}
