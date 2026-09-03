package dev.learning.fashionagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import dev.learning.fashionagent.config.QwenProperties;
import dev.learning.fashionagent.video.QwenVideoScriptRepository;
import dev.learning.fashionagent.video.QwenVideoScriptSnapshot;
import dev.learning.fashionagent.video.SnapAnyVideoImportService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class QwenVideoScriptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(QwenVideoScriptService.class);
    private static final String FIXED_PROMPT = """
            你身为资深影视分镜和镜头语言分析师，现在要对我上传的视频内容进行专业级的逐帧反推分镜拆解。严格按要求输出，精准标注专业镜头术语，要贴合主流视频节奏，给出可复刻的同款拍摄脚本。

            1. 格式要简洁、条理清晰，别废话，不要输出多余无效描述。时长压缩在30秒之内，不用体现“分镜头”“时长”等栏目，直接输出一段完整、可交给视频生成接口使用的脚本描述。
            2. 文案开头先分析视频里的主要人物，不要添加人物样貌标签，直接使用“男子1”“女子1”等编号代替人物，并按人物在视频中首次出现的顺序分配编号。人物参考信息必须使用以下固定格式：女子1参考虚拟图 1 成写实风格人物（去掉面部黑色遮罩和网格），男子1参考虚拟图 2 成写实风格人物（去掉面部黑色遮罩和网格）。识别到多个同类人物时继续使用“男子2”“女子2”等编号，并为每个人分配递增的参考虚拟图编号。开头的人物参考信息后紧接固定句：对话内容加上字幕,内容较长则需要换行。
            3. 脚本里不要描述人物的服装、发型、脸部五官或其他样貌特征，让视频生成模型完全按照对应上传图片1:1复刻人物。脚本只描述环境、镜头运动、景别、人物动作、表情变化、光影氛围、画面节奏和对白语气；多人对白要分别标明男子1、女子1等说话人和具体内容，不能遗漏。
            4. 文案中凡是人物实际说出的对白，必须使用 ASCII 双引号 "..." 包裹完整内容，例如：女子1说："你终于回来了。"；旁白、动作和环境描述不要放进双引号。对白较长时仍须完整保留在同一组双引号内，并按要求换行加字幕。
            """.strip();

    private final QwenProperties properties;
    private final SnapAnyVideoImportService snapAny;
    private final QwenVideoScriptRepository repository;
    private final Executor executor;
    private final QwenRestClientProvider clients;
    private final URI chatCompletionsEndpoint;

    public QwenVideoScriptService(QwenProperties properties, SnapAnyVideoImportService snapAny,
                                  QwenVideoScriptRepository repository,
                                  @Qualifier("storyVideoExecutor") Executor executor,
                                  QwenRestClientProvider clients) {
        this.properties = properties; this.snapAny = snapAny; this.repository = repository;
        this.executor = executor; this.clients = clients;
        String base = properties.getBaseUrl().toString().replaceAll("/+$", "");
        this.chatCompletionsEndpoint = URI.create(base + "/chat/completions");
        LOGGER.info("千问视频脚本服务初始化 model={} chatCompletionsEndpoint={} connectTimeout={} readTimeout={} route={} videoProxyEnabled={}",
                properties.getVideoScriptModel(), chatCompletionsEndpoint, properties.getConnectTimeout(), properties.getReadTimeout(),
                properties.isVideoScriptProxyEnabled() ? clients.configuredRoute() : "direct-only", properties.isVideoScriptProxyEnabled());
    }

    public QwenVideoScriptView create(String address, boolean parseImmediately) {
        if (address == null || address.isBlank()) throw new IllegalArgumentException("请输入视频地址");
        String apiKey = parseImmediately ? properties.requiredApiKey() : null;
        Job job = new Job(UUID.randomUUID(), address.trim(), Instant.now()); save(job);
        executor.execute(() -> download(job, parseImmediately, apiKey));
        return job.view();
    }

    public QwenVideoScriptView create(String address) { return create(address, true); }

    public List<QwenVideoScriptView> list() { return repository.list().stream().map(QwenVideoScriptView::from).toList(); }

    public QwenVideoScriptView get(UUID id) { return require(id).view(); }

    public QwenVideoScriptView generate(UUID id) {
        LOGGER.info("收到千问视频脚本分析请求 id={}", id);
        Job job = require(id);
        String apiKey = properties.requiredApiKey();
        if (job.videoPath == null || !Files.isRegularFile(job.videoPath)) throw new IllegalStateException("视频尚未下载完成");
        if ("ANALYZING".equals(job.status)) return job.view();
        job.status = "ANALYZING"; job.message = "正在调用千问分析脚本"; job.error = null; save(job);
        executor.execute(() -> analyze(job, apiKey));
        return job.view();
    }

    public Path video(UUID id) {
        Job job = require(id);
        if (job.videoPath == null || !Files.isRegularFile(job.videoPath)) throw new IllegalStateException("视频尚未下载完成");
        return job.videoPath;
    }

    private void download(Job job, boolean parseImmediately, String apiKey) {
        Path work = Path.of(properties.getOutputDirectory()).toAbsolutePath().normalize().resolve(job.id.toString());
        try {
            Files.createDirectories(work); job.status = "DOWNLOADING"; job.message = "正在通过 SnapAny 解析并下载视频"; save(job);
            Path video = snapAny.downloadFirst(job.address, work);
            long size = Files.size(video);
            if (size <= 0 || size > properties.getMaxVideoBytes()) throw new IllegalStateException("视频文件大小不符合千问接口限制：" + size + " bytes");
            job.videoPath = video; job.sourceFileName = video.getFileName().toString(); job.status = "DOWNLOADED";
            job.message = parseImmediately ? "视频下载完成，正在准备千问分析" : "视频下载完成，可点击生成文案"; save(job);
            if (parseImmediately) { job.status = "ANALYZING"; job.message = "正在调用千问分析脚本"; save(job); analyze(job, apiKey); }
        } catch (Exception e) { fail(job, "视频下载失败", e); }
    }

    private void analyze(Job job, String apiKey) {
        try {
            job.script = callQwenOnce(job.videoPath, job, apiKey);
            Files.writeString(job.videoPath.resolveSibling("script.txt"), job.script, StandardCharsets.UTF_8);
            job.status = "SUCCESS"; job.message = "视频脚本生成完成"; save(job);
        } catch (Exception e) {
            LOGGER.error("千问视频脚本分析失败，视频文件保留不变 id={} file={}", job.id, job.videoPath, e);
            fail(job, "视频脚本生成失败", e);
        }
    }

    private String callQwenOnce(Path video, Job job, String apiKey) throws IOException {
        // Automatic retries are intentionally disabled. A failed request is
        // terminal until the user explicitly clicks "再次分析".
        job.message = "正在调用千问分析脚本（单次请求）";
        save(job);
        try {
            return callQwen(video, apiKey);
        } catch (RuntimeException exception) {
            LOGGER.warn("千问视频脚本分析请求失败（不会自动重试） id={} reason={}",
                    job.id, rootMessage(exception), exception);
            throw exception;
        }
    }

    private String callQwen(Path video, String apiKey) throws IOException {
        byte[] bytes = Files.readAllBytes(video);
        String mime = Files.probeContentType(video);
        if (mime == null || !mime.startsWith("video/")) mime = "video/mp4";
        String videoData = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> videoPart = Map.of(
                "type", "video_url",
                "video_url", Map.of("url", videoData),
                "fps", 2);
        Map<String, Object> textPart = Map.of("type", "text", "text", FIXED_PROMPT);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(videoPart, textPart));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getVideoScriptModel());
        body.put("messages", List.of(message));
        body.put("stream", false);
        if (properties.isThinkingEnabled()) body.put("enable_thinking", true);
        QwenRestClientProvider.Selection selection = properties.isVideoScriptProxyEnabled() ? clients.select() : clients.selectDirect();
        QwenProperties.ApiKeyDiagnostic key = properties.apiKeyDiagnostic(apiKey);
        long started = System.nanoTime();
        LOGGER.info("千问视频脚本分析请求发送 operation=QWEN_VIDEO_SCRIPT method=POST endpoint={} route={} model={} file={} mime={} videoBytes={} encodedBytes={} requestBodyBytes~={} videoFps=2 thinking={} autoRetry=false keySource={} keyFingerprint={} keyLength={}",
                chatCompletionsEndpoint, selection.route(), properties.getVideoScriptModel(), video, mime, bytes.length,
                videoData.length(), videoData.length() + FIXED_PROMPT.length() + 500, properties.isThinkingEnabled(), key.source(), key.fingerprint(), key.length());
        LOGGER.info("千问视频脚本分析请求详情 operation=QWEN_VIDEO_SCRIPT headers=[Content-Type: application/json, Authorization: Bearer **REDACTED**] bodySummary={model={},messages=1,videoUrlDataChars={},promptChars={},stream=false}",
                properties.getVideoScriptModel(), videoData.length(), FIXED_PROMPT.length());
        try {
        JsonNode response = selection.client().post().uri(chatCompletionsEndpoint)
                .headers(h -> {
                    h.setBearerAuth(apiKey);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                    h.set("User-Agent", "fashion-image-agent/1.0");
                }).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .onStatus(HttpStatusCode::isError, (request, result) -> {
                    String error = readBody(result);
                    LOGGER.error("千问视频脚本分析 HTTP 错误 operation=QWEN_VIDEO_SCRIPT status={} endpoint={} route={} durationMs={} responseBodyChars={} responseBody={}", result.getStatusCode().value(), chatCompletionsEndpoint, selection.route(), elapsedMillis(started), error.length(), truncate(error));
                    throw new IllegalStateException("千问接口请求失败（HTTP " + result.getStatusCode().value() + "）：" + truncate(error));
                }).body(JsonNode.class);
        LOGGER.info("千问视频脚本分析响应成功 operation=QWEN_VIDEO_SCRIPT file={} route={} durationMs={} responsePresent={} responseChars={}", video, selection.route(), elapsedMillis(started), response != null, response == null ? 0 : response.toString().length());
        if (response != null && response.has("code") && !response.get("code").asText().isBlank() && !"0".equals(response.get("code").asText())) throw new IllegalStateException("千问接口返回错误：" + response.path("message").asText(response.path("code").asText()));
        String text = extractText(response); if (text == null || text.isBlank()) throw new IllegalStateException("千问接口未返回脚本文本"); return text.trim();
        } catch (RuntimeException exception) {
            LOGGER.error("千问视频脚本分析请求失败 operation=QWEN_VIDEO_SCRIPT endpoint={} route={} durationMs={} reason={}", chatCompletionsEndpoint, selection.route(), elapsedMillis(started), rootMessage(exception), exception);
            throw exception;
        }
    }

    private Job require(UUID id) { return repository.find(id).map(Job::from).orElseThrow(() -> new IllegalArgumentException("视频脚本任务不存在")); }
    private void save(Job job) { Instant now = Instant.now(); job.updatedAt = now; repository.save(new QwenVideoScriptSnapshot(job.id, job.address, job.sourceFileName, job.videoPath, job.status, job.message, job.script, job.error, job.createdAt, now)); }
    private void fail(Job job, String message, Exception error) {
        job.status = "FAILED";
        job.message = "视频脚本生成失败".equals(message) && job.videoPath != null
                ? "视频已保留，分析失败，可点击再次分析"
                : message;
        job.error = rootMessage(error);
        save(job);
    }
    private static String extractText(JsonNode response) {
        if (response == null) return null;
        JsonNode n = response.at("/output/0/content/0/text");
        if (n.isTextual()) return n.asText();
        n = response.at("/output/0/content");
        if (n.isArray()) for (JsonNode p : n) if (p.has("text")) return p.get("text").asText();
        n = response.at("/choices/0/message/content");
        if (n.isTextual()) return n.asText();
        if (n.isArray()) for (JsonNode p : n) if (p.has("text")) return p.get("text").asText();
        n = response.at("/output/choices/0/message/content/0/text");
        if (n.isTextual()) return n.asText();
        n = response.at("/output/text");
        if (n.isTextual()) return n.asText();
        return null;
    }
    private static String rootMessage(Throwable e) { Throwable c=e; while(c.getCause()!=null)c=c.getCause(); return c.getMessage()==null?c.toString():c.getMessage(); }
    private static String readBody(org.springframework.http.client.ClientHttpResponse response) { try { return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8); } catch (IOException exception) { return "<无法读取响应体: " + rootMessage(exception) + ">"; } }
    private static String truncate(String value) { return value == null ? "" : value.length() <= 8000 ? value : value.substring(0, 8000) + "...(truncated)"; }
    private static long elapsedMillis(long started) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    public record QwenVideoScriptView(UUID id, String address, String sourceFileName, String status, String message, String script, String error, Instant createdAt, Instant updatedAt) {
        static QwenVideoScriptView from(QwenVideoScriptSnapshot s) { return new QwenVideoScriptView(s.id(),s.address(),s.sourceFileName(),s.status(),s.message(),s.script(),s.error(),s.createdAt(),s.updatedAt()); }
    }
    private static final class Job {
        private final UUID id; private final String address; private final Instant createdAt; private volatile Instant updatedAt; private volatile String sourceFileName; private volatile Path videoPath; private volatile String status="QUEUED"; private volatile String message="已接收视频任务"; private volatile String script; private volatile String error;
        private Job(UUID id,String address,Instant createdAt){this.id=id;this.address=address;this.createdAt=createdAt;this.updatedAt=createdAt;}
        private QwenVideoScriptView view(){return new QwenVideoScriptView(id,address,sourceFileName,status,message,script,error,createdAt,updatedAt);}
        private static Job from(QwenVideoScriptSnapshot s){Job j=new Job(s.id(),s.address(),s.createdAt());j.sourceFileName=s.sourceFileName();j.videoPath=s.videoPath();j.status=s.status();j.message=s.message();j.script=s.script();j.error=s.error();j.updatedAt=s.updatedAt();return j;}
    }
}
