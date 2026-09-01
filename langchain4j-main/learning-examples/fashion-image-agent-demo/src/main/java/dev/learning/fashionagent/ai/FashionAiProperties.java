package dev.learning.fashionagent.ai;

import dev.learning.fashionagent.account.AccountContext;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fashion.ai")
public class FashionAiProperties {

    private boolean enabled = true;
    private String apiKey;
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String modelName = "glm-4.6v-flash";
    private String portraitPreset = "亚洲面孔，20到30岁的成年女性，身材匀称健美、比例协调、姿态自然，"
            + "穿着完整得体并适合公开展示的日常时尚服装，正面面向镜头自然站立，全身完整入镜，四肢清晰无遮挡，"
            + "人物居中并在四周保留适度留白，9:16竖版人像构图";
    private int portraitOutputWidth = 1080;
    private int portraitOutputHeight = 1920;
    private int enhancedPortraitOutputWidth = 756;
    private int enhancedPortraitOutputHeight = 1344;
    private String proxyHost;
    private int proxyPort;
    private Duration timeout = Duration.ofSeconds(90);
    private int busyMaxAttempts = 15;
    private Duration busyRetryInterval = Duration.ofSeconds(10);
    private int maxImageDimension = 1536;
    private int maxPortraitAttempts = 2;
    private int portraitAuditMaxRetries = 2;
    private int portraitQualityPassScore = 75;
    private int portraitPromptAlignmentPassScore = 70;
    private int portraitAnatomyPassScore = 70;
    private int portraitImageQualityPassScore = 70;
    private int maxOutfitAttempts = 2;
    private int outfitAcceptAndLearnScore = 70;
    private int qualityPassScore = 75;
    private int clothingMatchPassScore = 75;
    private int headAccessoryPassScore = 70;
    private int identityPassScore = 70;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return AccountContext.secretValue("zhipuKey", apiKey);
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPortraitPreset() {
        return portraitPreset;
    }

    public void setPortraitPreset(String portraitPreset) {
        this.portraitPreset = portraitPreset;
    }

    public int getPortraitOutputWidth() {
        return portraitOutputWidth;
    }

    public void setPortraitOutputWidth(int portraitOutputWidth) {
        this.portraitOutputWidth = portraitOutputWidth;
    }

    public int getPortraitOutputHeight() {
        return portraitOutputHeight;
    }

    public void setPortraitOutputHeight(int portraitOutputHeight) {
        this.portraitOutputHeight = portraitOutputHeight;
    }

    public int getEnhancedPortraitOutputWidth() {
        return enhancedPortraitOutputWidth;
    }

    public void setEnhancedPortraitOutputWidth(int enhancedPortraitOutputWidth) {
        this.enhancedPortraitOutputWidth = enhancedPortraitOutputWidth;
    }

    public int getEnhancedPortraitOutputHeight() {
        return enhancedPortraitOutputHeight;
    }

    public void setEnhancedPortraitOutputHeight(int enhancedPortraitOutputHeight) {
        this.enhancedPortraitOutputHeight = enhancedPortraitOutputHeight;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isProxyConfigured() {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort > 0;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getBusyMaxAttempts() {
        return busyMaxAttempts;
    }

    public void setBusyMaxAttempts(int busyMaxAttempts) {
        this.busyMaxAttempts = busyMaxAttempts;
    }

    public Duration getBusyRetryInterval() {
        return busyRetryInterval;
    }

    public void setBusyRetryInterval(Duration busyRetryInterval) {
        this.busyRetryInterval = busyRetryInterval;
    }

    public int getMaxImageDimension() {
        return maxImageDimension;
    }

    public void setMaxImageDimension(int maxImageDimension) {
        this.maxImageDimension = maxImageDimension;
    }

    public int getMaxOutfitAttempts() {
        return maxOutfitAttempts;
    }

    public int getMaxPortraitAttempts() {
        return maxPortraitAttempts;
    }

    public void setMaxPortraitAttempts(int maxPortraitAttempts) {
        this.maxPortraitAttempts = maxPortraitAttempts;
    }

    public int getPortraitAuditMaxRetries() {
        return portraitAuditMaxRetries;
    }

    public void setPortraitAuditMaxRetries(int portraitAuditMaxRetries) {
        this.portraitAuditMaxRetries = portraitAuditMaxRetries;
    }

    public int getPortraitQualityPassScore() {
        return portraitQualityPassScore;
    }

    public void setPortraitQualityPassScore(int portraitQualityPassScore) {
        this.portraitQualityPassScore = portraitQualityPassScore;
    }

    public int getPortraitPromptAlignmentPassScore() {
        return portraitPromptAlignmentPassScore;
    }

    public void setPortraitPromptAlignmentPassScore(int portraitPromptAlignmentPassScore) {
        this.portraitPromptAlignmentPassScore = portraitPromptAlignmentPassScore;
    }

    public int getPortraitAnatomyPassScore() {
        return portraitAnatomyPassScore;
    }

    public void setPortraitAnatomyPassScore(int portraitAnatomyPassScore) {
        this.portraitAnatomyPassScore = portraitAnatomyPassScore;
    }

    public int getPortraitImageQualityPassScore() {
        return portraitImageQualityPassScore;
    }

    public void setPortraitImageQualityPassScore(int portraitImageQualityPassScore) {
        this.portraitImageQualityPassScore = portraitImageQualityPassScore;
    }

    public void setMaxOutfitAttempts(int maxOutfitAttempts) {
        this.maxOutfitAttempts = maxOutfitAttempts;
    }

    public int getOutfitAcceptAndLearnScore() {
        return outfitAcceptAndLearnScore;
    }

    public void setOutfitAcceptAndLearnScore(int outfitAcceptAndLearnScore) {
        this.outfitAcceptAndLearnScore = Math.max(0, Math.min(100, outfitAcceptAndLearnScore));
    }

    public int getQualityPassScore() {
        return qualityPassScore;
    }

    public void setQualityPassScore(int qualityPassScore) {
        this.qualityPassScore = qualityPassScore;
    }

    public int getClothingMatchPassScore() {
        return clothingMatchPassScore;
    }

    public void setClothingMatchPassScore(int clothingMatchPassScore) {
        this.clothingMatchPassScore = clothingMatchPassScore;
    }

    public int getHeadAccessoryPassScore() {
        return headAccessoryPassScore;
    }

    public void setHeadAccessoryPassScore(int headAccessoryPassScore) {
        this.headAccessoryPassScore = headAccessoryPassScore;
    }

    public int getIdentityPassScore() {
        return identityPassScore;
    }

    public void setIdentityPassScore(int identityPassScore) {
        this.identityPassScore = identityPassScore;
    }

    public boolean isModelConfigured() {
        String effective = getApiKey();
        return enabled && effective != null && !effective.isBlank();
    }

    public String requiredApiKey() {
        if (!isModelConfigured()) {
            throw new IllegalStateException("请先配置 ZHIPU_API_KEY");
        }
        return getApiKey().trim();
    }
}
