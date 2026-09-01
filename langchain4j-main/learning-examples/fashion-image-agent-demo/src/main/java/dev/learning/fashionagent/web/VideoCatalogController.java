package dev.learning.fashionagent.web;

import dev.learning.fashionagent.video.VideoCatalogService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/video-catalog")
public class VideoCatalogController {

    private final VideoCatalogService service;
    private final dev.learning.fashionagent.video.SnapAnyVideoImportService snapAnyImportService;

    public VideoCatalogController(
            VideoCatalogService service,
            dev.learning.fashionagent.video.SnapAnyVideoImportService snapAnyImportService) {
        this.service = service;
        this.snapAnyImportService = snapAnyImportService;
    }

    @GetMapping
    List<VideoCatalogService.VideoAssetView> list(@RequestParam(required = false) String folder) {
        return folder == null ? service.list() : service.list(folder);
    }

    @GetMapping("/folders")
    List<VideoCatalogService.VideoFolderView> folders() {
        return service.folders();
    }

    @PostMapping("/folders/{folder}/select")
    VideoCatalogService.VideoFolderView selectFolder(@PathVariable String folder) {
        return service.selectFolder(folder);
    }

    @PutMapping("/folders/selection")
    List<VideoCatalogService.VideoFolderView> selectSourceFolders(@RequestBody SourceFoldersRequest request) {
        return service.selectSourceFolders(request == null ? null : request.folders());
    }

    @PostMapping("/imports")
    ResponseEntity<dev.learning.fashionagent.video.SnapAnyVideoImportService.ImportView> importVideos(
            @RequestBody ImportVideosRequest request) {
        return ResponseEntity.accepted().body(snapAnyImportService.create(request.folderName(), request.content()));
    }

    @GetMapping("/imports/{id}")
    dev.learning.fashionagent.video.SnapAnyVideoImportService.ImportView importStatus(@PathVariable UUID id) {
        return snapAnyImportService.get(id);
    }

    @GetMapping("/{id}/content")
    ResponseEntity<FileSystemResource> content(@PathVariable String id) throws IOException {
        Path video = service.resolve(id);
        String contentType = Files.probeContentType(video);
        MediaType mediaType = contentType == null ? MediaType.parseMediaType("video/mp4") : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(video));
    }

    record ImportVideosRequest(String folderName, String content) {}

    record SourceFoldersRequest(List<String> folders) {}
}
