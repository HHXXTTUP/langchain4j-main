package dev.learning.fashionagent.director;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.config.GeminiProperties;
import dev.learning.fashionagent.service.GeminiTextClient;
import dev.learning.fashionagent.script.MyScriptService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ShortDramaDirectorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortDramaDirectorService.class);
    private static final int MAX_CHARS = 80_000;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private final GeminiProperties geminiProperties;
    private final GeminiTextClient geminiClient;
    private final ShortDramaDirectorRepository repository;
    private final ShortDramaDirectorPromptLibrary prompts;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final MyScriptService myScripts;

    public ShortDramaDirectorService(GeminiProperties geminiProperties, ShortDramaDirectorRepository repository,
                                     ShortDramaDirectorPromptLibrary prompts, ObjectMapper mapper,
                                     @Qualifier("storyVideoExecutor") Executor executor,
                                     GeminiTextClient geminiClient, MyScriptService myScripts) {
        this.geminiProperties = geminiProperties; this.geminiClient = geminiClient;
        this.repository = repository; this.prompts = prompts; this.mapper = mapper;
        this.executor = executor; this.myScripts = myScripts;
        LOGGER.info("短剧导演 Gemini 服务初始化 model={} baseUrl={}", geminiProperties.getModel(), geminiProperties.getBaseUrl());
    }

    public View create(String rawMode, String rawText, MultipartFile file, String tier, String platform, String ratio) {
        ShortDramaDirectorMode mode = ShortDramaDirectorMode.parse(rawMode);
        String fileText = extract(file);
        String text = merge(rawText, fileText);
        if (text.isBlank()) throw new IllegalArgumentException("请输入创作内容或上传可读取的文本文件");
        String apiKey = geminiProperties.requiredApiKey(); // Capture the Gemini key before entering the executor.
        Job job = new Job(UUID.randomUUID(), mode, file != null && !file.isEmpty() ? "FILE" : "TEXT", fileName(file), text, tier, platform, ratio, Instant.now());
        save(job);
        GeminiProperties.ApiKeyDiagnostic keyDiagnostic = geminiProperties.apiKeyDiagnostic();
        LOGGER.info("短剧导演任务进入后台队列 id={} mode={} sourceType={} sourceChars={} fileName={} geminiKeySource={} geminiKeyFingerprint={} geminiKeyLength={}",
                job.id, job.mode, job.sourceType, job.sourceText.length(), job.sourceFileName,
                keyDiagnostic.source(), keyDiagnostic.fingerprint(), keyDiagnostic.length());
        executor.execute(() -> run(job, apiKey));
        return job.view();
    }
    public View retry(UUID id) {
        Job job = require(id);
        if ("RUNNING".equals(job.status)) return job.view();
        String apiKey = geminiProperties.requiredApiKey();
        job.status = "QUEUED"; job.message = "已重新排队"; job.error = null; save(job);
        executor.execute(() -> run(job, apiKey));
        return job.view();
    }
    public List<View> list() { return repository.list().stream().map(View::from).toList(); }
    public View get(UUID id) { return require(id).view(); }

    private void run(Job job, String apiKey) {
        try {
            job.status = "RUNNING"; job.message = "正在按短剧导演规则调用 Gemini"; save(job);
            LOGGER.info("短剧导演后台任务开始 id={} mode={} thread={} sourceChars={}",
                    job.id, job.mode, Thread.currentThread().getName(), job.sourceText.length());
            LOGGER.info("短剧导演规则提示词开始构建 id={} mode={} tier={} platform={} ratio={}",
                    job.id, job.mode, job.tier, job.platform, job.ratio);
            String system = prompts.instructions(job.mode, job.tier, job.platform, job.ratio);
            LOGGER.info("短剧导演规则提示词构建完成 id={} systemChars={}", job.id, system.length());
            JsonNode response = geminiClient.call("短剧导演/" + job.mode, system, job.sourceText, apiKey);
            String result = text(response);
            if (result == null || result.isBlank()) throw new IllegalStateException("Gemini 未返回短剧导演结果");
            job.result = result.trim(); job.status = "SUCCESS"; job.message = "短剧导演输出已完成"; save(job);
            if (job.mode == ShortDramaDirectorMode.FULL_EPISODE || job.mode == ShortDramaDirectorMode.SCREENPLAY) {
                myScripts.archiveInitial(job.id, job.sourceText, job.result, job.tier, job.platform, job.ratio);
            }
        } catch (Exception e) {
            LOGGER.error("短剧导演 Gemini 调用失败 id={} mode={} reason={}", job.id, job.mode, rootMessage(e), e);
            job.status = "FAILED"; job.message = "生成失败，可点击重试"; job.error = rootMessage(e); save(job);
        }
    }

    private String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) return "";
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("上传文件不能超过 8 MB");
        String name = fileName(file).toLowerCase(Locale.ROOT);
        try {
            String result;
            if (name.endsWith(".docx")) try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file.getBytes())); XWPFWordExtractor ext = new XWPFWordExtractor(doc)) { result = ext.getText(); }
            else if (name.endsWith(".pdf")) try (PDDocument doc = PDDocument.load(file.getBytes())) { result = new PDFTextStripper().getText(doc); }
            else if (name.matches(".*\\.(txt|md|json|csv|srt|vtt|xml|yaml|yml)$")) result = new String(file.getBytes(), StandardCharsets.UTF_8);
            else throw new IllegalArgumentException("仅支持 TXT、MD、JSON、CSV、SRT、VTT、DOCX、PDF 文件");
            return limit(result);
        } catch (IOException e) { throw new IllegalArgumentException("读取上传文件失败：" + rootMessage(e), e); }
    }
    private static String merge(String input, String file) { return limit(((input == null ? "" : input.trim()) + (file == null || file.isBlank() ? "" : "\n\n--- 上传文件内容 ---\n" + file)).trim()); }
    private static String limit(String value) { return value == null ? "" : value.length() <= MAX_CHARS ? value : value.substring(0, MAX_CHARS) + "\n\n[内容已按 80000 字符截断]"; }
    private Job require(UUID id) { return repository.find(id).map(Job::from).orElseThrow(() -> new IllegalArgumentException("短剧导演任务不存在")); }
    private void save(Job job) { job.updatedAt = Instant.now(); repository.save(job.snapshot()); }
    private static String fileName(MultipartFile file) { return file == null || file.getOriginalFilename() == null ? null : file.getOriginalFilename().replaceAll("[\\r\\n]", ""); }
    private static String text(JsonNode response) { if (response == null) return null; JsonNode node = response.at("/candidates/0/content/parts/0/text"); if (node.isTextual()) return node.asText(); node = response.at("/choices/0/message/content"); if (node.isTextual()) return node.asText(); if (node.isArray()) for (JsonNode part : node) if (part.has("text")) return part.path("text").asText(); node = response.at("/output/0/content/0/text"); if (node.isTextual()) return node.asText(); node = response.at("/output/text"); return node.isTextual() ? node.asText() : null; }
    private static String rootMessage(Throwable e) { Throwable current = e; while (current.getCause() != null) current = current.getCause(); return current.getMessage() == null ? current.toString() : current.getMessage(); }
    public record View(UUID id, String mode, String sourceType, String sourceFileName, String sourceText, String actionTier, String platform, String aspectRatio, String status, String message, String result, String error, Instant createdAt, Instant updatedAt) { static View from(ShortDramaDirectorSnapshot s) { return new View(s.id(), s.mode(), s.sourceType(), s.sourceFileName(), s.sourceText(), s.actionTier(), s.platform(), s.aspectRatio(), s.status(), s.message(), s.result(), s.error(), s.createdAt(), s.updatedAt()); } }
    private static final class Job { final UUID id; final ShortDramaDirectorMode mode; final String sourceType, sourceFileName, sourceText, tier, platform, ratio; final Instant createdAt; volatile Instant updatedAt; volatile String status = "QUEUED", message = "任务已进入队列", result, error; Job(UUID id, ShortDramaDirectorMode mode, String sourceType, String sourceFileName, String sourceText, String tier, String platform, String ratio, Instant createdAt) { this.id=id;this.mode=mode;this.sourceType=sourceType;this.sourceFileName=sourceFileName;this.sourceText=sourceText;this.tier=tier;this.platform=platform;this.ratio=ratio;this.createdAt=createdAt;this.updatedAt=createdAt; } ShortDramaDirectorSnapshot snapshot(){return new ShortDramaDirectorSnapshot(id,mode.name(),sourceType,sourceFileName,sourceText,tier,platform,ratio,status,message,result,error,createdAt,updatedAt);} View view(){return View.from(snapshot());} static Job from(ShortDramaDirectorSnapshot s){ Job j=new Job(s.id(),ShortDramaDirectorMode.parse(s.mode()),s.sourceType(),s.sourceFileName(),s.sourceText(),s.actionTier(),s.platform(),s.aspectRatio(),s.createdAt());j.updatedAt=s.updatedAt();j.status=s.status();j.message=s.message();j.result=s.result();j.error=s.error();return j;} }
}
