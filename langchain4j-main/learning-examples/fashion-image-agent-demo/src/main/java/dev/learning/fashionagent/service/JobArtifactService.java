package dev.learning.fashionagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.RunningHubException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JobArtifactService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobArtifactService.class);

    private final ObjectMapper objectMapper;
    private final RunningHubProperties properties;

    public JobArtifactService(ObjectMapper objectMapper, RunningHubProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Path writeJson(UUID jobId, String fileName, Object value) {
        Path target = target(jobId, fileName);
        try {
            Files.createDirectories(target.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), value);
            LOGGER.info("任务 JSON 产物已保存 jobId={} file={}", jobId, target);
            return target;
        } catch (IOException exception) {
            throw new RunningHubException("保存任务 JSON 产物失败：" + target, exception);
        }
    }

    public Path writeText(UUID jobId, String fileName, String value) {
        Path target = target(jobId, fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, value == null ? "" : value, StandardCharsets.UTF_8);
            LOGGER.info("任务文本产物已保存 jobId={} file={}", jobId, target);
            return target;
        } catch (IOException exception) {
            throw new RunningHubException("保存任务文本产物失败：" + target, exception);
        }
    }

    private Path target(UUID jobId, String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..")
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("任务产物文件名不合法：" + fileName);
        }
        return properties.getGeneratedDirectory()
                .toAbsolutePath()
                .normalize()
                .resolve("jobs")
                .resolve(jobId.toString())
                .resolve(fileName);
    }
}
