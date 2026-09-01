package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.video.VideoResultPackageService;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GeneratedTaskFileDeletionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedTaskFileDeletionService.class);
    private static final Pattern VIDEO_PACKAGE_NAME = Pattern.compile("\\d{8}-\\d+");

    private final RunningHubProperties properties;

    public GeneratedTaskFileDeletionService(RunningHubProperties properties) {
        this.properties = properties;
    }

    public void deleteImageJobDirectory(UUID jobId) {
        deleteJobDirectory("jobs", jobId);
    }

    public void deleteVideoJobDirectory(UUID jobId) {
        Path jobDirectory = resolveJobDirectory("video-jobs", jobId);
        deleteExportPackage(jobDirectory);
        deleteDirectory(jobDirectory, "video-jobs", jobId);
    }

    private void deleteJobDirectory(String category, UUID jobId) {
        deleteDirectory(resolveJobDirectory(category, jobId), category, jobId);
    }

    private Path resolveJobDirectory(String category, UUID jobId) {
        Path generatedRoot = properties.getGeneratedDirectory().toAbsolutePath().normalize();
        Path categoryRoot = generatedRoot.resolve(category).normalize();
        Path target = categoryRoot.resolve(jobId.toString()).normalize();
        if (!target.startsWith(categoryRoot) || target.equals(categoryRoot)) {
            throw new IllegalStateException("Refusing to delete a path outside the generated task directory: " + target);
        }
        return target;
    }

    private void deleteExportPackage(Path jobDirectory) {
        Path manifest = jobDirectory.resolve(VideoResultPackageService.MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifest)) {
            return;
        }
        try {
            String value = Files.readString(manifest).trim();
            Path exportRoot = properties.getVideoExportDirectory().toAbsolutePath().normalize();
            Path target = Path.of(value).toAbsolutePath().normalize();
            boolean directChild = exportRoot.equals(target.getParent());
            boolean validName = target.getFileName() != null
                    && VIDEO_PACKAGE_NAME.matcher(target.getFileName().toString()).matches();
            if (!directChild || !validName) {
                throw new IllegalStateException("Refusing to delete an invalid video export package: " + target);
            }
            deleteDirectory(target, "video-export", null);
        } catch (IOException exception) {
            throw new IllegalStateException("读取视频归档清单失败：" + manifest, exception);
        }
    }

    private void deleteDirectory(Path target, String category, UUID jobId) {
        if (!Files.exists(target)) {
            return;
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
            LOGGER.info("Deleted generated task directory category={} jobId={} path={}", category, jobId, target);
        } catch (IOException exception) {
            throw new IllegalStateException("删除任务本地文件失败：" + target, exception);
        }
    }
}
