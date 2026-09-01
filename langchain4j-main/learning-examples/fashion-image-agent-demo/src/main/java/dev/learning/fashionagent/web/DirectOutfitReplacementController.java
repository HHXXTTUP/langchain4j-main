package dev.learning.fashionagent.web;

import dev.learning.fashionagent.service.DirectOutfitReplacementService;
import dev.learning.fashionagent.service.DirectOutfitReplacementService.DirectOutfitView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/direct-outfit-replacements")
public class DirectOutfitReplacementController {
    private final DirectOutfitReplacementService service;
    public DirectOutfitReplacementController(DirectOutfitReplacementService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DirectOutfitView> create(@RequestPart("person") MultipartFile person, @RequestPart("clothing") MultipartFile clothing,
                                            @RequestParam(value = "prompt", required = false) String prompt) { return ResponseEntity.accepted().body(service.create(person, clothing, prompt)); }
    @GetMapping("/{id}") DirectOutfitView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/output") ResponseEntity<FileSystemResource> output(@PathVariable UUID id) throws IOException { Path path=service.output(id); String type=Files.probeContentType(path); return ResponseEntity.ok().contentType(type==null?MediaType.IMAGE_PNG:MediaType.parseMediaType(type)).body(new FileSystemResource(path)); }
}
