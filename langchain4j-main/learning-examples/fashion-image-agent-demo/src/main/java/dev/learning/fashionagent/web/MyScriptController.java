package dev.learning.fashionagent.web;

import dev.learning.fashionagent.script.MyScriptService;
import dev.learning.fashionagent.script.MyScriptService.EpisodeView;
import dev.learning.fashionagent.script.MyScriptService.ProjectView;
import dev.learning.fashionagent.script.MyScriptService.SegmentView;
import dev.learning.fashionagent.script.MyScriptService.CharacterView;
import dev.learning.fashionagent.script.MyScriptService.CharacterRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/my-scripts")
public class MyScriptController {
    private final MyScriptService service;
    public MyScriptController(MyScriptService service) { this.service = service; }
    @GetMapping List<ProjectView> list() { return service.list(); }
    @GetMapping("/{id}") ProjectView get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/episodes/{id}") EpisodeView episode(@PathVariable UUID id) { return service.episode(id); }
    @PostMapping("/{id}/episodes/first") ResponseEntity<EpisodeView> firstEpisode(@PathVariable UUID id) { return ResponseEntity.accepted().body(service.startFirstEpisode(id)); }
    @PostMapping("/{id}/episodes") ResponseEntity<EpisodeView> continueEpisode(@PathVariable UUID id) { return ResponseEntity.accepted().body(service.continueEpisode(id)); }
    @PostMapping("/episodes/{id}/rewrite") ResponseEntity<EpisodeView> rewriteEpisode(@PathVariable UUID id, @RequestBody RewriteRequest request) {
        return ResponseEntity.accepted().body(service.rewriteEpisode(id, request == null ? null : request.idea(), request == null ? null : request.promptId()));
    }
    @GetMapping("/{id}/characters") List<CharacterView> characters(@PathVariable UUID id) { return service.characters(id); }
    @PostMapping("/{id}/characters") ResponseEntity<Void> saveCharacters(@PathVariable UUID id, @RequestBody List<CharacterRequest> requests) { service.saveCharacters(id, requests); return ResponseEntity.ok().build(); }
    @PostMapping("/episodes/{id}/replication-segments") MyScriptService.ReplicationView prepareReplication(@PathVariable UUID id) { return service.prepareReplication(id); }
    @PostMapping("/episodes/{id}/replication-segments/replan") MyScriptService.ReplicationView replanReplication(@PathVariable UUID id) { return service.replanReplication(id); }
    @GetMapping("/episodes/{id}/replication-segments") List<SegmentView> segments(@PathVariable UUID id) { return service.segments(id); }
    @PutMapping("/replication-segments/{id}") SegmentView updateSegment(@PathVariable UUID id, @RequestBody SegmentUpdateRequest request) { return service.updateSegment(id, request == null ? null : request.content(), request == null ? null : request.durationSeconds()); }
    @PostMapping("/replication-segments/{id}/generate") ResponseEntity<SegmentView> replicate(@PathVariable UUID id, @RequestBody ReplicateRequest request) {
        return ResponseEntity.accepted().body(service.replicate(id, request == null ? List.of() : request.images(), request == null ? null : request.resolution()));
    }
    public record ReplicateRequest(List<String> images, String resolution) {}
    public record SegmentUpdateRequest(String content, Integer durationSeconds) {}
    public record RewriteRequest(String idea, UUID promptId) {}
}
