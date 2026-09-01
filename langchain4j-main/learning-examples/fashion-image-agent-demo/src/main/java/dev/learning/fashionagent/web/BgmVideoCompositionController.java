package dev.learning.fashionagent.web;

import dev.learning.fashionagent.video.BgmVideoCompositionService;
import dev.learning.fashionagent.video.BgmVideoCompositionService.BgmFile;
import dev.learning.fashionagent.video.BgmVideoCompositionService.BgmJobView;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/video-bgm-compositions")
public class BgmVideoCompositionController {
    private final BgmVideoCompositionService service;
    public BgmVideoCompositionController(BgmVideoCompositionService service) { this.service = service; }
    @GetMapping("/bgm") List<BgmFile> bgm() { return service.listBgm(); }
    @GetMapping("/bgm-ending") List<BgmFile> endingBgm() { return service.listEndingBgm(); }
    @GetMapping("/bgm/preview") ResponseEntity<FileSystemResource> preview(@RequestParam String name, @RequestParam(value = "ending", defaultValue = "false") boolean ending) throws IOException {
        Path path = service.preview(name, ending);
        String type = Files.probeContentType(path);
        return ResponseEntity.ok().contentType(type == null ? MediaType.parseMediaType("audio/mpeg") : MediaType.parseMediaType(type)).body(new FileSystemResource(path));
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<BgmJobView> compose(@RequestPart("video") MultipartFile video, @RequestParam("bgm") String bgm, @RequestParam(value = "name", required = false) String name, @RequestParam(value = "ending", defaultValue = "false") boolean ending, @RequestParam(value = "endingBgm", required = false) String endingBgm) { return ResponseEntity.accepted().body(service.compose(video, bgm, name, ending, endingBgm)); }
    @GetMapping("/{id}") BgmJobView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/output") ResponseEntity<FileSystemResource> output(@PathVariable UUID id) throws IOException { Path path=service.output(id); String type=Files.probeContentType(path); return ResponseEntity.ok().contentType(type==null?MediaType.parseMediaType("video/mp4"):MediaType.parseMediaType(type)).body(new FileSystemResource(path)); }
}
