package dev.learning.kidsgrowth.web;

import dev.learning.kidsgrowth.animation.AnimationScene;
import dev.learning.kidsgrowth.animation.AnimationSceneService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/animation")
public class AnimationController {

    private final AnimationSceneService service;

    public AnimationController(AnimationSceneService service) {
        this.service = service;
    }

    @PostMapping("/scenes")
    AnimationScene createScene(@Valid @RequestBody CreateAnimationRequest request) {
        return service.generate(request.chineseText(), request.englishText(), request.style());
    }

    public record CreateAnimationRequest(
            @NotBlank(message = "请先输入想做成动画的中文单词")
            @Size(max = 30, message = "请输入30个字以内的单词或短语")
            String chineseText,
            @Size(max = 80, message = "英文单词或短语不能超过80个字符")
            String englishText,
            String style) {}
}
