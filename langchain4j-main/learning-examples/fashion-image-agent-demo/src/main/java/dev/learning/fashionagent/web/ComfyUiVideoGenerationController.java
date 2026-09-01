package dev.learning.fashionagent.web;

import dev.learning.fashionagent.comfyui.ComfyUiVideoGenerationService;
import dev.learning.fashionagent.comfyui.ComfyUiVideoView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comfyui-video-generations")
public class ComfyUiVideoGenerationController {
    private final ComfyUiVideoGenerationService service;

    public ComfyUiVideoGenerationController(ComfyUiVideoGenerationService service) { this.service = service; }

    @PostMapping
    ResponseEntity<ComfyUiVideoView> create(@RequestBody(required = false) CreateRequest request) {
        if (request == null) throw new IllegalArgumentException("request body is required");
        return ResponseEntity.accepted().body(service.create(request.prompt(), request.duration(), request.resolution(), request.images()));
    }

    @GetMapping
    List<ComfyUiVideoView> list() { return service.list(); }

    @GetMapping("/{id}")
    ComfyUiVideoView get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/final")
    ResponseEntity<FileSystemResource> finalVideo(@PathVariable UUID id) throws IOException {
        Path path = service.finalVideo(id);
        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType == null ? MediaType.parseMediaType("video/mp4") : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(path));
    }

    @PostMapping("/{id}/open-folder")
    Map<String, String> openFolder(@PathVariable UUID id) { return Map.of("folder", service.openFolder(id).toString()); }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(id); return ResponseEntity.noContent().build(); }

    public record CreateRequest(String prompt, Integer duration, String resolution, List<String> images) {}
}
