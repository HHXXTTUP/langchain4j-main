package dev.learning.fashionagent.web;

import dev.learning.fashionagent.director.ShortDramaDirectorService;
import dev.learning.fashionagent.director.ShortDramaDirectorService.View;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/short-drama-director")
public class ShortDramaDirectorController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortDramaDirectorController.class);
    private final ShortDramaDirectorService service;
    public ShortDramaDirectorController(ShortDramaDirectorService service) { this.service = service; }

    @PostMapping(value = "/tasks", consumes = "multipart/form-data")
    ResponseEntity<View> create(@RequestParam(required = false) String mode, @RequestParam(required = false) String text,
                                @RequestParam(required = false) MultipartFile file, @RequestParam(required = false) String actionTier,
                                @RequestParam(required = false) String platform, @RequestParam(required = false) String aspectRatio) {
        LOGGER.info("短剧导演任务创建请求 mode={} sourceType={} textChars={} fileName={}", mode,
                file != null && !file.isEmpty() ? "FILE" : "TEXT", text == null ? 0 : text.length(),
                file == null ? null : file.getOriginalFilename());
        View task = service.create(mode, text, file, actionTier, platform, aspectRatio);
        LOGGER.info("短剧导演任务已受理 id={} status={} mode={} actionTier={} platform={} aspectRatio={}",
                task.id(), task.status(), task.mode(), task.actionTier(), task.platform(), task.aspectRatio());
        return ResponseEntity.accepted().body(task);
    }
    @GetMapping("/tasks") List<View> list() { return service.list(); }
    @GetMapping("/tasks/{id}") View get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping("/tasks/{id}/retry") ResponseEntity<View> retry(@PathVariable UUID id) {
        LOGGER.info("短剧导演任务重试请求 id={}", id);
        return ResponseEntity.accepted().body(service.retry(id));
    }
}
