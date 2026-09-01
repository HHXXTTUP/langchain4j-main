package dev.learning.fashionagent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DirectOutfitReplacementService {
    private static final String DEFAULT_PROMPT = "让图1人物穿上图2服装，严格保留图1人物的脸部五官、脸型、眼神、身体姿势、肢体动作、人物比例和原图背景；完整迁移图2的发型、服饰、配饰、材质、颜色和细节；保持人物身份、姿势、环境和构图不变，只替换服装造型。";
    private final ClothingReplacementService replacement;
    private final ImageTransferService transfer;
    private final Executor executor;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public DirectOutfitReplacementService(ClothingReplacementService replacement, ImageTransferService transfer,
                                          @Qualifier("storyVideoExecutor") Executor executor) {
        this.replacement = replacement; this.transfer = transfer; this.executor = executor;
    }

    public DirectOutfitView create(MultipartFile person, MultipartFile clothing, String prompt) {
        if (person == null || person.isEmpty()) throw new IllegalArgumentException("请上传人物原图");
        if (clothing == null || clothing.isEmpty()) throw new IllegalArgumentException("请上传服装参考图");
        UUID id = UUID.randomUUID(); Path work = Path.of("generated", "direct-outfit", id.toString()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(work);
            Path personPath = work.resolve("person" + extension(person.getOriginalFilename()));
            Path clothingPath = work.resolve("clothing" + extension(clothing.getOriginalFilename()));
            person.transferTo(personPath); clothing.transferTo(clothingPath);
            Job job = new Job(id, person.getOriginalFilename(), clothing.getOriginalFilename(), Instant.now()); jobs.put(id, job);
            String finalPrompt = prompt == null || prompt.isBlank() ? DEFAULT_PROMPT : prompt.trim();
            executor.execute(() -> run(job, personPath, clothingPath, finalPrompt));
            return job.view();
        } catch (IOException e) { throw new IllegalStateException("无法保存换装输入图片", e); }
    }
    public DirectOutfitView get(UUID id) { Job job = jobs.get(id); if (job == null) throw new IllegalArgumentException("换装任务不存在"); return job.view(); }
    public Path output(UUID id) { Job job = jobs.get(id); if (job == null || job.output == null || !Files.isRegularFile(job.output)) throw new IllegalStateException("换装图片尚未生成完成"); return job.output; }
    private void run(Job job, Path person, Path clothing, String prompt) {
        try {
            job.status = "PROCESSING"; job.message = "正在上传人物图和服装图到 RunningHub";
            var uploaded = replacement.upload(person, clothing, ignored -> {});
            job.message = "正在执行换装工作流";
            java.net.URI remote = replacement.replace(uploaded, prompt, ignored -> {});
            job.output = transfer.downloadRemote(remote, job.id, "direct-outfit");
            job.status = "SUCCESS"; job.message = "换装完成";
        } catch (Exception e) { job.status = "FAILED"; job.message = "换装失败"; job.error = rootMessage(e); }
    }
    private static String extension(String name) { if (name == null) return ".png"; int i = name.lastIndexOf('.'); return i < 0 ? ".png" : name.substring(i).toLowerCase(); }
    private static String rootMessage(Throwable e) { Throwable c=e; while(c.getCause()!=null)c=c.getCause(); return c.getMessage()==null?c.toString():c.getMessage(); }
    public record DirectOutfitView(UUID id, String personFileName, String clothingFileName, String status, String message, String error, String outputUrl, String outputFileName, Instant createdAt) {}
    private static final class Job {
        private final UUID id; private final String person; private final String clothing; private final Instant created; private volatile String status="QUEUED"; private volatile String message="已接收换装任务"; private volatile String error; private volatile Path output;
        private Job(UUID id,String person,String clothing,Instant created){this.id=id;this.person=person;this.clothing=clothing;this.created=created;}
        private DirectOutfitView view(){return new DirectOutfitView(id,person,clothing,status,message,error,output==null?null:"/api/direct-outfit-replacements/"+id+"/output",output==null?null:output.getFileName().toString(),created);}
    }
}
