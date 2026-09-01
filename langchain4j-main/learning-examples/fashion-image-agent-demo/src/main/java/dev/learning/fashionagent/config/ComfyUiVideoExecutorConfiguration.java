package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountTaskDecorator;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ComfyUiVideoExecutorConfiguration {

    @Bean
    Executor comfyUiVideoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("comfyui-video-");
        executor.setTaskDecorator(new AccountTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    Executor storyVideoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("story-video-");
        executor.setTaskDecorator(new AccountTaskDecorator());
        executor.initialize();
        return executor;
    }
}
