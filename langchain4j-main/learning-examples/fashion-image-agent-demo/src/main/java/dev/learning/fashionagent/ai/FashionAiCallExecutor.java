package dev.learning.fashionagent.ai;

import dev.langchain4j.exception.HttpException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FashionAiCallExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(FashionAiCallExecutor.class);

    private final int maxAttempts;
    private final Duration retryInterval;
    private final Sleeper sleeper;

    public FashionAiCallExecutor(FashionAiProperties properties) {
        this(properties.getBusyMaxAttempts(), properties.getBusyRetryInterval(), Thread::sleep);
    }

    FashionAiCallExecutor(int maxAttempts, Duration retryInterval, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryInterval = retryInterval == null || retryInterval.isNegative()
                ? Duration.ZERO
                : retryInterval;
        this.sleeper = sleeper;
    }

    public <T> T execute(String operation, Supplier<T> invocation) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            LOGGER.info("GLM {} 第 {}/{} 次调用开始", operation, attempt, maxAttempts);
            try {
                T result = invocation.get();
                LOGGER.info("GLM {} 第 {}/{} 次调用成功", operation, attempt, maxAttempts);
                return result;
            } catch (RuntimeException exception) {
                if (!isModelBusy(exception) || attempt == maxAttempts) {
                    throw exception;
                }
                LOGGER.warn("GLM {} 第 {}/{} 次调用遇到模型繁忙，{} 秒后重试：{}",
                        operation,
                        attempt,
                        maxAttempts,
                        retryInterval.toSeconds(),
                        rootMessage(exception));
                waitBeforeRetry(operation);
            }
        }
        throw new IllegalStateException("GLM 调用重试状态异常：" + operation);
    }

    private void waitBeforeRetry(String operation) {
        try {
            sleeper.sleep(retryInterval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("任务已在等待 GLM " + operation + " 重试时停止");
        }
    }

    static boolean isModelBusy(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            // Vision models occasionally return almost-valid JSON. A fresh attempt usually
            // produces valid structured output and is safer than failing the whole workflow.
            if (current.getClass().getSimpleName().contains("OutputParsingException")) {
                return true;
            }
            if (current instanceof HttpException httpException
                    && (httpException.statusCode() == 429 || httpException.statusCode() == 503)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("访问量过大")
                        || normalized.contains("稍后再试")
                        || normalized.contains("too many requests")
                        || normalized.contains("rate limit")
                        || normalized.contains("overloaded")
                        || normalized.contains("service unavailable")) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String rootMessage(Throwable throwable) {
        String message = throwable.getClass().getSimpleName();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return message;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }
}
