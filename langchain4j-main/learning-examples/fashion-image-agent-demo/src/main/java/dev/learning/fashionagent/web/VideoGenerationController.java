package dev.learning.fashionagent.web;

import dev.learning.fashionagent.video.VideoGenerationService;
import dev.learning.fashionagent.video.VideoGenerationView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/video-generations")
public class VideoGenerationController {

    private final VideoGenerationService service;

    public VideoGenerationController(VideoGenerationService service) {
        this.service = service;
    }

    @PostMapping("/source/{sourceJobId}")
    ResponseEntity<VideoGenerationView> create(@PathVariable UUID sourceJobId) {
        return ResponseEntity.accepted().body(service.create(sourceJobId));
    }

    @PostMapping("/batch")
    ResponseEntity<List<VideoGenerationView>> createBatch(@RequestBody BatchVideoGenerationRequest request) {
        return ResponseEntity.accepted().body(service.createBatch(
                request == null ? null : request.sourceJobIds()));
    }

    @GetMapping
    List<VideoGenerationView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    VideoGenerationView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/retry-download")
    ResponseEntity<VideoGenerationView> retryDownload(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.retryDownload(id));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/final")
    ResponseEntity<FileSystemResource> finalVideo(@PathVariable UUID id) throws IOException {
        Path video = service.finalVideo(id);
        String contentType = Files.probeContentType(video);
        MediaType mediaType = contentType == null ? MediaType.parseMediaType("video/mp4") : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(video));
    }

    @PostMapping("/{id}/open-folder")
    Map<String, String> openFolder(@PathVariable UUID id) {
        return Map.of("folder", service.openFolder(id).toString());
    }

    record BatchVideoGenerationRequest(List<UUID> sourceJobIds) {}
}
