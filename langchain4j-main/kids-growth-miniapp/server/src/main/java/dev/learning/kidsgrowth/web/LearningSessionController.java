package dev.learning.kidsgrowth.web;

import dev.learning.kidsgrowth.config.ChildAiProperties;
import dev.learning.kidsgrowth.config.EdgeTtsProperties;
import dev.learning.kidsgrowth.learning.LearningSessionService;
import dev.learning.kidsgrowth.learning.LearningSessionService.AttemptResponse;
import dev.learning.kidsgrowth.learning.LearningSessionService.LessonResponse;
import dev.learning.kidsgrowth.learning.LearningSessionService.QuestionResponse;
import dev.learning.kidsgrowth.speech.SpeechRecognitionGateway;
import dev.learning.kidsgrowth.speech.TextToSpeechGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/learning")
public class LearningSessionController {

    private final LearningSessionService service;
    private final ChildAiProperties aiProperties;
    private final EdgeTtsProperties edgeTtsProperties;
    private final TextToSpeechGateway textToSpeechGateway;
    private final SpeechRecognitionGateway speechRecognitionGateway;

    public LearningSessionController(
            LearningSessionService service,
            ChildAiProperties aiProperties,
            EdgeTtsProperties edgeTtsProperties,
            TextToSpeechGateway textToSpeechGateway,
            SpeechRecognitionGateway speechRecognitionGateway) {
        this.service = service;
        this.aiProperties = aiProperties;
        this.edgeTtsProperties = edgeTtsProperties;
        this.textToSpeechGateway = textToSpeechGateway;
        this.speechRecognitionGateway = speechRecognitionGateway;
    }

    @PostMapping("/sessions")
    LessonResponse createLesson(@Valid @RequestBody CreateLessonRequest request) {
        return service.createLesson(request.chineseText());
    }

    @PostMapping("/sessions/{sessionId}/questions/next")
    QuestionResponse nextQuestion(@PathVariable String sessionId) {
        return service.nextQuestion(sessionId);
    }

    @PostMapping(
            value = "/sessions/{sessionId}/attempts",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AttemptResponse evaluateAttempt(
            @PathVariable String sessionId,
            @RequestPart("audio") MultipartFile audio) throws Exception {
        if (audio.isEmpty()) {
            throw new IllegalArgumentException("录音内容为空");
        }
        return service.evaluateAttempt(sessionId, audio.getBytes());
    }

    @PostMapping("/sessions/{sessionId}/praise")
    AttemptResponse praise(@PathVariable String sessionId) {
        return service.praiseWithoutRecognition(sessionId);
    }

    @GetMapping(value = "/sessions/{sessionId}/audio/{audioKey}", produces = "audio/mpeg")
    ResponseEntity<byte[]> audio(
            @PathVariable String sessionId,
            @PathVariable String audioKey) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(service.getAudio(sessionId, audioKey));
    }

    @GetMapping("/readiness")
    ReadinessResponse readiness() {
        return new ReadinessResponse(
                aiProperties.isConfigured(),
                textToSpeechGateway.isReady(),
                speechRecognitionGateway.isReady(),
                textToSpeechGateway.providerName(),
                aiProperties.getModelName(),
                edgeTtsProperties.getEnglishVoice(),
                edgeTtsProperties.getChineseVoice());
    }

    public record CreateLessonRequest(
            @NotBlank(message = "请先输入想学习的中文单词")
            @Size(max = 30, message = "请输入30个字以内的单词或短语")
            String chineseText) {}

    public record ReadinessResponse(
            boolean aiReady,
            boolean ttsReady,
            boolean sttReady,
            String ttsProvider,
            String model,
            String englishVoice,
            String chineseVoice) {}
}
