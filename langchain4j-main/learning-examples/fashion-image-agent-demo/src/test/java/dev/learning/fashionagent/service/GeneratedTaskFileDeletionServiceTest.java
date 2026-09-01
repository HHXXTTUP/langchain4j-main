package dev.learning.fashionagent.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.video.VideoResultPackageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class GeneratedTaskFileDeletionServiceTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDirectory;

    @Test
    void shouldDeleteOnlyTheRequestedImageJobDirectory() throws Exception {
        RunningHubProperties properties = new RunningHubProperties();
        properties.setGeneratedDirectory(tempDirectory);
        GeneratedTaskFileDeletionService service = new GeneratedTaskFileDeletionService(properties);
        UUID requestedId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Path requestedDirectory = tempDirectory.resolve("jobs").resolve(requestedId.toString());
        Path otherDirectory = tempDirectory.resolve("jobs").resolve(otherId.toString());
        Files.createDirectories(requestedDirectory.resolve("nested"));
        Files.writeString(requestedDirectory.resolve("nested").resolve("result.txt"), "generated");
        Files.createDirectories(otherDirectory);
        Files.writeString(otherDirectory.resolve("keep.txt"), "keep");

        service.deleteImageJobDirectory(requestedId);

        assertFalse(Files.exists(requestedDirectory));
        assertTrue(Files.isRegularFile(otherDirectory.resolve("keep.txt")));
    }

    @Test
    void shouldDeleteTheVideoJobAndItsExportPackage() throws Exception {
        Path generatedRoot = tempDirectory.resolve("generated");
        Path exportRoot = tempDirectory.resolve("exports");
        RunningHubProperties properties = new RunningHubProperties();
        properties.setGeneratedDirectory(generatedRoot);
        properties.setVideoExportDirectory(exportRoot);
        GeneratedTaskFileDeletionService service = new GeneratedTaskFileDeletionService(properties);
        UUID jobId = UUID.randomUUID();
        Path jobDirectory = generatedRoot.resolve("video-jobs").resolve(jobId.toString());
        Path packageDirectory = exportRoot.resolve("20260808-1").toAbsolutePath().normalize();
        Path unrelatedPackage = exportRoot.resolve("20260808-2");
        Files.createDirectories(jobDirectory);
        Files.createDirectories(packageDirectory);
        Files.createDirectories(unrelatedPackage);
        Files.writeString(packageDirectory.resolve("final-video.mp4"), "delete");
        Files.writeString(unrelatedPackage.resolve("final-video.mp4"), "keep");
        Files.writeString(
                jobDirectory.resolve(VideoResultPackageService.MANIFEST_FILE_NAME),
                packageDirectory.toString());

        service.deleteVideoJobDirectory(jobId);

        assertFalse(Files.exists(jobDirectory));
        assertFalse(Files.exists(packageDirectory));
        assertTrue(Files.isRegularFile(unrelatedPackage.resolve("final-video.mp4")));
    }
}
