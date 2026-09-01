package dev.learning.kidsgrowth.ai;

import dev.langchain4j.exception.RateLimitException;
import dev.learning.kidsgrowth.web.ExternalServiceUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GlmChildLessonGenerator implements ChildLessonGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlmChildLessonGenerator.class);
    private static final int MAX_RETRIES = 10;
    private static final int MAX_ATTEMPTS = MAX_RETRIES + 1;
    private static final long RETRY_DELAY_MILLIS = 500L;

    private final ChildEnglishAgent agent;
    private final Sleeper sleeper;

    GlmChildLessonGenerator(ChildEnglishAgent agent) {
        this(agent, Thread::sleep);
    }

    GlmChildLessonGenerator(ChildEnglishAgent agent, Sleeper sleeper) {
        this.agent = agent;
        this.sleeper = sleeper;
    }

    @Override
    public ChildLessonDraft generate(String chineseText) {
        RateLimitException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return normalizeLesson(agent.createLesson(chineseText));
            } catch (RateLimitException exception) {
                lastFailure = exception;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                LOGGER.warn("GLM 当前繁忙，第 {}/{} 次调用失败，{}ms 后重试",
                        attempt, MAX_ATTEMPTS, RETRY_DELAY_MILLIS);
                try {
                    sleeper.sleep(RETRY_DELAY_MILLIS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new ExternalServiceUnavailableException("服务开小差了~", interruptedException);
                }
            }
        }
        throw new ExternalServiceUnavailableException("服务开小差了~", lastFailure);
    }

    private static ChildLessonDraft normalizeLesson(ChildLessonDraft draft) {
        if (draft == null
                || isBlank(draft.englishText())
                || isBlank(draft.exampleSentence())
                || isBlank(draft.expectedAnswer())
                || draft.questionsChinese() == null
                || draft.questionsChinese().isEmpty()) {
            throw new ExternalServiceUnavailableException("GLM 没有生成完整课程，请稍后再试");
        }
        List<String> questions = draft.questionsChinese().stream()
                .filter(question -> question != null && !question.isBlank())
                .limit(3)
                .toList();
        if (questions.isEmpty()) {
            throw new ExternalServiceUnavailableException("GLM 没有生成可用问题，请稍后再试");
        }
        return new ChildLessonDraft(
                draft.englishText().trim(),
                safeText(draft.pronunciationTip()),
                draft.exampleSentence().trim(),
                safeText(draft.exampleTranslation()),
                questions,
                draft.expectedAnswer().trim());
    }

    @FunctionalInterface
    interface Sleeper {

        void sleep(long millis) throws InterruptedException;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
