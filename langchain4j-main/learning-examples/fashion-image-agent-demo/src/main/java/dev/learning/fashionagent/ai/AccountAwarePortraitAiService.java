package dev.learning.fashionagent.ai;

import dev.langchain4j.service.AiServices;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

final class AccountAwarePortraitAiService implements PortraitAiService {
    private final FashionAiProperties properties;
    private final String unavailableReason;
    private final ConcurrentHashMap<String, PortraitAiService> delegates = new ConcurrentHashMap<>();

    AccountAwarePortraitAiService(FashionAiProperties properties, String unavailableReason) {
        this.properties = properties; this.unavailableReason = unavailableReason;
    }

    private PortraitAiService delegate() {
        if (!properties.isModelConfigured()) return new UnavailableFashionAiService(unavailableReason);
        String cacheKey = properties.getBaseUrl() + "|" + properties.getModelName() + "|" + properties.getApiKey();
        return delegates.computeIfAbsent(cacheKey, ignored -> {
            var agent = AiServices.builder(ModelPortraitAgent.class)
                    .chatModel(FashionAiConfiguration.createModel(properties)).build();
            return new LangChain4jPortraitAiService(agent, new VisionImageEncoder(properties.getMaxImageDimension()), properties);
        });
    }

    public PortraitPromptSpec enhancePrompt(String description) { return delegate().enhancePrompt(description); }
    public PortraitPromptSpec rewritePromptAfterAudit(PortraitPromptSpec rejected, int retry) { return delegate().rewritePromptAfterAudit(rejected, retry); }
    public PortraitQualityReport inspectPortrait(Path image, PortraitPromptSpec spec, String prompt, int attempt) { return delegate().inspectPortrait(image, spec, prompt, attempt); }
    public boolean aiEnabled() { return properties.isModelConfigured(); }
}
