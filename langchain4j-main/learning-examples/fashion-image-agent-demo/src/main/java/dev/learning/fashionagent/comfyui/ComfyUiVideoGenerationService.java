package dev.learning.fashionagent.comfyui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.account.AccountContext;
import dev.learning.fashionagent.config.ComfyUiVideoProperties;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ComfyUiVideoGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComfyUiVideoGenerationService.class);
    private static final Pattern DATA_URI = Pattern.compile("^data:(image/[A-Za-z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final ComfyUiVideoProperties properties;
    private final ComfyUiVideoRepository repository;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final HttpClient httpClient;
    private final Map<UUID, MutableTask> tasks = new ConcurrentHashMap<>();

    public ComfyUiVideoGenerationService(
            ComfyUiVideoProperties properties,
            ComfyUiVideoRepository repository,
            ObjectMapper objectMapper,
            @Qualifier("comfyUiVideoExecutor") Executor executor) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public synchronized ComfyUiVideoView create(String prompt, Integer duration, String resolution, List<String> images) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("COMFYUI_VIDEO_TOKEN is not configured");
        }
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
        if (prompt.length() > properties.getMaxPromptLength()) throw new IllegalArgumentException("prompt is too long");
        List<String> sources = images == null ? List.of() : images.stream().filter(value -> value != null && !value.isBlank()).toList();
        if (sources.isEmpty()) throw new IllegalArgumentException("at least one reference image is required");
        if (sources.size() > properties.getMaxImages()) throw new IllegalArgumentException("at most " + properties.getMaxImages() + " images are supported");
        int seconds = duration == null ? 5 : duration;
        if (seconds < 1 || seconds > 15) throw new IllegalArgumentException("duration must be between 1 and 15 seconds");
        String outputResolution = resolution == null || resolution.isBlank() ? "768p竖" : resolution;

        UUID id = UUID.randomUUID();
        String accountId = requiredAccountId();
        Path inputDirectory = properties.getDirectory().toAbsolutePath().normalize().resolve(id.toString()).resolve("inputs");
        Path exportDirectory = properties.getExportDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(inputDirectory);
            List<String> storedSources = new ArrayList<>();
            long totalBytes = 0;
            for (int i = 0; i < sources.size(); i++) {
                String source = sources.get(i).trim();
                if (source.startsWith("data:")) {
                    ImageData image = decodeDataUri(source);
                    if (image.bytes().length > properties.getMaxImageBytes()) {
                        throw new IllegalArgumentException("image " + (i + 1) + " exceeds the size limit");
                    }
                    totalBytes += image.bytes().length;
                    if (totalBytes > properties.getMaxTotalImageBytes()) throw new IllegalArgumentException("total image size exceeds the limit");
                    Path path = inputDirectory.resolve("image-" + (i + 1) + extension(image.mimeType()));
                    Files.write(path, image.bytes());
                    storedSources.add(path.toString());
                } else if (source.startsWith("http://") || source.startsWith("https://")) {
                    storedSources.add(source);
                } else {
                    throw new IllegalArgumentException("image " + (i + 1) + " must be a data URL or http URL");
                }
            }
            MutableTask task = new MutableTask(id, accountId, token.trim(), prompt.trim(), seconds, outputResolution,
                    storedSources, inputDirectory, exportDirectory);
            tasks.put(id, task);
            persist(task);
            executor.execute(() -> execute(task));
            return task.view();
        } catch (Exception e) {
            deleteDirectory(inputDirectory);
            throw e instanceof IllegalArgumentException iae ? iae : new IllegalStateException("cannot store reference images", e);
        }
    }

    public synchronized ComfyUiVideoView createFirstLast(
            String prompt, Integer duration, String resolution, String firstFrame, String lastFrame) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("COMFYUI_VIDEO_TOKEN is not configured");
        }
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
        if (firstFrame == null || firstFrame.isBlank() || lastFrame == null || lastFrame.isBlank()) {
            throw new IllegalArgumentException("firstFrame and lastFrame are required");
        }
        int seconds = duration == null ? 5 : duration;
        if (seconds < 1 || seconds > 10) throw new IllegalArgumentException("duration must be between 1 and 10 seconds");
        UUID id = UUID.randomUUID();
        String accountId = requiredAccountId();
        Path inputDirectory = properties.getDirectory().toAbsolutePath().normalize().resolve(id.toString()).resolve("inputs");
        Path exportDirectory = properties.getExportDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(inputDirectory);
            MutableTask task = new MutableTask(id, accountId, token.trim(), prompt.trim(), seconds,
                    resolution == null || resolution.isBlank() ? "768p竖" : resolution,
                    List.of(), inputDirectory, exportDirectory, true, firstFrame.trim(), lastFrame.trim());
            tasks.put(id, task);
            persist(task);
            executor.execute(() -> execute(task));
            return task.view();
        } catch (Exception e) {
            deleteDirectory(inputDirectory);
            throw e instanceof IllegalArgumentException iae ? iae : new IllegalStateException("cannot store first/last frames", e);
        }
    }

    public List<ComfyUiVideoView> list() {
        String accountId = currentAccountScope();
        Map<UUID, ComfyUiVideoView> merged = new HashMap<>();
        repository.list(accountId).forEach(view -> merged.put(view.id(), view));
        tasks.values().stream().filter(task -> accountId == null || task.accountId.equals(accountId)).forEach(task -> merged.put(task.id, task.view()));
        return merged.values().stream().sorted(Comparator.comparing(ComfyUiVideoView::createdAt).reversed()).toList();
    }

    public ComfyUiVideoView get(UUID id) {
        String accountId = currentAccountScope();
        MutableTask task = tasks.get(id);
        if (task != null && (accountId == null || task.accountId.equals(accountId))) return task.view();
        return repository.find(id, accountId).orElseThrow(() -> new IllegalArgumentException("video task not found: " + id));
    }

    public Path finalVideo(UUID id) {
        String accountId = currentAccountScope();
        MutableTask task = tasks.get(id);
        Path path = task == null || (accountId != null && !task.accountId.equals(accountId)) ? repository.finalVideo(id, accountId).orElse(null) : task.finalVideo;
        if (path == null || !Files.isRegularFile(path)) throw new IllegalStateException("video is not ready");
        return path;
    }

    public Path openFolder(UUID id) {
        Path folder = finalVideo(id).getParent();
        try {
            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(folder.toFile());
            else if (System.getProperty("os.name", "").toLowerCase().contains("win")) new ProcessBuilder("explorer.exe", folder.toString()).start();
            else throw new IllegalStateException("desktop folder opening is not supported");
            return folder;
        } catch (Exception e) { throw new IllegalStateException("cannot open video folder", e); }
    }

    public synchronized void delete(UUID id) {
        String accountId = currentAccountScope();
        MutableTask task = tasks.get(id);
        ComfyUiVideoView view = get(id);
        if (view.status() == ComfyUiVideoStatus.QUEUED || view.status() == ComfyUiVideoStatus.SUBMITTING || view.status() == ComfyUiVideoStatus.RUNNING || view.status() == ComfyUiVideoStatus.DOWNLOADING) {
            throw new IllegalStateException("running task cannot be deleted");
        }
        Path finalVideo = finalVideoPathOrNull(id, accountId, task);
        if (task != null && (accountId == null || task.accountId.equals(accountId))) deleteDirectory(task.inputDirectory.getParent());
        if (finalVideo != null) try { Files.deleteIfExists(finalVideo); } catch (Exception e) { throw new IllegalStateException("cannot delete exported video", e); }
        repository.delete(id, accountId);
        tasks.remove(id);
    }

    private void execute(MutableTask task) {
        try {
            task.transition(ComfyUiVideoStatus.SUBMITTING, "submitting ComfyUI workflow"); persist(task);
            List<String> refs = task.imageSources.stream().map(this::toDataUrlIfFile).toList();
            Map<String, Object> body = new HashMap<>();
            body.put("prompt", task.prompt);
            body.put("duration", task.duration);
            body.put("resolution", task.resolution);
            if (task.firstLast) {
                body.put("first_frame", toDataUrlIfFile(task.firstFrame));
                body.put("last_frame", toDataUrlIfFile(task.lastFrame));
            } else {
                for (int i = 0; i < refs.size(); i++) body.put("ref_image_" + i, refs.get(i));
            }
            JsonNode submit = request("POST", workflowPath(task), body, task.token);
            String taskId = text(submit, "/data/task_id");
            if (taskId == null || taskId.isBlank()) throw new IllegalStateException("ComfyUI response did not contain data.task_id: " + submit);
            task.remoteTaskId = taskId;
            String submitStatus = text(submit, "/data/status");
            JsonNode result = submitStatus != null && isSuccess(submitStatus) ? submit : poll(task, taskId);
            String url = firstResultUrl(result);
            if (url == null || url.isBlank()) throw new IllegalStateException("ComfyUI completed without a video URL");
            task.remoteResultUrl = url;
            task.transition(ComfyUiVideoStatus.DOWNLOADING, "downloading generated video"); persist(task);
            Files.createDirectories(task.exportDirectory);
            Path output = task.exportDirectory.resolve("minimax-15s-" + FILE_TIME.format(task.createdAt)
                    + "-" + task.id.toString().substring(0, 8) + ".mp4");
            downloadWithRetry(url, output);
            task.finalVideo = output;
            task.transition(ComfyUiVideoStatus.SUCCESS, "video generated successfully"); persist(task);
        } catch (Exception e) {
            LOGGER.warn("ComfyUI video task {} failed", task.id, e);
            task.error = rootMessage(e);
            task.transition(ComfyUiVideoStatus.FAILED, "video generation failed");
            persist(task);
        }
    }

    private JsonNode poll(MutableTask task, String taskId) throws Exception {
        Instant deadline = Instant.now().plus(properties.getTaskTimeout());
        task.transition(ComfyUiVideoStatus.RUNNING, "ComfyUI task " + taskId + " is running"); persist(task);
        while (Instant.now().isBefore(deadline)) {
            Thread.sleep(properties.getPollInterval().toMillis());
            JsonNode response = request("GET", "/api/v1/comfyui/comfyui_workflow/result/" + taskId, null, task.token);
            String status = text(response, "/data/status");
            if (status == null) status = text(response, "/status");
            if (status != null && isSuccess(status)) return response;
            if (status != null && ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status))) {
                throw new IllegalStateException("ComfyUI task failed: " + response);
            }
            task.message = "ComfyUI status: " + (status == null ? "UNKNOWN" : status);
            persist(task);
        }
        throw new IllegalStateException("ComfyUI task timed out after " + properties.getTaskTimeout());
    }

    private JsonNode request(String method, String path, Object body, String token) throws Exception {
        URI uri = properties.getBaseUrl().resolve(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.getTaskTimeout())
                .header("Authorization", token)
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        } else builder.GET();
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("ComfyUI HTTP " + response.statusCode() + ": " + response.body());
        return objectMapper.readTree(response.body());
    }

    private String workflowPath(MutableTask task) {
        return "/api/v1/comfyui/comfyui_workflow/"
                + (task.firstLast ? properties.getFirstLastWorkflowId() : properties.getWorkflowId());
    }

    private void downloadWithRetry(String rawUrl, Path target) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                download(rawUrl, target);
                return;
            } catch (Exception e) {
                last = e;
                LOGGER.warn("ComfyUI video download failed attempt {}/3 url={}", attempt, rawUrl, e);
                if (attempt < 3) Thread.sleep(2000L * attempt);
            }
        }
        throw last == null ? new IllegalStateException("video download failed") : last;
    }

    private void download(String rawUrl, Path target) throws Exception {
        URI uri = URI.create(rawUrl.replace(" ", "%20"));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(properties.getTaskTimeout()).header("User-Agent", "fashion-image-agent-demo").GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("video download HTTP " + response.statusCode());
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        long total = 0;
        try (InputStream in = response.body(); var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > 1024L * 1024 * 1024) throw new IllegalStateException("video exceeds 1GB download limit");
                out.write(buffer, 0, read);
            }
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String toDataUrlIfFile(String source) {
        if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("data:")) {
            return source;
        }
        if (!source.startsWith("file:")) {
            Path path = Path.of(source);
            if (Files.isRegularFile(path)) {
                try {
                    String mime = Files.probeContentType(path);
                    if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";
                    return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
                } catch (Exception e) { throw new IllegalStateException("cannot read reference image", e); }
            }
        }
        return source;
    }

    private ImageData decodeDataUri(String source) {
        Matcher matcher = DATA_URI.matcher(source);
        if (!matcher.matches()) throw new IllegalArgumentException("invalid image data URL");
        try { return new ImageData(matcher.group(1), Base64.getDecoder().decode(matcher.group(2))); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("invalid base64 image", e); }
    }

    private String firstResultUrl(JsonNode response) {
        JsonNode results = response.at("/data/results");
        if (!results.isArray()) results = response.at("/results");
        if (!results.isArray() || results.isEmpty()) return null;
        for (JsonNode item : results) {
            String url = item.path("url").asText(null);
            if (url != null && !url.isBlank()) return url;
        }
        return null;
    }

    private static boolean isSuccess(String status) { return "SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status); }
    private static String text(JsonNode node, String pointer) { JsonNode value = node.at(pointer); return value.isMissingNode() || value.isNull() ? null : value.asText(); }
    private static String extension(String mime) { return mime.endsWith("png") ? ".png" : mime.endsWith("webp") ? ".webp" : ".jpg"; }
    private static String rootMessage(Throwable e) { Throwable current = e; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.toString() : current.getMessage(); }
    private void persist(MutableTask task) { repository.save(new ComfyUiVideoSnapshot(task.accountId, task.view(), task.inputDirectory, task.finalVideo)); }
    private static String currentAccountScope() {
        AccountContext.Snapshot account = AccountContext.current();
        if (account == null || account.accountId() == null || account.accountId().isBlank()) throw new IllegalStateException("account context is unavailable");
        return account.administrator() ? null : account.accountId();
    }
    private static String requiredAccountId() {
        AccountContext.Snapshot account = AccountContext.current();
        if (account == null || account.accountId() == null || account.accountId().isBlank()) throw new IllegalStateException("account context is unavailable");
        return account.accountId();
    }
    private Path finalVideoPathOrNull(UUID id, String accountId, MutableTask task) {
        if (task != null && (accountId == null || task.accountId.equals(accountId))) return task.finalVideo;
        return repository.finalVideo(id, accountId).orElse(null);
    }
    private static void deleteDirectory(Path path) { if (path == null) return; try { if (Files.exists(path)) Files.walk(path).sorted(Comparator.reverseOrder()).forEach(file -> { try { Files.deleteIfExists(file); } catch (Exception ignored) {} }); } catch (Exception ignored) {} }

    private record ImageData(String mimeType, byte[] bytes) {}

    private static final class MutableTask {
        private final UUID id;
        private final String accountId;
        private final String token;
        private final String prompt;
        private final int duration;
        private final String resolution;
        private final List<String> imageSources;
        private final Path inputDirectory;
        private final Path exportDirectory;
        private final boolean firstLast;
        private final String firstFrame;
        private final String lastFrame;
        private final Instant createdAt = Instant.now();
        private volatile Instant updatedAt = createdAt;
        private volatile ComfyUiVideoStatus status = ComfyUiVideoStatus.QUEUED;
        private volatile String message = "queued";
        private volatile String remoteTaskId;
        private volatile String remoteResultUrl;
        private volatile Path finalVideo;
        private volatile String error;
        private MutableTask(UUID id, String accountId, String token, String prompt, int duration, String resolution,
                            List<String> imageSources, Path inputDirectory, Path exportDirectory) {
            this(id, accountId, token, prompt, duration, resolution, imageSources, inputDirectory, exportDirectory, false, null, null);
        }
        private MutableTask(UUID id, String accountId, String token, String prompt, int duration, String resolution,
                            List<String> imageSources, Path inputDirectory, Path exportDirectory,
                            boolean firstLast, String firstFrame, String lastFrame) {
            this.id = id; this.accountId = accountId; this.token = token; this.prompt = prompt; this.duration = duration;
            this.resolution = resolution; this.imageSources = imageSources; this.inputDirectory = inputDirectory;
            this.exportDirectory = exportDirectory; this.firstLast = firstLast; this.firstFrame = firstFrame; this.lastFrame = lastFrame;
        }
        private synchronized void transition(ComfyUiVideoStatus next, String nextMessage) { status = next; message = nextMessage; updatedAt = Instant.now(); }
        private ComfyUiVideoView view() { return new ComfyUiVideoView(id, prompt, duration, resolution, imageSources.size(), status, message, remoteTaskId, remoteResultUrl, finalVideo == null ? null : "/api/comfyui-video-generations/" + id + "/final", finalVideo == null ? null : finalVideo.getFileName().toString(), error, createdAt, updatedAt); }
    }
}
