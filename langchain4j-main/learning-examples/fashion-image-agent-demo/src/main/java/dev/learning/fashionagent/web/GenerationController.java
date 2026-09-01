package dev.learning.fashionagent.web;

import dev.learning.fashionagent.job.GenerationJobService;
import dev.learning.fashionagent.job.GenerationPromptBatchParser;
import dev.learning.fashionagent.job.JobView;
import dev.learning.fashionagent.job.JobStepView;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import dev.learning.fashionagent.video.VideoGenerationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generations")
public class GenerationController {

    private final GenerationJobService jobService;
    private final VideoGenerationService videoGenerationService;

    public GenerationController(GenerationJobService jobService, VideoGenerationService videoGenerationService) {
        this.jobService = jobService;
        this.videoGenerationService = videoGenerationService;
    }

    @PostMapping
    ResponseEntity<CreateGenerationResponse> create(@RequestBody(required = false) CreateGenerationRequest request) {
        List<String> prompts = GenerationPromptBatchParser.parse(request == null ? null : request.prompt());
        List<UUID> ids = jobService.createBatch(
                prompts,
                request == null ? PortraitGenerationMode.STANDARD : request.portraitGenerationMode());
        return ResponseEntity.accepted()
                .body(batchResponse(ids, prompts));
    }

    @GetMapping
    List<JobView> list() {
        return jobService.list();
    }

    @GetMapping("/{id}")
    JobView get(@PathVariable UUID id) {
        return jobService.get(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobService.validateDeletion(id);
        videoGenerationService.validateDeletionBySourceJob(id);
        videoGenerationService.deleteBySourceJob(id);
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    JobView cancel(@PathVariable UUID id) {
        return jobService.cancel(id);
    }

    @PostMapping("/{id}/restart")
    ResponseEntity<CreateGenerationResponse> restart(@PathVariable UUID id) {
        UUID restartedId = jobService.restart(id);
        return ResponseEntity.accepted()
                .body(batchResponse(List.of(restartedId), List.of(jobService.get(restartedId).prompt())));
    }

    @GetMapping("/{id}/events")
    List<JobStepView> events(@PathVariable UUID id) {
        return jobService.events(id);
    }

    @GetMapping("/{id}/clothing")
    ResponseEntity<FileSystemResource> clothing(@PathVariable UUID id) throws IOException {
        return localImage(jobService.clothingImage(id));
    }

    @GetMapping("/{id}/original")
    ResponseEntity<FileSystemResource> original(@PathVariable UUID id) throws IOException {
        return localImage(jobService.originalImage(id));
    }

    @GetMapping("/{id}/final")
    ResponseEntity<FileSystemResource> finalImage(@PathVariable UUID id) throws IOException {
        return localImage(jobService.finalImage(id));
    }

    @GetMapping("/{id}/attempts/{attemptNumber}/image")
    ResponseEntity<FileSystemResource> attemptImage(
            @PathVariable UUID id, @PathVariable int attemptNumber) throws IOException {
        return localImage(jobService.attemptImage(id, attemptNumber));
    }

    @GetMapping("/{id}/portrait-attempts/{attemptNumber}/image")
    ResponseEntity<FileSystemResource> portraitAttemptImage(
            @PathVariable UUID id, @PathVariable int attemptNumber) throws IOException {
        return localImage(jobService.portraitAttemptImage(id, attemptNumber));
    }

    private static ResponseEntity<FileSystemResource> localImage(Path image) throws IOException {
        String contentType = Files.probeContentType(image);
        MediaType mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(image));
    }

    record CreateGenerationRequest(String prompt, PortraitGenerationMode portraitGenerationMode) {}

    record CreateGenerationResponse(
            UUID jobId,
            String statusUrl,
            int jobCount,
            List<CreateGenerationItem> jobs) {}

    record CreateGenerationItem(UUID jobId, String prompt, String statusUrl) {}

    private static CreateGenerationResponse batchResponse(List<UUID> ids, List<String> prompts) {
        if (ids.isEmpty() || ids.size() != prompts.size()) {
            throw new IllegalStateException("批量任务创建结果不完整");
        }
        List<CreateGenerationItem> jobs = java.util.stream.IntStream.range(0, ids.size())
                .mapToObj(index -> new CreateGenerationItem(
                        ids.get(index),
                        prompts.get(index),
                        "/api/generations/" + ids.get(index)))
                .toList();
        CreateGenerationItem first = jobs.get(0);
        return new CreateGenerationResponse(first.jobId(), first.statusUrl(), jobs.size(), jobs);
    }
}
