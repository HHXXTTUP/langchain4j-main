package dev.learning.fashionagent.web;

import dev.learning.fashionagent.comfyui.StoryVideoPlan;
import dev.learning.fashionagent.comfyui.StoryVideoPlannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comfyui-video-plans")
public class StoryVideoPlannerController {
    private final StoryVideoPlannerService service;

    public StoryVideoPlannerController(StoryVideoPlannerService service) { this.service = service; }

    @PostMapping("/preview")
    ResponseEntity<StoryVideoPlan> preview(@RequestBody(required = false) PreviewRequest request) {
        if (request == null) throw new IllegalArgumentException("request body is required");
        return ResponseEntity.ok(service.preview(request.story()));
    }

    public record PreviewRequest(String story) {}
}
