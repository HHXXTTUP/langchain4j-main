package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountContext;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "comfyui.video")
public class ComfyUiVideoProperties {

    private URI baseUrl = URI.create("https://autodl.art");
    private String token;
    private String workflowId = "minimax_h3_lightx2v_v5_15s";
    private String firstLastWorkflowId = "minimax_h3_lightx2v";
    private Path directory = Path.of("generated", "comfyui-video");
    private Path exportDirectory = Path.of("E:/AI视频文件夹");
    private Duration pollInterval = Duration.ofSeconds(10);
    private Duration taskTimeout = Duration.ofMinutes(30);
    private int maxImages = 9;
    private long maxImageBytes = 15L * 1024 * 1024;
    private long maxTotalImageBytes = 60L * 1024 * 1024;
    private int maxPromptLength = 4000;

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public String getToken() { return AccountContext.secretValue("comfyuiToken", token); }
    public void setToken(String token) { this.token = token; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getFirstLastWorkflowId() { return firstLastWorkflowId; }
    public void setFirstLastWorkflowId(String firstLastWorkflowId) { this.firstLastWorkflowId = firstLastWorkflowId; }
    public Path getDirectory() {
        String configured = AccountContext.value("generatedDirectory", null);
        return configured == null || configured.isBlank() ? directory : Path.of(configured).resolve("comfyui-video");
    }
    public void setDirectory(Path directory) { this.directory = directory; }
    public Path getExportDirectory() {
        String configured = AccountContext.value("videoExportDirectory", null);
        return configured == null || configured.isBlank() ? exportDirectory : Path.of(configured);
    }
    public void setExportDirectory(Path exportDirectory) { this.exportDirectory = exportDirectory; }
    public Duration getPollInterval() { return pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public Duration getTaskTimeout() { return taskTimeout; }
    public void setTaskTimeout(Duration taskTimeout) { this.taskTimeout = taskTimeout; }
    public int getMaxImages() { return maxImages; }
    public void setMaxImages(int maxImages) { this.maxImages = maxImages; }
    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    public long getMaxTotalImageBytes() { return maxTotalImageBytes; }
    public void setMaxTotalImageBytes(long maxTotalImageBytes) { this.maxTotalImageBytes = maxTotalImageBytes; }
    public int getMaxPromptLength() { return maxPromptLength; }
    public void setMaxPromptLength(int maxPromptLength) { this.maxPromptLength = maxPromptLength; }
}
