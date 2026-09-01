package dev.learning.kidsgrowth.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.RateLimitException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GlmChildLessonGeneratorTest {

    @Test
    void shouldRetryTenTimesAfterInitialRateLimitAndReturnFriendlyError() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        ChildEnglishAgent agent = chineseText -> {
            calls.incrementAndGet();
            throw new RateLimitException("1305");
        };
        GlmChildLessonGenerator generator = new GlmChildLessonGenerator(agent, delays::add);

        assertThatThrownBy(() -> generator.generate("苹果"))
                .isInstanceOfSatisfying(
                        dev.learning.kidsgrowth.web.ExternalServiceUnavailableException.class,
                        exception -> assertThat(exception.getMessage()).isEqualTo("服务开小差了~"));
        assertThat(calls).hasValue(11);
        assertThat(delays).hasSize(10).containsOnly(500L);
    }

    @Test
    void shouldReturnLessonWhenRateLimitClearsDuringRetry() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        ChildEnglishAgent agent = chineseText -> {
            if (calls.incrementAndGet() <= 2) {
                throw new RateLimitException("1305");
            }
            return new ChildLessonDraft(
                    "apple",
                    "慢慢读",
                    "I see an apple.",
                    "我看见一个苹果。",
                    List.of("你看到了什么呀？"),
                    "apple");
        };
        GlmChildLessonGenerator generator = new GlmChildLessonGenerator(agent, delays::add);

        ChildLessonDraft lesson = generator.generate("苹果");

        assertThat(lesson.englishText()).isEqualTo("apple");
        assertThat(calls).hasValue(3);
        assertThat(delays).containsExactly(500L, 500L);
    }
}
