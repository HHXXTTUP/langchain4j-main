package dev.learning.fashionagent.web;

import dev.learning.fashionagent.browser.VideoBrowserProfileService;
import dev.learning.fashionagent.browser.VideoBrowserProfileView;
import dev.learning.fashionagent.browser.EmbeddedBrowserService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/video-browser")
public class VideoBrowserController {
    private final VideoBrowserProfileService profiles;
    private final EmbeddedBrowserService embedded;
    public VideoBrowserController(VideoBrowserProfileService profiles, EmbeddedBrowserService embedded) { this.profiles = profiles; this.embedded = embedded; }
    @GetMapping List<VideoBrowserProfileView> list() { return profiles.list(); }
    @PostMapping VideoBrowserProfileView create(@RequestBody VideoBrowserProfileService.CreateProfile request) { return profiles.create(request); }
    @PutMapping("/{id}") VideoBrowserProfileView update(@PathVariable UUID id, @RequestBody VideoBrowserProfileService.UpdateProfile request) { return profiles.update(id, request); }
    @PostMapping("/{id}/start") ResponseEntity<VideoBrowserProfileView> start(@PathVariable UUID id) { return ResponseEntity.accepted().body(profiles.start(id)); }
    @PostMapping("/{id}/stop") VideoBrowserProfileView stop(@PathVariable UUID id) { return profiles.stop(id); }
    @PostMapping("/{id}/open") EmbeddedBrowserService.SessionInfo open(@PathVariable UUID id) { return embedded.open(id); }
    @GetMapping("/{id}/session") EmbeddedBrowserService.SessionInfo session(@PathVariable UUID id) { return embedded.info(id); }
    @GetMapping(value = "/{id}/screenshot", produces = MediaType.IMAGE_JPEG_VALUE)
    byte[] screenshot(@PathVariable UUID id) { return embedded.screenshot(id); }
    @GetMapping("/{id}/selection")
    Map<String, String> selection(@PathVariable UUID id) { return Map.of("text", embedded.selectedText(id)); }
    @PostMapping("/{id}/input")
    void input(@PathVariable UUID id, @RequestBody InputRequest request) {
        if (request == null || request.type() == null) return;
        switch (request.type()) {
            case "click" -> embedded.click(id, number(request.x()), number(request.y()));
            case "type" -> embedded.type(id, request.text());
            case "key" -> embedded.press(id, request.key(), request.code(), modifiers(request));
            case "scroll" -> embedded.scroll(id, number(request.x()), number(request.y()), number(request.deltaY()));
            default -> throw new IllegalArgumentException("不支持的浏览器输入类型: " + request.type());
        }
    }
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable UUID id) { embedded.close(id); profiles.delete(id); return ResponseEntity.noContent().build(); }
    private static double number(Double value) { return value == null ? 0 : value; }
    private static int modifiers(InputRequest request) {
        int modifiers = 0;
        if (Boolean.TRUE.equals(request.altKey())) modifiers |= 1;
        if (Boolean.TRUE.equals(request.ctrlKey())) modifiers |= 2;
        if (Boolean.TRUE.equals(request.metaKey())) modifiers |= 4;
        if (Boolean.TRUE.equals(request.shiftKey())) modifiers |= 8;
        return modifiers;
    }
    record InputRequest(String type, Double x, Double y, Double deltaY, String text, String key, String code,
                        Boolean altKey, Boolean ctrlKey, Boolean metaKey, Boolean shiftKey) {}
}
