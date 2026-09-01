package dev.learning.fashionagent.ai;

import dev.langchain4j.service.AiServices;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

final class AccountAwareFashionVisionService implements FashionVisionService {
    private final FashionAiProperties properties;
    private final String unavailableReason;
    private final ConcurrentHashMap<String, FashionVisionService> delegates = new ConcurrentHashMap<>();

    AccountAwareFashionVisionService(FashionAiProperties properties, String unavailableReason) {
        this.properties = properties; this.unavailableReason = unavailableReason;
    }

    private FashionVisionService delegate() {
        if (!properties.isModelConfigured()) return new UnavailableFashionAiService(unavailableReason);
        String cacheKey = properties.getBaseUrl() + "|" + properties.getModelName() + "|" + properties.getApiKey();
        return delegates.computeIfAbsent(cacheKey, ignored -> {
            var agent = AiServices.builder(ModelFashionVisionAgent.class)
                    .chatModel(FashionAiConfiguration.createModel(properties)).build();
            return new LangChain4jFashionVisionService(agent, new VisionImageEncoder(properties.getMaxImageDimension()), properties);
        });
    }

    public ClothingCatalogAnalysis analyzeCatalogImage(Path image) { return delegate().analyzeCatalogImage(image); }
    public FashionReferenceSpec analyzeClothing(Path image) { return delegate().analyzeClothing(image); }
    public OutfitQualityReport inspectResult(Path original, Path clothing, Path result, FashionReferenceSpec spec, String prompt, int attempt) { return delegate().inspectResult(original, clothing, result, spec, prompt, attempt); }
    public FashionExperienceDraft extractSuccessfulExperience(String description, FashionReferenceSpec spec, OutfitQualityReport report, String prompt) { return delegate().extractSuccessfulExperience(description, spec, report, prompt); }
    public boolean aiEnabled() { return properties.isModelConfigured(); }
}
