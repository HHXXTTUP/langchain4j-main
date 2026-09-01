package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountTaskDecorator;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PipelineExecutorConfiguration {

    @Bean
    Executor fashionPipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("fashion-agent-");
        executor.setTaskDecorator(new AccountTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    Executor videoPipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("fashion-video-queue-");
        executor.setTaskDecorator(new AccountTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    Executor videoSegmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("fashion-video-segment-");
        executor.setTaskDecorator(new AccountTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    Executor snapAnyImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("snapany-import-");
        executor.setTaskDecorator(new AccountTaskDecorator());
        executor.initialize();
        return executor;
    }

}
