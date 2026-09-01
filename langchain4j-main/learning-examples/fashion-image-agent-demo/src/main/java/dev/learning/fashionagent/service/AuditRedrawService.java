package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import dev.learning.fashionagent.integration.runninghub.NodeInput;
import dev.learning.fashionagent.integration.runninghub.RunningHubTaskRunner;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Runs the RunningHub audit-redraw workflow and archives its result locally. */
@Service
public class AuditRedrawService {
    private static final String PROMPT = "将上传的单人人物图片制作成高清写实的16宫格人物档案图。严格保持原人物的五官、脸型、发型发色、肤色、年龄感、身材比例、服装颜色材质结构和配饰完全一致，不换脸、不改变身份，不增加人物。使用纯白背景、均匀自然光、统一镜头高度和中性档案摄影构图，输出4x4单张图片；所有人脸仅做局部纯黑矩形隐私遮挡，除遮挡区域外保留真实细节。仅输出最终图片，不输出提示词、JSON、参数、Logo或水印。比例固定16:9。";

    private final RunningHubProperties properties;
    private final RunningHubTaskRunner taskRunner;
    private final ImageTransferService transfer;
    private final Executor executor;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public AuditRedrawService(RunningHubProperties properties, RunningHubTaskRunner taskRunner,
                              ImageTransferService transfer,
                              @Qualifier("storyVideoExecutor") Executor executor) {
        this.properties = properties;
        this.taskRunner = taskRunner;
        this.transfer = transfer;
        this.executor = executor;
    }

    public AuditRedrawView create(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("请上传一张图片");
        }
        UUID id = UUID.randomUUID();
        Path work = properties.getGeneratedDirectory().toAbsolutePath().normalize()
                .resolve("audit-redraw").resolve(id.toString());
        try {
            Files.createDirectories(work);
            Path input = work.resolve("input" + extension(image.getOriginalFilename()));
            image.transferTo(input);
            Job job = new Job(id, image.getOriginalFilename(), Instant.now());
            jobs.put(id, job);
            executor.execute(() -> run(job, input));
            return job.view();
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存过审重绘输入图片", exception);
        }
    }

    public AuditRedrawView get(UUID id) {
        Job job = jobs.get(id);
        if (job == null) throw new IllegalArgumentException("过审重绘任务不存在");
        return job.view();
    }

    public Path output(UUID id) {
        Job job = jobs.get(id);
        if (job == null || job.output == null || !Files.isRegularFile(job.output)) {
            throw new IllegalStateException("过审图片尚未生成完成");
        }
        return job.output;
    }

    private void run(Job job, Path input) {
        try {
            job.status = "PROCESSING";
            job.message = "正在上传图片到 RunningHub";
            String uploaded = transfer.uploadLocal(input);
            job.message = "正在执行过审重绘工作流（16:9）";
            List<NodeInput> nodes = List.of(
                    new NodeInput("11", "image", uploaded, "image"),
                    new NodeInput("13", "aspectRatio", "16:9", "aspectRatio"),
                    new NodeInput("13", "channel", "Third-party", "channel"),
                    new NodeInput("13", "resolution", "2k", "resolution"),
                    new NodeInput("17", "text", PROMPT, "text"));
            RunningHubTaskRunner.TaskOutput result = taskRunner.run(
                    properties.getAuditRedrawAppId(), nodes, status -> job.message = status);
            Path downloaded = transfer.downloadRemote(URI.create(result.url()), job.id, "audit-redraw");
            Path archiveDir = properties.getAuditRedrawOutputDirectory().toAbsolutePath().normalize();
            Files.createDirectories(archiveDir);
            Path archived = archiveDir.resolve("audit-redraw-" + Instant.now().toEpochMilli()
                    + "-" + job.id + extension(downloaded.getFileName().toString()));
            Files.copy(downloaded, archived, StandardCopyOption.REPLACE_EXISTING);
            job.output = archived;
            job.status = "SUCCESS";
            job.message = "过审重绘完成，已保存到 E:\\AI过审图";
        } catch (Exception exception) {
            job.status = "FAILED";
            job.message = "过审重绘失败";
            job.error = rootMessage(exception);
        }
    }

    private static String extension(String name) {
        if (name == null) return ".png";
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".webp")) return ".webp";
        return ".png";
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    public record AuditRedrawView(UUID id, String inputFileName, String status, String message,
                                  String error, String outputUrl, String outputFileName, Instant createdAt) {}

    private static final class Job {
        private final UUID id;
        private final String inputFileName;
        private final Instant createdAt;
        private volatile String status = "QUEUED";
        private volatile String message = "已接收过审重绘任务";
        private volatile String error;
        private volatile Path output;

        private Job(UUID id, String inputFileName, Instant createdAt) {
            this.id = id;
            this.inputFileName = inputFileName;
            this.createdAt = createdAt;
        }

        private AuditRedrawView view() {
            return new AuditRedrawView(id, inputFileName, status, message, error,
                    output == null ? null : "/api/audit-redraw/" + id + "/output",
                    output == null ? null : output.getFileName().toString(), createdAt);
        }
    }
}
