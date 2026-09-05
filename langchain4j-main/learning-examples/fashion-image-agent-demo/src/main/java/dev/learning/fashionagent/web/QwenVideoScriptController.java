package dev.learning.fashionagent.web;

import dev.learning.fashionagent.service.QwenVideoScriptService;
import dev.learning.fashionagent.service.QwenVideoScriptService.QwenVideoScriptView;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/qwen-video-scripts")
public class QwenVideoScriptController {
    private static final Logger LOGGER = LoggerFactory.getLogger(QwenVideoScriptController.class);
    private final QwenVideoScriptService service;
    public QwenVideoScriptController(QwenVideoScriptService service) { this.service = service; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<QwenVideoScriptView> create(@RequestBody(required = false) CreateRequest request) {
        if (request == null) throw new IllegalArgumentException("address is required");
        LOGGER.info("千问视频脚本任务创建请求 parse={} addressPresent={}", request.shouldParse(),
                request.address() != null && !request.address().isBlank());
        return ResponseEntity.accepted().body(service.create(request.address(), request.shouldParse()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<QwenVideoScriptView> upload(@RequestPart("video") MultipartFile video,
                                                @RequestParam(value = "parse", defaultValue = "true") boolean parse) {
        LOGGER.info("千问视频脚本本地上传请求 parse={} file={} size={} contentType={}", parse,
                video == null ? null : video.getOriginalFilename(), video == null ? 0 : video.getSize(),
                video == null ? null : video.getContentType());
        return ResponseEntity.accepted().body(service.create(video, parse));
    }

    @GetMapping
    List<QwenVideoScriptView> list() { return service.list(); }

    @PostMapping("/{id}/generate")
    ResponseEntity<QwenVideoScriptView> generate(@PathVariable UUID id) {
        LOGGER.info("千问视频脚本生成接口请求 id={}", id);
        return ResponseEntity.accepted().body(service.generate(id));
    }

    @GetMapping("/{id}/video")
    ResponseEntity<FileSystemResource> video(@PathVariable UUID id) throws IOException {
        Path path = service.video(id);
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok().contentType(contentType == null ? MediaType.parseMediaType("video/mp4") : MediaType.parseMediaType(contentType)).body(new FileSystemResource(path));
    }

    @GetMapping("/{id}")
    QwenVideoScriptView get(@PathVariable UUID id) { return service.get(id); }

    public record CreateRequest(String address, Boolean parse) {
        public boolean shouldParse() { return parse == null || parse; }
    }
}
