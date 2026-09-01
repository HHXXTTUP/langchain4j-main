package dev.learning.fashionagent.web;

import dev.learning.fashionagent.service.AuditRedrawService;
import dev.learning.fashionagent.service.AuditRedrawService.AuditRedrawView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/audit-redraw")
public class AuditRedrawController {
    private final AuditRedrawService service;

    public AuditRedrawController(AuditRedrawService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AuditRedrawView> create(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.accepted().body(service.create(image));
    }

    @GetMapping("/{id}")
    AuditRedrawView get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/output")
    ResponseEntity<FileSystemResource> output(@PathVariable UUID id) throws IOException {
        Path path = service.output(id);
        String type = Files.probeContentType(path);
        MediaType mediaType = type == null ? MediaType.IMAGE_PNG : MediaType.parseMediaType(type);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(path));
    }
}
