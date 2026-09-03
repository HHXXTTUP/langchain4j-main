package dev.learning.fashionagent.video;

import dev.learning.fashionagent.config.RunningHubProperties;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VideoResultPackageService {

    public static final String MANIFEST_FILE_NAME = "export-package-path.txt";

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoResultPackageService.class);
    private static final DateTimeFormatter DIRECTORY_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private final RunningHubProperties properties;
    private final Clock clock;

    @Autowired
    public VideoResultPackageService(RunningHubProperties properties) {
        this(properties, Clock.system(CHINA_ZONE));
    }

    VideoResultPackageService(RunningHubProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public ExportResult export(
            UUID videoJobId,
            Path workDirectory,
            Path originalImage,
            Path outfitImage,
            Path finalVideo) {
        requireFile(originalImage, "生成人物原图");
        requireFile(outfitImage, "服装替换图");
        requireFile(finalVideo, "最终视频");

        Path exportRoot = properties.getVideoExportDirectory().toAbsolutePath().normalize();
        Path packageDirectory = null;
        try {
            Files.createDirectories(exportRoot);
            packageDirectory = createPackageDirectory(exportRoot);
            Path exportedOriginal = copy(
                    originalImage, packageDirectory.resolve("original-image" + extensionOf(originalImage)));
            Path exportedOutfit = copy(
                    outfitImage, packageDirectory.resolve("outfit-image" + extensionOf(outfitImage)));
            Path exportedVideo = copy(finalVideo, packageDirectory.resolve("final-video" + extensionOf(finalVideo)));

            Files.createDirectories(workDirectory);
            Files.writeString(
                    workDirectory.resolve(MANIFEST_FILE_NAME),
                    packageDirectory.toString());
            LOGGER.info(
                    "视频结果归档完成 jobId={} package={} original={} outfit={} video={}",
                    videoJobId,
                    packageDirectory,
                    exportedOriginal.getFileName(),
                    exportedOutfit.getFileName(),
                    exportedVideo.getFileName());
            return new ExportResult(packageDirectory, exportedOriginal, exportedOutfit, exportedVideo);
        } catch (IOException exception) {
            deleteIncompletePackage(packageDirectory);
            throw new IllegalStateException("视频结果归档失败：" + exportRoot, exception);
        }
    }

    private Path createPackageDirectory(Path exportRoot) throws IOException {
        String date = LocalDate.now(clock).format(DIRECTORY_DATE);
        for (int sequence = 1; sequence < Integer.MAX_VALUE; sequence++) {
            Path candidate = exportRoot.resolve(date + "-" + sequence);
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Another completed task already owns this sequence number.
            }
        }
        throw new IOException("当天的视频归档序号已经用尽：" + date);
    }

    private static Path copy(Path source, Path target) throws IOException {
        return Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void requireFile(Path file, String label) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalStateException(label + "不存在，不能创建视频结果包：" + file);
        }
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    private static void deleteIncompletePackage(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    LOGGER.warn("清理未完成的视频结果包失败 path={}", path, exception);
                }
            });
        } catch (IOException exception) {
            LOGGER.warn("读取未完成的视频结果包失败 path={}", directory, exception);
        }
    }

    public record ExportResult(
            Path directory,
            Path originalImage,
            Path outfitImage,
            Path finalVideo) {}
}
