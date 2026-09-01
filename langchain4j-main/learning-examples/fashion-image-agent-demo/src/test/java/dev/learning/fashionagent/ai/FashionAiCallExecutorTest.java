package dev.learning.fashionagent.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.exception.HttpException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FashionAiCallExecutorTest {

    @Test
    void shouldRetryBusyModelAtConfiguredInterval() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        FashionAiCallExecutor executor = new FashionAiCallExecutor(
                4,
                Duration.ofSeconds(10),
                sleeps::add);

        String result = executor.execute("人物提示词扩写", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new HttpException(429, "该模型当前访问量过大，请您稍后再试");
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(10_000L, 10_000L), sleeps);
    }

    @Test
    void shouldNotRetryAuthenticationFailure() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        FashionAiCallExecutor executor = new FashionAiCallExecutor(
                12,
                Duration.ofSeconds(10),
                sleeps::add);

        assertThrows(HttpException.class, () -> executor.execute("人物提示词扩写", () -> {
            calls.incrementAndGet();
            throw new HttpException(401, "invalid api key");
        }));

        assertEquals(1, calls.get());
        assertEquals(List.of(), sleeps);
    }

    @Test
    void shouldStopWhenRetryWaitIsInterrupted() {
        FashionAiCallExecutor executor = new FashionAiCallExecutor(
                12,
                Duration.ofSeconds(10),
                ignored -> {
                    throw new InterruptedException("cancelled");
                });

        try {
            assertThrows(CancellationException.class, () -> executor.execute(
                    "人物图片质检",
                    () -> {
                        throw new HttpException(429, "该模型当前访问量过大，请您稍后再试");
                    }));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
