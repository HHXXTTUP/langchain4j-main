package dev.learning.stockanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.stockanalyzer.config.FundamentalsProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AkshareSidecarManager {

    private static final Logger log = LoggerFactory.getLogger(AkshareSidecarManager.class);
    private static final String SERVICE_PROTOCOL = "stock-lens-akshare-v2";
    private static final int PORT_SCAN_LIMIT = 30;

    private final FundamentalsProperties properties;
    private final ObjectMapper objectMapper;
    private volatile Process process;
    private volatile boolean packagedSidecar;
    private volatile boolean ready;
    private volatile String failureMessage;

    public AkshareSidecarManager(FundamentalsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startIfConfigured() {
        if (!properties.isEnabled() || !properties.isAutoStart()) return;

        Path workingDirectory = resolveApplicationDirectory();
        log.info("AKShare application directory: {}", workingDirectory);
        List<String> command = resolveCommand(workingDirectory);
        if (command == null) {
            failureMessage = packagedSidecar
                    ? "内置 AKShare 数据服务文件缺失"
                    : "AKShare Python 环境未安装";
            return;
        }

        try {
            URI baseUri = URI.create(properties.getBaseUrl());
            int preferredPort = baseUri.getPort() > 0 ? baseUri.getPort() : 8765;
            int port = findAvailablePort(baseUri.getHost(), preferredPort);
            URI instanceUri = withPort(baseUri, port);
            String instanceToken = UUID.randomUUID().toString();
            properties.setBaseUrl(instanceUri.toString());
            Path logDirectory = resolveLogDirectory(workingDirectory);
            Files.createDirectories(logDirectory);
            Path logFile = logDirectory.resolve("akshare-sidecar.log");

            command.add("--port");
            command.add(String.valueOf(port));
            command.add("--request-timeout");
            command.add(String.valueOf(properties.getRequestTimeoutSeconds()));
            command.add("--instance-token");
            command.add(instanceToken);

            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            processBuilder.environment().put("PYTHONUTF8", "1");
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            process = processBuilder.start();
            if (waitUntilHealthy(instanceUri, instanceToken)) {
                ready = true;
                failureMessage = null;
                log.info("AKShare data service started: pid={} url={}", process.pid(), instanceUri);
            } else {
                failureMessage = "AKShare 数据服务启动后未能通过健康检查";
                log.warn("AKShare data service did not become ready: pid={} url={}", process.pid(), instanceUri);
            }
        } catch (Exception e) {
            failureMessage = "AKShare 数据服务启动失败: " + e.getMessage();
            log.warn("Failed to start AKShare data service", e);
        }
    }

    private List<String> resolveCommand(Path workingDirectory) {
        Path packagedExecutable = resolvePackagedSidecar(workingDirectory);
        if (packagedExecutable != null) {
            packagedSidecar = true;
            return new ArrayList<>(List.of(packagedExecutable.toString()));
        }

        Path script = workingDirectory.resolve(properties.getScriptPath()).normalize();
        Path python = resolvePython(workingDirectory);
        if (!Files.isRegularFile(script)) {
            log.warn("AKShare script not found: {}", script);
            return null;
        }
        if (python == null) {
            log.warn("AKShare Python environment not found. Run scripts/setup-akshare.ps1 first.");
            return null;
        }
        return new ArrayList<>(List.of(python.toString(), script.toString()));
    }

    public String unavailableMessage() {
        if (ready) return "AKShare 数据请求失败，请稍后重试";
        if (packagedSidecar || Boolean.getBoolean("stock.app.desktop")) {
            return "内置 AKShare 数据服务暂不可用，请重启应用；如仍失败请查看 logs/akshare-sidecar.log";
        }
        return "AKShare 数据服务暂不可用，请先运行 scripts/setup-akshare.ps1 并重启项目";
    }

    private Path resolveApplicationDirectory() {
        Path userDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                userDirectory,
                userDirectory.resolve("stock-analyzer"),
                userDirectory.getParent() == null
                        ? userDirectory
                        : userDirectory.getParent().resolve("stock-analyzer")
        );
        return candidates.stream()
                .filter(this::looksLikeApplicationDirectory)
                .findFirst()
                .orElse(userDirectory);
    }

    private boolean looksLikeApplicationDirectory(Path directory) {
        return resolvePackagedSidecar(directory) != null
                || Files.isRegularFile(directory.resolve(properties.getScriptPath()))
                || Files.isRegularFile(directory.resolve("pom.xml"))
                && Files.isDirectory(directory.resolve("src/main"));
    }

    private Path resolvePackagedSidecar(Path workingDirectory) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(workingDirectory.resolve("akshare-sidecar.exe"));
        candidates.add(workingDirectory.resolve("akshare-sidecar/akshare-sidecar.exe"));
        candidates.add(workingDirectory.resolve("app/akshare-sidecar.exe"));
        candidates.add(workingDirectory.resolve("app/akshare-sidecar/akshare-sidecar.exe"));
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path launcherDirectory = Path.of(appPath).toAbsolutePath().normalize().getParent();
            if (launcherDirectory != null) {
                candidates.add(launcherDirectory.resolve("app/akshare-sidecar.exe"));
                candidates.add(launcherDirectory.resolve("app/akshare-sidecar/akshare-sidecar.exe"));
            }
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private Path resolvePython(Path workingDirectory) {
        if (properties.getPythonExecutable() != null && !properties.getPythonExecutable().isBlank()) {
            Path configured = Path.of(properties.getPythonExecutable());
            if (!configured.isAbsolute()) configured = workingDirectory.resolve(configured);
            if (Files.isRegularFile(configured)) return configured.normalize();
        }
        List<Path> candidates = List.of(
                workingDirectory.resolve(".venv-akshare/Scripts/python.exe"),
                workingDirectory.resolve(".venv-akshare/bin/python")
        );
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private Path resolveLogDirectory(Path workingDirectory) {
        if (Boolean.getBoolean("stock.app.desktop")) {
            return Path.of(System.getProperty("user.home"), ".stock-lens", "logs");
        }
        return workingDirectory.resolve("target");
    }

    private boolean waitUntilHealthy(URI baseUri, String instanceToken) {
        long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos();
        while (System.nanoTime() < deadline) {
            if (isExpectedInstance(baseUri, instanceToken)) return true;
            Process current = process;
            if (current != null && !current.isAlive()) return false;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isExpectedInstance(URI baseUri, String instanceToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUri.toString() + "/health"))
                    .timeout(Duration.ofMillis(800))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            JsonNode health = objectMapper.readTree(response.body());
            return SERVICE_PROTOCOL.equals(health.path("protocol").asText())
                    && instanceToken.equals(health.path("instanceToken").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private int findAvailablePort(String host, int preferredPort) throws Exception {
        for (int port = preferredPort; port < preferredPort + PORT_SCAN_LIMIT; port++) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(host, port));
                if (port != preferredPort) {
                    log.warn("AKShare port {} is occupied; using {} for this application instance", preferredPort, port);
                }
                return port;
            } catch (Exception ignored) {
                // Try the next local port. Old packaged sidecars may still own the preferred one.
            }
        }
        throw new IllegalStateException("No available AKShare port near " + preferredPort);
    }

    private URI withPort(URI baseUri, int port) throws Exception {
        return new URI(baseUri.getScheme(), baseUri.getUserInfo(), baseUri.getHost(), port,
                baseUri.getPath(), baseUri.getQuery(), baseUri.getFragment());
    }

    @PreDestroy
    void stop() {
        Process current = process;
        if (current != null && current.isAlive()) current.destroy();
    }
}
