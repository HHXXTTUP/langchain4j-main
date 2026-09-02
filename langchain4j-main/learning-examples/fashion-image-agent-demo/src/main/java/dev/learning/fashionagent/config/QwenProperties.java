package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountContext;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qwen")
public class QwenProperties {
    private URI baseUrl = URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1");
    private String apiKey;
    private String model = "qwen3.8-max";
    // Thinking can keep a non-streaming proxy connection idle for several
    // minutes. Keep the compatible Chat Completions request lean by default.
    private boolean thinkingEnabled = false;
    private Duration connectTimeout = Duration.ofSeconds(60);
    private Duration readTimeout = Duration.ofMinutes(30);
    // The desktop runtime reaches DashScope through the local VPN proxy.
    // Set QWEN_PROXY_ENABLED=false only when direct TLS is available.
    private boolean proxyEnabled = true;
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 7897;
    // OpenAI-compatible video_url accepts Base64 only for files below 7 MB.
    private long maxVideoBytes = 7L * 1024 * 1024;
    private String outputDirectory = "generated/video-scripts";
    // Automatic retries are disabled; a new request is only started by the
    // explicit retry action in the UI. Keep these properties for config-file
    // compatibility with older installations.
    private int analysisMaxAttempts = 1;
    private Duration analysisRetryDelay = Duration.ZERO;

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return AccountContext.secretValue("qwenKey", apiKey); }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public boolean isThinkingEnabled() { return thinkingEnabled; }
    public void setThinkingEnabled(boolean thinkingEnabled) { this.thinkingEnabled = thinkingEnabled; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public boolean isProxyEnabled() { return proxyEnabled; }
    public void setProxyEnabled(boolean proxyEnabled) { this.proxyEnabled = proxyEnabled; }
    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
    public long getMaxVideoBytes() { return maxVideoBytes; }
    public void setMaxVideoBytes(long maxVideoBytes) { this.maxVideoBytes = maxVideoBytes; }
    public String getOutputDirectory() { return AccountContext.value("qwenOutputDirectory", outputDirectory); }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
    public int getAnalysisMaxAttempts() { return analysisMaxAttempts; }
    public void setAnalysisMaxAttempts(int analysisMaxAttempts) { this.analysisMaxAttempts = analysisMaxAttempts; }
    public Duration getAnalysisRetryDelay() { return analysisRetryDelay; }
    public void setAnalysisRetryDelay(Duration analysisRetryDelay) { this.analysisRetryDelay = analysisRetryDelay; }

    public String requiredApiKey() {
        String effective = getApiKey();
        if (effective == null || effective.isBlank()) {
            throw new IllegalStateException("请先在账号配置中填写千问 API Key");
        }
        return effective.trim();
    }
}
