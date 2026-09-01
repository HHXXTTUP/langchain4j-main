package dev.learning.fashionagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import dev.learning.fashionagent.service.BeautyImageGenerationService;
import dev.learning.fashionagent.service.ImageTransferService;
import dev.learning.fashionagent.service.PortraitImageFormatter;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Agent1BeautyCreatorTest {

    @Test
    void shouldUseEnhancedPromptAndArchiveAttemptSeparately() {
        BeautyImageGenerationService service = mock(BeautyImageGenerationService.class);
        ImageTransferService imageTransferService = mock(ImageTransferService.class);
        PortraitImageFormatter imageFormatter = mock(PortraitImageFormatter.class);
        PipelineObserver observer = mock(PipelineObserver.class);
        when(service.generate(anyString(), any())).thenReturn(URI.create("https://example.com/person.png"));
        UUID jobId = UUID.randomUUID();
        Path downloaded = Path.of("generated/person/person.png");
        Path formatted = Path.of("generated/person/person-1080x1920.png");
        when(imageTransferService.downloadRemote(
                        URI.create("https://example.com/person.png"), jobId, "portrait-attempt-1"))
                .thenReturn(downloaded);
        when(imageFormatter.targetSize()).thenReturn("1080x1920");
        when(imageFormatter.format(downloaded)).thenReturn(formatted);
        Agent1BeautyCreator agent = new Agent1BeautyCreator(service, imageTransferService, imageFormatter);
        String enhancedPrompt = "一位明确年满20岁的成年女性，在艺术馆中自然站立，全身构图";

        Path result = agent.generateAttempt(jobId, enhancedPrompt, 1, observer);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> progressCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(service).generate(promptCaptor.capture(), progressCaptor.capture());
        assertEquals(enhancedPrompt, promptCaptor.getValue());
        assertEquals(formatted, result);
        verify(imageTransferService).downloadRemote(
                URI.create("https://example.com/person.png"), jobId, "portrait-attempt-1");
        verify(imageFormatter).format(downloaded);
    }

    @Test
    void shouldFormatEnhancedPortraitUsingEnhancedDimensions() {
        BeautyImageGenerationService service = mock(BeautyImageGenerationService.class);
        ImageTransferService imageTransferService = mock(ImageTransferService.class);
        PortraitImageFormatter imageFormatter = mock(PortraitImageFormatter.class);
        PipelineObserver observer = mock(PipelineObserver.class);
        when(service.generate(anyString(), any(PortraitGenerationMode.class), any()))
                .thenReturn(URI.create("https://example.com/enhanced-person.png"));
        UUID jobId = UUID.randomUUID();
        Path downloaded = Path.of("generated/person/enhanced-person.png");
        Path formatted = Path.of("generated/person/enhanced-person-756x1344.png");
        when(imageTransferService.downloadRemote(
                        URI.create("https://example.com/enhanced-person.png"), jobId, "portrait-attempt-1"))
                .thenReturn(downloaded);
        when(imageFormatter.targetSize(PortraitGenerationMode.ENHANCED)).thenReturn("756x1344");
        when(imageFormatter.format(downloaded, PortraitGenerationMode.ENHANCED)).thenReturn(formatted);
        Agent1BeautyCreator agent = new Agent1BeautyCreator(service, imageTransferService, imageFormatter);

        Path result = agent.generateAttempt(
                jobId, "成年女性正面全身照", 1, PortraitGenerationMode.ENHANCED, observer);

        assertEquals(formatted, result);
        verify(imageFormatter).format(downloaded, PortraitGenerationMode.ENHANCED);
    }
}
