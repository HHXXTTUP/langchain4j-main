package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountContext;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snapany")
public class SnapAnyProperties {

    private URI baseUrl = URI.create("https://api.snapany.com");
    private String apiKey;
    // Disabled for direct-instantiation tests; application.yml enables this
    // and follows the same QWEN proxy settings by default.
    private boolean proxyEnabled;
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 7897;
    private int targetQuality = 1080;
    private Duration connectTimeout = Duration.ofSeconds(15);
    private Duration readTimeout = Duration.ofMinutes(5);
    private long maxDownloadBytes = 500L * 1024 * 1024;
    private int maxUrlsPerImport = 50;
    private int downloadMaxAttempts = 3;
    private Duration downloadRetryDelay = Duration.ofSeconds(3);
    private boolean chromiumFallbackEnabled = true;
    private String chromiumExecutable;
    private int chromiumChunkBytes = 1024 * 1024;
    private Duration chromiumStartupTimeout = Duration.ofSeconds(20);

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return AccountContext.secretValue("snapanyKey", apiKey);
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
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

    public int getTargetQuality() {
        return targetQuality;
    }

    public void setTargetQuality(int targetQuality) {
        this.targetQuality = targetQuality;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public long getMaxDownloadBytes() {
        return maxDownloadBytes;
    }

    public void setMaxDownloadBytes(long maxDownloadBytes) {
        this.maxDownloadBytes = maxDownloadBytes;
    }

    public int getMaxUrlsPerImport() {
        return maxUrlsPerImport;
    }

    public void setMaxUrlsPerImport(int maxUrlsPerImport) {
        this.maxUrlsPerImport = maxUrlsPerImport;
    }

    public int getDownloadMaxAttempts() {
        return downloadMaxAttempts;
    }

    public void setDownloadMaxAttempts(int downloadMaxAttempts) {
        this.downloadMaxAttempts = downloadMaxAttempts;
    }

    public Duration getDownloadRetryDelay() {
        return downloadRetryDelay;
    }

    public void setDownloadRetryDelay(Duration downloadRetryDelay) {
        this.downloadRetryDelay = downloadRetryDelay;
    }

    public boolean isChromiumFallbackEnabled() {
        return chromiumFallbackEnabled;
    }

    public void setChromiumFallbackEnabled(boolean chromiumFallbackEnabled) {
        this.chromiumFallbackEnabled = chromiumFallbackEnabled;
    }

    public String getChromiumExecutable() {
        return chromiumExecutable;
    }

    public void setChromiumExecutable(String chromiumExecutable) {
        this.chromiumExecutable = chromiumExecutable;
    }

    public int getChromiumChunkBytes() {
        return chromiumChunkBytes;
    }

    public void setChromiumChunkBytes(int chromiumChunkBytes) {
        this.chromiumChunkBytes = chromiumChunkBytes;
    }

    public Duration getChromiumStartupTimeout() {
        return chromiumStartupTimeout;
    }

    public void setChromiumStartupTimeout(Duration chromiumStartupTimeout) {
        this.chromiumStartupTimeout = chromiumStartupTimeout;
    }

    public String requiredApiKey() {
        String effective = getApiKey();
        if (effective == null || effective.isBlank()) {
            throw new IllegalStateException("请先在账号配置中填写 SNAPANY API Key");
        }
        return effective.trim();
    }
}
