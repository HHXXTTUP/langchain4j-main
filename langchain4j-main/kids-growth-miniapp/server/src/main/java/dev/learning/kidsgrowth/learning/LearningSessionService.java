package dev.learning.kidsgrowth.learning;

import dev.learning.kidsgrowth.ai.ChildLessonDraft;
import dev.learning.kidsgrowth.ai.ChildLessonGenerator;
import dev.learning.kidsgrowth.speech.SpeechRecognitionGateway;
import dev.learning.kidsgrowth.speech.SpeechVoice;
import dev.learning.kidsgrowth.speech.TextToSpeechGateway;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LearningSessionService {

    private static final int MAX_INPUT_LENGTH = 30;

    private final ChildLessonGenerator lessonGenerator;
    private final TextToSpeechGateway textToSpeechGateway;
    private final SpeechRecognitionGateway speechRecognitionGateway;
    private final LearningSessionStore store;

    public LearningSessionService(
            ChildLessonGenerator lessonGenerator,
            TextToSpeechGateway textToSpeechGateway,
            SpeechRecognitionGateway speechRecognitionGateway,
            LearningSessionStore store) {
        this.lessonGenerator = lessonGenerator;
        this.textToSpeechGateway = textToSpeechGateway;
        this.speechRecognitionGateway = speechRecognitionGateway;
        this.store = store;
    }

    public LessonResponse createLesson(String rawChineseText) {
        String chineseText = normalizeInput(rawChineseText);
        ChildLessonDraft lesson = lessonGenerator.generate(chineseText);
        String firstQuestion = lesson.questionsChinese().get(0);
        byte[] englishAudio = textToSpeechGateway.synthesize(lesson.englishText(), SpeechVoice.ENGLISH_CHILD);
        byte[] questionAudio = textToSpeechGateway.synthesize(firstQuestion, SpeechVoice.CHINESE_YOUNG_FEMALE);
        String sessionId = UUID.randomUUID().toString();
        store.put(sessionId, chineseText, lesson, englishAudio, questionAudio);
        return toLessonResponse(sessionId, chineseText, lesson, 0);
    }

    public QuestionResponse nextQuestion(String sessionId) {
        LearningSessionStore.StoredSession session = store.require(sessionId);
        int index = session.nextQuestionIndex();
        String question = session.lesson().questionsChinese().get(index);
        String audioKey = "question-" + index;
        byte[] audio = textToSpeechGateway.synthesize(question, SpeechVoice.CHINESE_YOUNG_FEMALE);
        session.putAudio(audioKey, audio);
        return new QuestionResponse(
                index,
                session.lesson().questionsChinese().size(),
                question,
                sessionAudioPath(sessionId, audioKey));
    }

    public AttemptResponse evaluateAttempt(String sessionId, byte[] wavAudio) {
        LearningSessionStore.StoredSession session = store.require(sessionId);
        String recognizedText = speechRecognitionGateway.recognizeEnglish(wavAudio);
        String expected = session.lesson().expectedAnswer();
        boolean matched = matchesExpected(recognizedText, expected);
        String feedback;
        if (matched) {
            feedback = "真棒！你读得很清楚，给你一颗小星星！";
        } else if (recognizedText == null || recognizedText.isBlank()) {
            feedback = "真棒，你已经勇敢开口啦！靠近一点，我们再读一次 "
                    + session.lesson().englishText() + "。";
        } else {
            feedback = "真棒，你勇敢说出来啦！我们再一起读一次 "
                    + session.lesson().englishText() + "。";
        }
        return storeFeedback(sessionId, recognizedText, matched, feedback);
    }

    public AttemptResponse praiseWithoutRecognition(String sessionId) {
        LearningSessionStore.StoredSession session = store.require(sessionId);
        String feedback = "真棒！你认真读出来啦，给你一颗小星星！";
        return storeFeedback(sessionId, "", false, feedback);
    }

    public byte[] getAudio(String sessionId, String audioKey) {
        if (audioKey == null || !audioKey.matches("[a-z]+(?:-[a-z0-9-]+)?")) {
            throw new IllegalArgumentException("无效的音频类型");
        }
        return store.requireAudio(sessionId, audioKey);
    }

    private AttemptResponse storeFeedback(
            String sessionId,
            String recognizedText,
            boolean matched,
            String feedback) {
        String attemptId = UUID.randomUUID().toString();
        String audioKey = "feedback-" + attemptId;
        byte[] feedbackAudio = textToSpeechGateway.synthesize(feedback, SpeechVoice.CHINESE_YOUNG_FEMALE);
        store.require(sessionId).putAudio(audioKey, feedbackAudio);
        return new AttemptResponse(
                attemptId,
                recognizedText == null ? "" : recognizedText,
                matched,
                feedback,
                sessionAudioPath(sessionId, audioKey));
    }

    private static LessonResponse toLessonResponse(
            String sessionId,
            String chineseText,
            ChildLessonDraft lesson,
            int questionIndex) {
        return new LessonResponse(
                sessionId,
                chineseText,
                lesson.englishText(),
                lesson.pronunciationTip(),
                lesson.exampleSentence(),
                lesson.exampleTranslation(),
                lesson.questionsChinese().get(questionIndex),
                questionIndex,
                lesson.questionsChinese().size(),
                sessionAudioPath(sessionId, "english"),
                sessionAudioPath(sessionId, "question-" + questionIndex));
    }

    private static String sessionAudioPath(String sessionId, String audioKey) {
        return "/api/learning/sessions/" + sessionId + "/audio/" + audioKey;
    }

    private static String normalizeInput(String rawChineseText) {
        if (rawChineseText == null || rawChineseText.isBlank()) {
            throw new IllegalArgumentException("请先输入想学习的中文单词");
        }
        String text = rawChineseText.trim();
        if (text.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("请输入30个字以内的单词或短语");
        }
        return text;
    }

    private static boolean matchesExpected(String recognizedText, String expectedText) {
        String recognized = normalizeEnglish(recognizedText);
        String expected = normalizeEnglish(expectedText);
        return !recognized.isBlank()
                && !expected.isBlank()
                && (recognized.equals(expected)
                        || recognized.contains(expected)
                        || expected.contains(recognized));
    }

    private static String normalizeEnglish(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record LessonResponse(
            String sessionId,
            String chineseText,
            String englishText,
            String pronunciationTip,
            String exampleSentence,
            String exampleTranslation,
            String question,
            int questionIndex,
            int questionCount,
            String englishAudioPath,
            String questionAudioPath) {}

    public record QuestionResponse(
            int questionIndex,
            int questionCount,
            String question,
            String questionAudioPath) {}

    public record AttemptResponse(
            String attemptId,
            String recognizedText,
            boolean matched,
            String feedback,
            String feedbackAudioPath) {}
}
