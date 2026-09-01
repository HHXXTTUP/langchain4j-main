package dev.learning.fashionagent.ai;

import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FashionAiConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(FashionAiConfiguration.class);

    @Bean
    VisionImageEncoder visionImageEncoder(FashionAiProperties properties) {
        return new VisionImageEncoder(properties.getMaxImageDimension());
    }

    @Bean
    FashionAiModelRuntime fashionAiModelRuntime(FashionAiProperties properties) {
        String unavailableReason = properties.isEnabled()
                ? "未配置 ZHIPU_API_KEY，LangChain4j AI 能力不可用"
                : "fashion.ai.enabled=false，LangChain4j AI 能力已关闭";
        if (!properties.isModelConfigured()) {
            LOGGER.error("Fashion AI 未就绪：{}；任务不会使用规则提示词降级执行", unavailableReason);
            return new FashionAiModelRuntime(Optional.empty(), unavailableReason);
        }

        ChatModel model = createModel(properties);
        LOGGER.info("GLM 多模态模式已启用 model={} baseUrl={}", properties.getModelName(), properties.getBaseUrl());
        return new FashionAiModelRuntime(Optional.of(model), unavailableReason);
    }

    public static ChatModel createModel(FashionAiProperties properties) {
        // 智谱提供 OpenAI Chat Completions 兼容接口，LangChain4j 仍负责多模态消息和 AI Service 映射。
        var modelBuilder = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey().trim())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(0.1)
                .timeout(properties.getTimeout())
                .maxRetries(0)
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false);

        JdkHttpClientBuilder httpClientBuilder = new JdkHttpClientBuilder()
                .connectTimeout(properties.getTimeout())
                .readTimeout(properties.getTimeout());
        if (properties.isProxyConfigured()) {
            HttpClient.Builder javaHttpClientBuilder = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(new InetSocketAddress(
                            properties.getProxyHost().trim(),
                            properties.getProxyPort())));
            httpClientBuilder.httpClientBuilder(javaHttpClientBuilder);
            LOGGER.info("GLM AI HTTP 代理已启用 proxy={}:{}",
                    properties.getProxyHost(), properties.getProxyPort());
        }
        return modelBuilder.httpClientBuilder(httpClientBuilder).build();
    }

    @Bean
    FashionVisionService fashionVisionService(
            FashionAiProperties properties,
            FashionAiModelRuntime runtime) {
        return new AccountAwareFashionVisionService(properties, runtime.unavailableReason());
    }

    @Bean
    PortraitAiService portraitAiService(
            FashionAiProperties properties,
            FashionAiModelRuntime runtime) {
        return new AccountAwarePortraitAiService(properties, runtime.unavailableReason());
    }

    public record FashionAiModelRuntime(Optional<ChatModel> model, String unavailableReason) {}
}
