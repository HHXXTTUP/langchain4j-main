package dev.learning.fashionagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import dev.learning.fashionagent.job.GenerationJobService;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import dev.learning.fashionagent.video.VideoGenerationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GenerationControllerTest {

    @Test
    void shouldReturnEveryCreatedBatchTaskAndKeepTheFirstTaskCompatibilityFields() {
        GenerationJobService service = mock(GenerationJobService.class);
        GenerationController controller = new GenerationController(service, mock(VideoGenerationService.class));
        List<String> prompts = List.of("卧室美女", "沙滩美女", "办公室美女", "学校美女");
        List<UUID> ids = List.of(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(service.createBatch(prompts, PortraitGenerationMode.ENHANCED)).thenReturn(ids);

        var response = controller.create(new GenerationController.CreateGenerationRequest(
                String.join(";", prompts),
                PortraitGenerationMode.ENHANCED));
        var body = response.getBody();

        assertEquals(202, response.getStatusCode().value());
        assertEquals(ids.get(0), body.jobId());
        assertEquals("/api/generations/" + ids.get(0), body.statusUrl());
        assertEquals(4, body.jobCount());
        assertEquals(prompts, body.jobs().stream().map(GenerationController.CreateGenerationItem::prompt).toList());
        verify(service).createBatch(prompts, PortraitGenerationMode.ENHANCED);
    }

    @Test
    void shouldDeleteDependentVideosBeforeDeletingTheImageTask() {
        GenerationJobService imageService = mock(GenerationJobService.class);
        VideoGenerationService videoService = mock(VideoGenerationService.class);
        GenerationController controller = new GenerationController(imageService, videoService);
        UUID id = UUID.randomUUID();

        var response = controller.delete(id);

        assertEquals(204, response.getStatusCode().value());
        InOrder order = inOrder(imageService, videoService);
        order.verify(imageService).validateDeletion(id);
        order.verify(videoService).validateDeletionBySourceJob(id);
        order.verify(videoService).deleteBySourceJob(id);
        order.verify(imageService).delete(id);
    }
}
