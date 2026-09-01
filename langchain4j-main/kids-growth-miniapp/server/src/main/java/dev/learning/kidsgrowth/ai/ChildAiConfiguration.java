package dev.learning.kidsgrowth.ai;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.learning.kidsgrowth.config.ChildAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChildAiConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChildAiConfiguration.class);

    @Bean
    ChildLessonGenerator childLessonGenerator(ChildAiProperties properties) {
        if (!properties.isConfigured()) {
            LOGGER.warn("儿童英语 GLM 未配置：请设置 ZHIPU_API_KEY");
            return new UnavailableChildLessonGenerator();
        }

        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectTimeout(properties.getTimeout())
                .readTimeout(properties.getTimeout());
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey().trim())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(0.2)
                .timeout(properties.getTimeout())
                // 限流重试由 GlmChildLessonGenerator 统一控制，避免底层和业务层叠加等待。
                .maxRetries(0)
                .responseFormat("json_object")
                .httpClientBuilder(httpClientBuilder)
                .logRequests(false)
                .logResponses(false)
                .build();
        ChildEnglishAgent agent = AiServices.builder(ChildEnglishAgent.class)
                .chatModel(model)
                .build();
        LOGGER.info("儿童英语 GLM 已启用 model={} baseUrl={}", properties.getModelName(), properties.getBaseUrl());
        return new GlmChildLessonGenerator(agent);
    }
}
