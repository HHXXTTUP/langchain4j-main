package dev.learning.fashionagent.web;

import dev.learning.fashionagent.service.GptImageGenerationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gpt-images")
public class GptImageController {
    private final GptImageGenerationService service;
    public GptImageController(GptImageGenerationService service) { this.service = service; }
    @PostMapping ResponseEntity<?> create(@RequestBody Map<String, String> body) { return ResponseEntity.accepted().body(service.generate(body == null ? null : body.get("prompt"))); }
    @GetMapping("/{id}") GptImageGenerationService.View get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/output") ResponseEntity<FileSystemResource> output(@PathVariable UUID id) throws IOException { Path path = service.output(id); String type = Files.probeContentType(path); return ResponseEntity.ok().contentType(type == null ? MediaType.IMAGE_PNG : MediaType.parseMediaType(type)).body(new FileSystemResource(path)); }
}
