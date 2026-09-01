package dev.learning.imagegen;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    ImageGenerationController(ImageGenerationService imageGenerationService) {
        this.imageGenerationService = imageGenerationService;
    }

    @PostMapping
    ImageGenerationResponse generate(@RequestBody(required = false) ImageGenerationRequest request) {
        String prompt = request == null ? null : request.prompt();
        ImageGenerationResult result = imageGenerationService.generate(prompt);
        return new ImageGenerationResponse(result.imageSrc(), result.revisedPrompt());
    }

    record ImageGenerationRequest(String prompt) {}

    record ImageGenerationResponse(String imageSrc, String revisedPrompt) {}
}
