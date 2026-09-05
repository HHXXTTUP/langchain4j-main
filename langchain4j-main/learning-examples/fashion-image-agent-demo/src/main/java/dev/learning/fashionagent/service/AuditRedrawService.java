package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.RunningHubProperties;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
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

/** Runs the GPT Images audit-redraw workflow and archives its result locally. */
@Service
public class AuditRedrawService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditRedrawService.class);
    private static final String PROMPT = ""
            + "将上传的单人人物图片制作成一张横向16:9高清写实人物档案图，画布比例严格为16:9（2048x1152）。"
            + "顶部保留约10%的独立白色标题栏，水平居中，只写人物名：{CHARACTER_NAME}，使用清晰黑色无衬线字体；标题不能被裁切，除标题外禁止任何文字、编号、Logo、水印或说明。"
            + "主体区域必须严格排成6列×3行的18个等大档案格，白色背景，细黑线分隔，行列对齐，格子之间留白一致，不能把多格拼成一张大图。"
            + "18格依次展示同一人物的正面全身、正面半身、左侧面、右侧面、三分之二侧面、背面全身、背面半身、回头侧脸、面部近景、后侧发型细节、颈肩领口细节、耳饰与后侧发丝、手部、上身服装、腰部服装、腿部与鞋、背部服装、服装材质与配饰细节；第10至12格不得出现眼鼻嘴或正侧脸，每格主体完整且不被相邻格裁切。"
            + "所有格子必须是同一个人、同一套服装、同一发型发色、同一肤色年龄感和同一人体比例。{CLOTHING_RULE}不换脸、不美化成另一个人、不增加人物。"
            + "使用统一的中性白底和均匀自然棚拍光，固定人像焦段和镜头高度，真实摄影质感，皮肤纹理、发丝、布料褶皱和配饰细节清晰，不要场景、道具、电影特效或夸张姿势。"
            + "隐私遮挡必须按档案格分别执行：只要一个格子中可辨认出任何人脸或局部五官，包括正脸、侧脸、回头脸、眼睛、眉毛、鼻子、嘴唇、脸颊、耳前脸部或下颌线，就必须在该格放置一条纯黑、不透明、边缘平直清晰的横向长矩形遮挡条；绝不允许存在清晰可辨的人脸局部却没有黑条的格子。若无法合理放置黑条，就不要生成该局部人脸，改为无脸的服装、手部或背部细节。"
            + "必须严格执行以下格位遮挡分配，七个部位在整张图中各出现且只出现一次，不能自由改成全部遮眼：第1格正面全身只遮眉眼；第2格正面半身只遮鼻梁与鼻尖；第3格左侧面只遮嘴唇；第4格右侧面只遮下巴与下颌线；第5格三分之二侧面只遮左脸颊与颧骨；第8格回头侧脸只遮右脸颊与耳前区域；第9格面部近景只遮额头下缘与发际线。第6、7、10至18格必须采用背面、后侧发型、颈肩、耳后、手部、服装或鞋履等无清晰面部内容，因此不加黑条。"
            + "每一个含脸格只能遮挡上述一个指定部位，不能再叠加第二条黑条；眉眼、鼻、嘴、下颌、左脸颊、右脸颊、额头七类遮挡不得在别的格重复，尤其禁止任意两个格同时遮眼。黑条不得是短小方块，长度必须约为该格可见脸部或五官区域宽度的65%至85%，高度约为该区域高度的12%至22%，必须横跨指定部位但不能跨出当前格。"
            + "整张图的遮挡必须形成互补分布：至少一个格清楚保留眼部、一个格清楚保留鼻部、一个格清楚保留嘴部、一个格清楚保留下颌线；这些保留部位必须分散在不同格子中，同时每个含脸格仍由本格指定位置的黑条阻断完整露脸。不同角度的未遮挡部分应能互相拼合识别为同一个人的完整统一面貌。"
            + "禁止黑条跨格、遮住整张脸或整个人头、全图统一遮眼、使用马赛克、模糊或渐变；不要遮挡衣服或格子边框。生成前按格号逐格验收：先确认第1、2、3、4、5、8、9格的七条黑条分别落在七个唯一部位，再确认第6、7、10至18格没有清晰脸和多余黑块；任何规则不满足都必须在输出前修正。"
            + "只输出这一张最终档案图，不输出提示词或生成过程。";

    /** Builds the shared multi-cell audit prompt used by standalone and script-replication redraws. */
    public static String auditPromptFor(String characterName, String wardrobeInstruction) {
        String name = characterName == null || characterName.isBlank() ? "人物" : characterName.trim();
        String wardrobe = wardrobeInstruction == null || wardrobeInstruction.isBlank()
                ? "严格锁定上传图的脸型、五官结构、发型、服装颜色材质纹理、鞋子和配饰，保持原服装完全一致。"
                : "将本集服装设定落实到所有格子，并统一替换原图服装：" + wardrobeInstruction.trim()
                        + "；允许且必须改变服装、发型配饰或状态中明确要求变化的部分，脸部身份、五官结构、人体比例和未要求变化的资产保持不变。";
        return PROMPT.replace("{CHARACTER_NAME}", name).replace("{CLOTHING_RULE}", wardrobe);
    }

    /** Builds the same audit sheet without requiring an existing reference image. */
    public static String auditGenerationPromptFor(String characterName, String characterDesign) {
        String design = characterDesign == null ? "" : characterDesign.trim();
        String prompt = auditPromptFor(characterName, null)
                .replace("将上传的单人人物图片制作成", "根据给定角色设定从零生成")
                .replace("严格锁定上传图的脸型、五官结构、发型、服装颜色材质纹理、鞋子和配饰，保持原服装完全一致。",
                        "严格统一角色的脸型、五官结构、发型、服装颜色材质纹理、鞋子和配饰，所有格子保持完全一致。");
        return "角色设定：" + design + "。" + prompt;
    }

    private final RunningHubProperties properties;
    private final GptImageClient imageClient;
    private final GptImageProperties imageProperties;
    private final ImageTransferService transfer;
    private final Executor executor;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public AuditRedrawService(RunningHubProperties properties, GptImageClient imageClient, GptImageProperties imageProperties,
                              ImageTransferService transfer,
                              @Qualifier("storyVideoExecutor") Executor executor) {
        this.properties = properties;
        this.imageClient = imageClient;
        this.imageProperties = imageProperties;
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
            LOGGER.info("GPT 图生图任务已创建 jobId={} inputFile={} inputBytes={} model={}",
                    id, image.getOriginalFilename(), image.getSize(), imageProperties.getModel());
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
            job.message = "正在调用 GPT Image 2 图生图过审重绘";
            String apiKey = imageProperties.requiredApiKey();
            String characterName = characterName(job.inputFileName);
            String prompt = auditPromptFor(characterName, null);
            LOGGER.info("GPT 图生图任务开始调用 jobId={} model={} input={} inputBytes={} promptChars={}",
                    job.id, imageProperties.getModel(), input, Files.size(input), prompt.length());
            Path archiveDir = properties.getAuditRedrawOutputDirectory().toAbsolutePath().normalize();
            Files.createDirectories(archiveDir);
            Path archived = archiveDir.resolve("audit-redraw-" + Instant.now().toEpochMilli() + "-" + job.id + ".png");
            job.output = imageClient.edit(List.of(input), prompt, archived, apiKey, "2048x1152");
            job.status = "SUCCESS";
            job.message = "过审重绘完成，已保存到配置目录";
            LOGGER.info("GPT 图生图任务完成 jobId={} output={} bytes={}", job.id, job.output, Files.size(job.output));
        } catch (Exception | LinkageError exception) {
            job.status = "FAILED";
            job.message = "过审重绘失败";
            job.error = rootMessage(exception);
            LOGGER.error("GPT 图生图任务失败 jobId={} model={} durationMs={} reason={}",
                    job.id, imageProperties.getModel(), java.time.Duration.between(job.createdAt, Instant.now()).toMillis(), rootMessage(exception), exception);
        }
    }

    private static String extension(String name) {
        if (name == null) return ".png";
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return ".jpg";
        if (lower.endsWith(".webp")) return ".webp";
        return ".png";
    }

    private static String characterName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "人物";
        String fileName = originalName.trim();
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < fileName.length()) fileName = fileName.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) fileName = fileName.substring(0, dot);
        return fileName.isBlank() ? "人物" : fileName;
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
