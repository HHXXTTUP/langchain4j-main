package dev.learning.fashionagent.web;

import dev.learning.fashionagent.learning.FashionExperienceLearningService;
import dev.learning.fashionagent.learning.LearnedFashionExperience;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fashion-knowledge")
public class FashionKnowledgeController {

    private final FashionExperienceLearningService service;

    public FashionKnowledgeController(FashionExperienceLearningService service) {
        this.service = service;
    }

    @GetMapping("/experiences")
    List<LearnedFashionExperience> experiences() {
        return service.listApproved();
    }
}
