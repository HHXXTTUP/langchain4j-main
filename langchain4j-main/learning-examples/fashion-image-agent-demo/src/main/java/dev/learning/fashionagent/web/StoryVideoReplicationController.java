package dev.learning.fashionagent.web;

import dev.learning.fashionagent.comfyui.StoryVideoReplicationService;
import dev.learning.fashionagent.comfyui.StoryVideoReplicationService.ShotRequest;
import dev.learning.fashionagent.comfyui.StoryVideoReplicationView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/story-video-replications")
public class StoryVideoReplicationController {
    private final StoryVideoReplicationService service;
    public StoryVideoReplicationController(StoryVideoReplicationService service) { this.service = service; }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<StoryVideoReplicationView> analyze(@RequestPart("video") MultipartFile video) {
        return ResponseEntity.accepted().body(service.analyze(video));
    }

    @PostMapping("/resolve-url")
    ResponseEntity<StoryVideoReplicationView> resolveUrl(@RequestBody(required = false) ResolveUrlRequest request) {
        if (request == null) throw new IllegalArgumentException("address is required");
        return ResponseEntity.accepted().body(service.resolveUrl(request.address()));
    }

    @GetMapping
    List<StoryVideoReplicationView> list() { return service.list(); }

    @GetMapping("/{id}")
    StoryVideoReplicationView get(@PathVariable UUID id) { return service.get(id); }

    @PostMapping("/{id}/execute")
    ResponseEntity<StoryVideoReplicationView> execute(@PathVariable UUID id, @RequestBody ExecuteRequest request) {
        if (request == null || request.shots() == null) throw new IllegalArgumentException("shots is required");
        return ResponseEntity.accepted().body(service.execute(id, request.shots()));
    }

    @PostMapping("/{id}/shots/{sequence}/generate")
    ResponseEntity<StoryVideoReplicationView> generateShot(
            @PathVariable UUID id, @PathVariable int sequence, @RequestBody ShotRequest request) {
        return ResponseEntity.accepted().body(service.generateShot(id, sequence, request));
    }

    @PostMapping("/{id}/shots/{sequence}/recognize-first-frame")
    StoryVideoReplicationView recognizeFirstFrame(@PathVariable UUID id, @PathVariable int sequence) {
        return service.recognizeFirstFrame(id, sequence);
    }

    @PostMapping("/{id}/assemble")
    ResponseEntity<StoryVideoReplicationView> assemble(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.assemble(id));
    }

    @PostMapping("/{id}/analyze")
    ResponseEntity<StoryVideoReplicationView> analyzeDownloaded(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.startAnalysis(id));
    }

    @GetMapping("/{id}/final")
    ResponseEntity<FileSystemResource> finalVideo(@PathVariable UUID id) throws IOException {
        Path path = service.finalVideo(id);
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok().contentType(contentType == null ? MediaType.parseMediaType("video/mp4") : MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }

    @GetMapping("/{id}/shots/{sequence}/video")
    ResponseEntity<FileSystemResource> shotVideo(@PathVariable UUID id, @PathVariable int sequence) throws IOException {
        Path path = service.shotVideo(id, sequence);
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok().contentType(contentType == null ? MediaType.parseMediaType("video/mp4") : MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }

    @GetMapping("/{id}/shots/{sequence}/first-frame")
    ResponseEntity<FileSystemResource> firstFrame(@PathVariable UUID id, @PathVariable int sequence) throws IOException {
        Path path = service.firstFrame(id, sequence);
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok().contentType(contentType == null ? MediaType.IMAGE_JPEG : MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(path));
    }

    public record ExecuteRequest(List<ShotRequest> shots) {}
    public record ResolveUrlRequest(String address) {}
}
