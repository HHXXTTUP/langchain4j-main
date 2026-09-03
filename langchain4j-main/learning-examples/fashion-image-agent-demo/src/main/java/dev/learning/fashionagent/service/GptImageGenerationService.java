package dev.learning.fashionagent.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import dev.learning.fashionagent.config.GptImageProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class GptImageGenerationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GptImageGenerationService.class);
    private final GptImageClient client;
    private final GptImageProperties properties;
    private final Executor executor;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public GptImageGenerationService(GptImageClient client, GptImageProperties properties, @Qualifier("storyVideoExecutor") Executor executor) {
        this.client = client; this.properties = properties; this.executor = executor;
    }

    public View generate(String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("请输入图片提示词");
        String apiKey = properties.requiredApiKey();
        UUID id = UUID.randomUUID(); Job job = new Job(id, prompt.trim(), Instant.now()); jobs.put(id, job);
        GptImageProperties.ApiKeyDiagnostic key = properties.apiKeyDiagnostic(apiKey);
        LOGGER.info("GPT 文生图任务已创建 jobId={} model={} promptChars={} keySource={} keyFingerprint={} keyLength={}",
                id, properties.getModel(), prompt.trim().length(), key.source(), key.fingerprint(), key.length());
        executor.execute(() -> run(job, apiKey));
        return job.view();
    }
    public View get(UUID id) { Job job = jobs.get(id); if (job == null) throw new IllegalArgumentException("GPT 文生图任务不存在"); return job.view(); }
    public Path output(UUID id) { Job job = jobs.get(id); if (job == null || job.output == null || !Files.isRegularFile(job.output)) throw new IllegalStateException("图片尚未生成完成"); return job.output; }

    private void run(Job job, String apiKey) {
        try {
            job.status = "PROCESSING"; job.message = "正在调用 GPT Image 2 文生图";
            LOGGER.info("GPT 文生图任务开始调用 jobId={} model={} promptChars={}", job.id, properties.getModel(), job.prompt.length());
            Path output = Path.of("generated", "gpt-images", job.id.toString(), "image.png").toAbsolutePath().normalize();
            job.output = client.generate(job.prompt, output, apiKey); job.status = "SUCCESS"; job.message = "图片生成完成";
            LOGGER.info("GPT 文生图任务完成 jobId={} output={} bytes={}", job.id, job.output, Files.size(job.output));
        } catch (Exception | LinkageError e) {
            job.status = "FAILED"; job.message = "图片生成失败"; job.error = rootMessage(e);
            LOGGER.error("GPT 文生图任务失败 jobId={} model={} durationMs={} reason={}",
                    job.id, properties.getModel(), java.time.Duration.between(job.createdAt, Instant.now()).toMillis(), rootMessage(e), e);
        }
    }
    private static String rootMessage(Throwable e) { Throwable c = e; while (c.getCause() != null) c = c.getCause(); return c.getMessage() == null ? c.toString() : c.getMessage(); }
    public record View(UUID id, String prompt, String status, String message, String error, String outputUrl, Instant createdAt) {}
    private static final class Job {
        private final UUID id; private final String prompt; private final Instant createdAt; private volatile String status = "QUEUED"; private volatile String message = "已接收文生图任务"; private volatile String error; private volatile Path output;
        private Job(UUID id, String prompt, Instant createdAt) { this.id = id; this.prompt = prompt; this.createdAt = createdAt; }
        private View view() { return new View(id, prompt, status, message, error, output == null ? null : "/api/gpt-images/" + id + "/output", createdAt); }
    }
}
