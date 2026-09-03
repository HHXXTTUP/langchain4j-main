package dev.learning.fashionagent.video;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.selection.AssetType;
import dev.learning.fashionagent.selection.BalancedAssetSelectionService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VideoCatalogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCatalogService.class);
    /** Public API token for videos that are stored directly under video_ai. */
    public static final String ROOT_FOLDER = "__root__";

    private final RunningHubProperties properties;
    // Kept in the constructor for compatibility; expensive FFprobe metadata is loaded by the browser on demand.
    private final VideoMediaProcessor processor;
    private final BalancedAssetSelectionService balancedSelection;
    /** Folders used as the source pool for video generation. */
    private final Set<String> selectedSourceFolders = ConcurrentHashMap.newKeySet();
    /** Legacy browsing/selection state kept for the old single-folder endpoint. */
    private volatile String selectedFolder;

    @Autowired
    public VideoCatalogService(
            RunningHubProperties properties,
            VideoMediaProcessor processor,
            BalancedAssetSelectionService balancedSelection) {
        this.properties = properties;
        this.processor = processor;
        this.balancedSelection = balancedSelection;
    }

    VideoCatalogService(RunningHubProperties properties, VideoMediaProcessor processor) {
        this(properties, processor, null);
    }

    public synchronized List<VideoAssetView> list() {
        return list(selectedFolderOrDefault());
    }

    public List<VideoAssetView> list(String folder) {
        Path directory = resolveFolder(folder);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(VideoCatalogService::isVideo)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::view)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地视频目录失败：" + directory, exception);
        }
    }

    public synchronized List<VideoFolderView> folders() {
        Path root = directory();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var children = Files.list(root)) {
            List<VideoFolderView> result = children
                    .filter(Files::isDirectory)
                    .filter(path -> !listFiles(path).isEmpty())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(path -> new VideoFolderView(path.getFileName().toString(), listFiles(path).size(), false))
                    .toList();
            if (result.isEmpty() && !listFiles(root).isEmpty()) {
                ensureValidSourceFolders(List.of(new VideoFolderView(ROOT_FOLDER, listFiles(root).size(), false)));
                return List.of(new VideoFolderView(ROOT_FOLDER, listFiles(root).size(), true));
            }
            ensureValidSourceFolders(result);
            return result.stream()
                    .map(folder -> new VideoFolderView(folder.name(), folder.videoCount(), selectedSourceFolders.contains(folder.name())))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read local video folders: " + root, exception);
        }
    }

    public synchronized VideoFolderView selectFolder(String folder) {
        Path path = resolveFolder(folder);
        if (!Files.isDirectory(path) || listFiles(path).isEmpty()) {
            throw new IllegalArgumentException("Video folder is empty or does not exist: " + folder);
        }
        String normalized = normalizeFolderName(folder);
        selectedFolder = normalized;
        selectedSourceFolders.clear();
        selectedSourceFolders.add(normalized);
        return folders().stream()
                .filter(item -> item.name().equals(normalized))
                .findFirst()
                .orElse(new VideoFolderView(normalized, listFiles(path).size(), true));
    }

    public synchronized String selectedFolder() {
        return selectedFolderOrDefault();
    }

    /**
     * Replaces the folders used for future video generation. The browser folder is
     * intentionally independent from this setting.
     */
    public synchronized List<VideoFolderView> selectSourceFolders(List<String> folders) {
        if (folders == null || folders.isEmpty()) {
            throw new IllegalArgumentException("At least one video source folder must be selected");
        }
        Set<String> normalized = folders.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(VideoCatalogService::normalizeFolderName)
                .collect(java.util.stream.Collectors.toSet());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one video source folder must be selected");
        }
        List<VideoFolderView> available = foldersWithoutSelection();
        Set<String> availableNames = available.stream().map(VideoFolderView::name).collect(java.util.stream.Collectors.toSet());
        if (!availableNames.containsAll(normalized)) {
            throw new IllegalArgumentException("Video source folder is empty or does not exist: " + normalized);
        }
        selectedSourceFolders.clear();
        selectedSourceFolders.addAll(normalized);
        return folders();
    }

    public synchronized Set<String> selectedSourceFolders() {
        ensureValidSourceFolders(foldersWithoutSelection());
        return Set.copyOf(selectedSourceFolders);
    }

    public Path selectBalancedVideo() {
        List<Path> videos = selectedSourceFolders().stream()
                .flatMap(folder -> listFiles(resolveFolder(folder)).stream())
                .distinct()
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        if (videos.isEmpty()) {
            throw new IllegalStateException("视频目录中没有可用视频：" + directory());
        }
        if (balancedSelection != null) {
            BalancedAssetSelectionService.Selection selected = balancedSelection.select(
                    AssetType.VIDEO,
                    videos.stream()
                            .map(path -> BalancedAssetSelectionService.Candidate.of(path, videoAssetKey(path)))
                            .toList());
            LOGGER.info(
                    "本地动作视频均衡选择 selected={} useCountBefore={} useCountAfter={} candidateCount={}",
                    selected.path().getFileName(),
                    selected.useCountBefore(),
                    selected.useCountAfter(),
                    selected.candidateCount());
            return selected.path();
        }
        return videos.get(ThreadLocalRandom.current().nextInt(videos.size()));
    }

    public Path resolve(String id) {
        return allVideoFiles().stream()
                .filter(path -> sha256(path).equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("本地视频不存在：" + id));
    }

    private VideoAssetView view(Path video) {
        try {
            String id = sha256(video);
            return new VideoAssetView(
                    id,
                    video.getFileName().toString(),
                    Files.size(video),
                    null,
                    null,
                    null,
                    "/api/video-catalog/" + id + "/content",
                    Files.getLastModifiedTime(video).toInstant());
        } catch (IOException exception) {
            throw new IllegalStateException("读取视频资料失败：" + video, exception);
        }
    }

    private List<Path> listFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(VideoCatalogService::isVideo).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地视频目录失败：" + directory, exception);
        }
    }

    private Path directory() {
        return properties.getVideoDirectory().toAbsolutePath().normalize();
    }

    private String videoAssetKey(Path path) {
        return directory().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private List<Path> allVideoFiles() {
        Path root = directory();
        if (!Files.isDirectory(root)) return List.of();
        try (var children = Files.list(root)) {
            List<Path> result = new java.util.ArrayList<>(listFiles(root));
            children.filter(Files::isDirectory).forEach(path -> result.addAll(listFiles(path)));
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read local video directory: " + root, exception);
        }
    }

    private String selectedFolderOrDefault() {
        String current = selectedFolder;
        if (current != null && !current.isBlank()) {
            try {
                Path path = resolveFolder(current);
                if (Files.isDirectory(path) && !listFiles(path).isEmpty()) return current;
            } catch (IllegalArgumentException ignored) {
                // A folder may have been removed while the application is running.
            }
        }
        List<VideoFolderView> available = foldersWithoutSelection();
        String fallback = available.isEmpty() ? ROOT_FOLDER : available.get(0).name();
        selectedFolder = fallback;
        ensureValidSourceFolders(available);
        return fallback;
    }

    private void ensureValidSourceFolders(List<VideoFolderView> available) {
        Set<String> availableNames = available.stream().map(VideoFolderView::name).collect(java.util.stream.Collectors.toSet());
        selectedSourceFolders.retainAll(availableNames);
        if (selectedSourceFolders.isEmpty() && !available.isEmpty()) {
            selectedSourceFolders.add(available.get(0).name());
        }
    }

    private List<VideoFolderView> foldersWithoutSelection() {
        Path root = directory();
        if (!Files.isDirectory(root)) return List.of();
        try (var children = Files.list(root)) {
            List<VideoFolderView> result = children
                    .filter(Files::isDirectory)
                    .filter(path -> !listFiles(path).isEmpty())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(path -> new VideoFolderView(path.getFileName().toString(), listFiles(path).size(), false))
                    .toList();
            if (result.isEmpty() && !listFiles(root).isEmpty()) {
                return List.of(new VideoFolderView(ROOT_FOLDER, listFiles(root).size(), false));
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read local video folders: " + root, exception);
        }
    }

    private Path resolveFolder(String folder) {
        String normalized = normalizeFolderName(folder);
        if (ROOT_FOLDER.equals(normalized)) return directory();
        Path root = directory();
        Path candidate = root.resolve(normalized).normalize();
        if (!candidate.getParent().equals(root) || !Files.isDirectory(candidate)) {
            throw new IllegalArgumentException("Invalid video folder: " + folder);
        }
        return candidate;
    }

    private static String normalizeFolderName(String folder) {
        if (folder == null || folder.isBlank()) return ROOT_FOLDER;
        String value = folder.trim();
        if (ROOT_FOLDER.equals(value)) return ROOT_FOLDER;
        if (value.contains("\\") || value.contains("/") || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Invalid video folder: " + folder);
        }
        return value;
    }

    private static boolean isVideo(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm")
                || name.endsWith(".m4v") || name.endsWith(".mkv");
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("计算视频摘要失败：" + file, exception);
        }
    }

    public record VideoAssetView(
            String id,
            String fileName,
            long sizeBytes,
            Double durationSeconds,
            Integer width,
            Integer height,
            String videoUrl,
            Instant updatedAt) {}

    public record VideoFolderView(String name, int videoCount, boolean selected) {}
}
