package dev.learning.kidsgrowth.learning;

import dev.learning.kidsgrowth.ai.ChildLessonDraft;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
class LearningSessionStore {

    private static final long MAX_AGE_SECONDS = 60 * 60;
    private final Map<String, StoredSession> sessions = new ConcurrentHashMap<>();

    void put(String id, String chineseText, ChildLessonDraft lesson, byte[] englishAudio, byte[] firstQuestionAudio) {
        cleanupExpired();
        StoredSession session = new StoredSession(chineseText, lesson);
        session.audio.put("english", englishAudio);
        session.audio.put("question-0", firstQuestionAudio);
        sessions.put(id, session);
    }

    StoredSession require(String id) {
        StoredSession session = sessions.get(id);
        if (session == null) {
            throw new LearningSessionNotFoundException("学习会话已过期，请重新学习这个单词");
        }
        return session;
    }

    byte[] requireAudio(String id, String key) {
        byte[] audio = require(id).audio.get(key);
        if (audio == null) {
            throw new LearningSessionNotFoundException("语音不存在或已经过期");
        }
        return audio;
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minusSeconds(MAX_AGE_SECONDS);
        sessions.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(cutoff));
    }

    static final class StoredSession {
        private final String chineseText;
        private final ChildLessonDraft lesson;
        private final Instant createdAt = Instant.now();
        private final AtomicInteger currentQuestionIndex = new AtomicInteger(0);
        private final Map<String, byte[]> audio = new ConcurrentHashMap<>();

        StoredSession(String chineseText, ChildLessonDraft lesson) {
            this.chineseText = chineseText;
            this.lesson = lesson;
        }

        String chineseText() {
            return chineseText;
        }

        ChildLessonDraft lesson() {
            return lesson;
        }

        int nextQuestionIndex() {
            return currentQuestionIndex.updateAndGet(current ->
                    Math.min(current + 1, lesson.questionsChinese().size() - 1));
        }

        void putAudio(String key, byte[] value) {
            audio.put(key, value);
        }
    }
}
