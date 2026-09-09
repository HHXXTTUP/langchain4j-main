package dev.learning.fashionagent.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Manages isolated local Chromium profiles. Credentials stay in the browser profile; the app only stores metadata. */
@Service
public class VideoBrowserProfileService {
    private final JdbcTemplate jdbc;
    private final VideoBrowserProperties properties;
    private final Map<UUID, Process> processes = new ConcurrentHashMap<>();

    public VideoBrowserProfileService(JdbcTemplate jdbc, VideoBrowserProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<VideoBrowserProfileView> list() {
        return jdbc.query("SELECT * FROM video_browser_profile ORDER BY created_at, id", this::map);
    }

    public VideoBrowserProfileView create(CreateProfile request) {
        if (request == null || request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("账号名称不能为空");
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Path profile = properties.profileRootPath().resolve(id.toString()).toAbsolutePath().normalize();
        String extension = blankToNull(request.extensionDirectory(), properties.getExtensionDirectory());
        String browser = blankToNull(request.browserExecutable(), properties.getBrowserExecutable());
        String url = blankToDefault(request.startUrl(), properties.getStartUrl());
        validatePathInsideProfileRoot(profile);
        jdbc.update("INSERT INTO video_browser_profile(id,name,account_hint,profile_directory,extension_directory,browser_executable,start_url,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                id.toString(), request.name().trim(), trim(request.accountHint()), profile.toString(), extension, browser, url, Timestamp.from(now), Timestamp.from(now));
        return get(id);
    }

    public VideoBrowserProfileView update(UUID id, UpdateProfile request) {
        get(id);
        if (request == null || request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("账号名称不能为空");
        String extension = blankToNull(request.extensionDirectory(), properties.getExtensionDirectory());
        String browser = blankToNull(request.browserExecutable(), properties.getBrowserExecutable());
        String url = blankToDefault(request.startUrl(), properties.getStartUrl());
        jdbc.update("UPDATE video_browser_profile SET name=?,account_hint=?,extension_directory=?,browser_executable=?,start_url=?,updated_at=? WHERE id=?",
                request.name().trim(), trim(request.accountHint()), extension, browser, url, Timestamp.from(Instant.now()), id.toString());
        return get(id);
    }

    public VideoBrowserProfileView get(UUID id) {
        return jdbc.queryForObject("SELECT * FROM video_browser_profile WHERE id=?", this::map, id.toString());
    }

    public VideoBrowserProfileView start(UUID id) {
        VideoBrowserProfileView profile = get(id);
        Process current = processes.get(id);
        if (current != null && current.isAlive()) return profile;
        Path profileDirectory = Path.of(profile.profileDirectory()).toAbsolutePath().normalize();
        try { Files.createDirectories(profileDirectory); } catch (IOException e) { throw new IllegalStateException("无法创建浏览器 Profile 目录: " + e.getMessage(), e); }
        Path executable = resolveBrowser(profile.browserExecutable());
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.add("--user-data-dir=" + profileDirectory);
        command.add("--no-first-run");
        command.add("--no-default-browser-check");
        command.add("--new-window");
        if (profile.extensionDirectory() != null && !profile.extensionDirectory().isBlank()) {
            Path extension = Path.of(profile.extensionDirectory()).toAbsolutePath().normalize();
            if (!Files.isDirectory(extension) || !Files.isRegularFile(extension.resolve("manifest.json"))) throw new IllegalArgumentException("插件目录无效，必须包含 manifest.json: " + extension);
            command.add("--disable-extensions-except=" + extension);
            command.add("--load-extension=" + extension);
        }
        command.add(profile.startUrl());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            processes.put(id, process);
            process.onExit().thenRun(() -> processes.remove(id, process));
            return profile;
        } catch (IOException e) { throw new IllegalStateException("启动浏览器失败，请检查浏览器路径: " + executable, e); }
    }

    public VideoBrowserProfileView stop(UUID id) {
        get(id);
        Process process = processes.remove(id);
        if (process != null && process.isAlive()) {
            process.destroy();
            try { if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        }
        return get(id);
    }

    public void delete(UUID id) {
        stop(id);
        jdbc.update("DELETE FROM video_browser_profile WHERE id=?", id.toString());
    }

    public Path chooseDefaultExtensionDirectory() {
        return properties.getExtensionDirectory().isBlank() ? null : Path.of(properties.getExtensionDirectory()).toAbsolutePath().normalize();
    }

    private Path resolveBrowser(String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) candidates.add(configured);
        String local = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("PROGRAMFILES");
        String programFilesX86 = System.getenv("PROGRAMFILES(X86)");
        if (local != null) {
            candidates.add(local + "\\Google\\Chrome\\Application\\chrome.exe");
            candidates.add(local + "\\Microsoft\\Edge\\Application\\msedge.exe");
        }
        if (programFiles != null) candidates.add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe");
        if (programFilesX86 != null) candidates.add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe");
        return candidates.stream().map(Path::of).map(Path::toAbsolutePath).filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到 Chrome/Edge，请在页面中填写浏览器可执行文件路径"));
    }

    private VideoBrowserProfileView map(ResultSet rs, int ignored) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        Process process = processes.get(id);
        String status = process != null && process.isAlive() ? "RUNNING" : "STOPPED";
        Long pid = process != null && process.isAlive() ? process.pid() : null;
        return new VideoBrowserProfileView(id, rs.getString("name"), rs.getString("account_hint"), rs.getString("profile_directory"), rs.getString("extension_directory"), rs.getString("browser_executable"), rs.getString("start_url"), status, pid, rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private void validatePathInsideProfileRoot(Path profile) { if (!profile.startsWith(properties.profileRootPath())) throw new IllegalArgumentException("Profile 路径必须位于配置的 Profile 根目录下"); }
    private static String blankToNull(String value, String fallback) { String v = value == null || value.isBlank() ? fallback : value.trim(); return v == null || v.isBlank() ? null : v; }
    private static String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }

    public record CreateProfile(String name, String accountHint, String extensionDirectory, String browserExecutable, String startUrl) {}
    public record UpdateProfile(String name, String accountHint, String extensionDirectory, String browserExecutable, String startUrl) {}
}
