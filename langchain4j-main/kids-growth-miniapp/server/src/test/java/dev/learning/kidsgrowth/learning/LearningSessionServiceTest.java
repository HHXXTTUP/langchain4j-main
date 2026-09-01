package dev.learning.kidsgrowth.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.learning.kidsgrowth.ai.ChildLessonDraft;
import dev.learning.kidsgrowth.ai.ChildLessonGenerator;
import dev.learning.kidsgrowth.speech.SpeechRecognitionGateway;
import dev.learning.kidsgrowth.speech.SpeechVoice;
import dev.learning.kidsgrowth.speech.TextToSpeechGateway;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningSessionServiceTest {

    private final FakeTextToSpeechGateway textToSpeechGateway = new FakeTextToSpeechGateway();
    private final FakeSpeechRecognitionGateway speechRecognitionGateway = new FakeSpeechRecognitionGateway();
    private final LearningSessionService service = new LearningSessionService(
            new FixedLessonGenerator(),
            textToSpeechGateway,
            speechRecognitionGateway,
            new LearningSessionStore());

    @Test
    void shouldCreateLessonWithStreamableEnglishAndQuestionAudio() {
        var response = service.createLesson("苹果");

        assertThat(response.englishText()).isEqualTo("apple");
        assertThat(response.question()).contains("苹果");
        assertThat(response.questionCount()).isEqualTo(3);
        assertThat(service.getAudio(response.sessionId(), "english"))
                .isEqualTo("ENGLISH_CHILD:apple".getBytes(StandardCharsets.UTF_8));
        assertThat(service.getAudio(response.sessionId(), "question-0"))
                .isNotEmpty();
    }

    @Test
    void shouldMoveToNextQuestionAndGenerateItsAudio() {
        var lesson = service.createLesson("苹果");

        var question = service.nextQuestion(lesson.sessionId());

        assertThat(question.questionIndex()).isEqualTo(1);
        assertThat(question.question()).contains("颜色");
        assertThat(service.getAudio(lesson.sessionId(), "question-1"))
                .isNotEmpty();
    }

    @Test
    void shouldRecognizeExpectedWordAndGeneratePraiseAudio() {
        var lesson = service.createLesson("苹果");
        speechRecognitionGateway.recognizedText = "Apple.";

        var attempt = service.evaluateAttempt(lesson.sessionId(), new byte[] {1, 2, 3});

        assertThat(attempt.matched()).isTrue();
        assertThat(attempt.feedback()).startsWith("真棒");
        String key = attempt.feedbackAudioPath().substring(attempt.feedbackAudioPath().lastIndexOf('/') + 1);
        assertThat(service.getAudio(lesson.sessionId(), key)).isNotEmpty();
    }

    @Test
    void shouldRejectBlankLearningText() {
        assertThatThrownBy(() -> service.createLesson("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("中文单词");
    }

    private static final class FixedLessonGenerator implements ChildLessonGenerator {

        @Override
        public ChildLessonDraft generate(String chineseText) {
            return new ChildLessonDraft(
                    "apple",
                    "嘴巴张开，慢慢读",
                    "I see an apple.",
                    "我看见一个苹果。",
                    List.of(
                            "看到红红的苹果，你会说哪个英文单词呀？",
                            "苹果是什么颜色的呀？请先说 apple。",
                            "你喜欢 apple 吗？"),
                    "apple");
        }
    }

    private static final class FakeTextToSpeechGateway implements TextToSpeechGateway {
        @Override
        public byte[] synthesize(String text, SpeechVoice voice) {
            return (voice.name() + ":" + text).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public String providerName() {
            return "fake";
        }
    }

    private static final class FakeSpeechRecognitionGateway implements SpeechRecognitionGateway {

        private String recognizedText = "";

        @Override
        public String recognizeEnglish(byte[] wavAudio) {
            return recognizedText;
        }

        @Override
        public boolean isReady() {
            return true;
        }
    }
}
