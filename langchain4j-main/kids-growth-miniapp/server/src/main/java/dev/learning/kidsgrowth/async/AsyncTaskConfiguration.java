package dev.learning.kidsgrowth.async;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncTaskConfiguration {

    @Bean(name = "learningTaskExecutor", destroyMethod = "shutdown")
    ExecutorService learningTaskExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "learning-task-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
