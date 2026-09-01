package dev.learning.stockanalyzer.service;

import dev.learning.stockanalyzer.config.PublicCleanProperties;
import org.springframework.stereotype.Service;

@Service
public class PublicCleanModeService {

    private static final String MESSAGE = "公开版仅提供行情、公开资料和数据展示，不提供个股推荐或交易判断";
    private final PublicCleanProperties properties;

    public PublicCleanModeService(PublicCleanProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public void requirePrivateFeature(String feature) {
        if (enabled()) {
            throw new FeatureUnavailableException(MESSAGE);
        }
    }
}
