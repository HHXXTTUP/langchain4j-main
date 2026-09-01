package dev.learning.fashionagent.web;

import dev.learning.fashionagent.learning.CatalogRefreshView;
import dev.learning.fashionagent.learning.ClothingCatalogLearningService;
import dev.learning.fashionagent.learning.ClothingProfileView;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clothing-catalog")
public class ClothingCatalogController {

    private final ClothingCatalogLearningService service;

    public ClothingCatalogController(ClothingCatalogLearningService service) {
        this.service = service;
    }

    @PostMapping("/refresh")
    ResponseEntity<CatalogRefreshView> refresh() {
        return ResponseEntity.accepted().body(service.startRefresh());
    }

    @GetMapping("/status")
    CatalogRefreshView status() {
        return service.status();
    }

    @GetMapping
    List<ClothingProfileView> profiles() {
        return service.profiles();
    }

    @GetMapping("/{id}/image")
    ResponseEntity<FileSystemResource> image(@PathVariable String id) throws IOException {
        Path image = service.image(id);
        String contentType = Files.probeContentType(image);
        MediaType mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(image));
    }
}
