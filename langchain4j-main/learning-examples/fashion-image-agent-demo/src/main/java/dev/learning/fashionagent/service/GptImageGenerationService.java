package dev.learning.fashionagent.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
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
        return generate(prompt, List.of());
    }

    public View generate(String prompt, List<MultipartFile> uploadedReferences) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("请输入图片提示词");
        List<MultipartFile> references = uploadedReferences == null ? List.of() : uploadedReferences.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
        if (references.size() > 16) throw new IllegalArgumentException("最多上传 16 张参考图片");
        String apiKey = properties.requiredApiKey();
        UUID id = UUID.randomUUID();
        List<Path> referencePaths = saveReferenceImages(id, references);
        Job job = new Job(id, prompt.trim(), referencePaths, Instant.now()); jobs.put(id, job);
        GptImageProperties.ApiKeyDiagnostic key = properties.apiKeyDiagnostic(apiKey);
        LOGGER.info("GPT 图片任务已创建 jobId={} model={} promptChars={} referenceCount={} keySource={} keyFingerprint={} keyLength={}",
                id, properties.getModel(), prompt.trim().length(), referencePaths.size(), key.source(), key.fingerprint(), key.length());
        executor.execute(() -> run(job, apiKey));
        return job.view();
    }
    public View get(UUID id) { Job job = jobs.get(id); if (job == null) throw new IllegalArgumentException("GPT 文生图任务不存在"); return job.view(); }
    public Path output(UUID id) { Job job = jobs.get(id); if (job == null || job.output == null || !Files.isRegularFile(job.output)) throw new IllegalStateException("图片尚未生成完成"); return job.output; }

    private void run(Job job, String apiKey) {
        try {
            job.status = "PROCESSING"; job.message = job.referenceImages.isEmpty() ? "正在调用 GPT Image 2 文生图" : "正在调用 GPT Image 2 图生图";
            LOGGER.info("GPT 图片任务开始调用 jobId={} model={} promptChars={} referenceCount={}", job.id, properties.getModel(), job.prompt.length(), job.referenceImages.size());
            Path output = Path.of("generated", "gpt-images", job.id.toString(), "image.png").toAbsolutePath().normalize();
            job.output = job.referenceImages.isEmpty()
                    ? client.generate(job.prompt, output, apiKey)
                    : client.edit(job.referenceImages, job.prompt, output, apiKey, "1024x1024");
            job.status = "SUCCESS"; job.message = job.referenceImages.isEmpty() ? "图片生成完成" : "参考图生成完成";
            LOGGER.info("GPT 文生图任务完成 jobId={} output={} bytes={}", job.id, job.output, Files.size(job.output));
        } catch (Exception | LinkageError e) {
            job.status = "FAILED"; job.message = "图片生成失败"; job.error = rootMessage(e);
            LOGGER.error("GPT 文生图任务失败 jobId={} model={} durationMs={} reason={}",
                    job.id, properties.getModel(), java.time.Duration.between(job.createdAt, Instant.now()).toMillis(), rootMessage(e), e);
        }
    }
    private static String rootMessage(Throwable e) { Throwable c = e; while (c.getCause() != null) c = c.getCause(); return c.getMessage() == null ? c.toString() : c.getMessage(); }
    public record View(UUID id, String prompt, String status, String message, String error, String outputUrl, Instant createdAt, int referenceImageCount) {}
    private static final class Job {
        private final UUID id; private final String prompt; private final List<Path> referenceImages; private final Instant createdAt; private volatile String status = "QUEUED"; private volatile String message = "已接收图片任务"; private volatile String error; private volatile Path output;
        private Job(UUID id, String prompt, List<Path> referenceImages, Instant createdAt) { this.id = id; this.prompt = prompt; this.referenceImages = List.copyOf(referenceImages); this.createdAt = createdAt; }
        private View view() { return new View(id, prompt, status, message, error, output == null ? null : "/api/gpt-images/" + id + "/output", createdAt, referenceImages.size()); }
    }

    private List<Path> saveReferenceImages(UUID jobId, List<MultipartFile> references) {
        if (references.isEmpty()) return List.of();
        Path directory = Path.of("generated", "gpt-images", jobId.toString(), "references").toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            List<Path> saved = new ArrayList<>();
            for (int i = 0; i < references.size(); i++) {
                MultipartFile file = references.get(i);
                String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
                if (!contentType.startsWith("image/")) throw new IllegalArgumentException("参考文件必须是图片");
                if (file.getSize() > 20L * 1024 * 1024) throw new IllegalArgumentException("单张参考图片不能超过 20MB");
                Path target = directory.resolve(String.format("reference-%02d%s", i + 1, extension(file.getOriginalFilename(), contentType)));
                file.transferTo(target);
                saved.add(target);
            }
            return saved;
        } catch (Exception error) {
            throw new IllegalStateException("保存参考图片失败：" + rootMessage(error), error);
        }
    }

    private static String extension(String originalName, String contentType) {
        String name = originalName == null ? "" : originalName.toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String value = name.substring(dot);
            if (value.matches("\\.(png|jpe?g|webp|gif)")) return value;
        }
        return contentType.contains("png") ? ".png" : contentType.contains("webp") ? ".webp" : ".jpg";
    }
}
