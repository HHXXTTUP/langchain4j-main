package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountContext;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "runninghub")
public class RunningHubProperties {

    private URI baseUrl = URI.create("https://www.runninghub.cn");
    private String apiKey;
    private String beautyAppId = "2066795888403640322";
    private String enhancedBeautyAppId = "2039516680887472130";
    private String outfitAppId = "2062480340836511746";
    private String auditRedrawAppId = "2057132474349735937";
    private String videoMotionAppId = "1975951975441412098";
    private String videoInstanceType = "plus";
    private Path clothingDirectory = Path.of("clothing");
    private Path videoDirectory = Path.of("video_ai");
    private Path generatedDirectory = Path.of("generated");
    private Path videoExportDirectory = Path.of("E:/AI视频文件夹");
    private Path auditRedrawOutputDirectory = Path.of("E:/AI过审图");
    private Duration pollInterval = Duration.ofSeconds(3);
    private Duration taskTimeout = Duration.ofMinutes(10);
    private Duration uploadConnectTimeout = Duration.ofSeconds(10);
    private Duration uploadReadTimeout = Duration.ofSeconds(90);
    private int uploadMaxAttempts = 3;
    private Duration uploadRetryDelay = Duration.ofSeconds(2);
    private long maxUploadBytes = 20L * 1024 * 1024;
    private int downloadMaxAttempts = 4;
    private Duration downloadRetryDelay = Duration.ofSeconds(2);
    private Duration downloadConnectTimeout = Duration.ofSeconds(10);
    private Duration downloadReadTimeout = Duration.ofSeconds(30);
    private long maxDownloadBytes = 20L * 1024 * 1024;
    private Duration videoPollInterval = Duration.ofMinutes(2);
    private Duration videoTaskTimeout = Duration.ofMinutes(45);
    private long videoMaxUploadBytes = 200L * 1024 * 1024;
    private long videoMaxDownloadBytes = 500L * 1024 * 1024;
    private int videoDownloadMaxAttempts = 12;
    private Duration videoDownloadRetryDelay = Duration.ofSeconds(5);
    private boolean videoDownloadAllowHttpFallback = true;
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private Duration ffmpegTimeout = Duration.ofMinutes(15);
    private int videoMotionInputWidth = 1080;
    private int videoMotionInputHeight = 1920;
    private long videoMotionInputMaxPixels = 2_500_000L;
    private int videoMotionInputFrameRate = 30;
    private int videoMotionMaxFrames = 840;
    private long videoMotionMaxPixelFrames = 2_000_000_000L;
    private String videoMotionResolutionPreset = "2";
    private int videoOutputWidth = 1080;
    private int videoOutputHeight = 1920;
    private int videoOutputFrameRate = 30;
    private Duration videoTransitionDuration = Duration.ofSeconds(1);

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return AccountContext.secretValue("runninghubKey", apiKey);
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOutfitAppId() {
        return outfitAppId;
    }

    public String getBeautyAppId() {
        return beautyAppId;
    }

    public void setBeautyAppId(String beautyAppId) {
        this.beautyAppId = beautyAppId;
    }

    public String getEnhancedBeautyAppId() {
        return enhancedBeautyAppId;
    }

    public void setEnhancedBeautyAppId(String enhancedBeautyAppId) {
        this.enhancedBeautyAppId = enhancedBeautyAppId;
    }

    public void setOutfitAppId(String outfitAppId) {
        this.outfitAppId = outfitAppId;
    }

    public String getAuditRedrawAppId() {
        return auditRedrawAppId;
    }

    public void setAuditRedrawAppId(String auditRedrawAppId) {
        this.auditRedrawAppId = auditRedrawAppId;
    }

    public String getVideoMotionAppId() {
        return videoMotionAppId;
    }

    public void setVideoMotionAppId(String videoMotionAppId) {
        this.videoMotionAppId = videoMotionAppId;
    }

    public String getVideoInstanceType() {
        return videoInstanceType;
    }

    public void setVideoInstanceType(String videoInstanceType) {
        this.videoInstanceType = videoInstanceType;
    }

    public Path getClothingDirectory() {
        return Path.of(AccountContext.value("clothingDirectory", clothingDirectory.toString()));
    }

    public void setClothingDirectory(Path clothingDirectory) {
        this.clothingDirectory = clothingDirectory;
    }

    public Path getVideoDirectory() {
        return Path.of(AccountContext.value("videoDirectory", videoDirectory.toString()));
    }

    public void setVideoDirectory(Path videoDirectory) {
        this.videoDirectory = videoDirectory;
    }

    public Path getGeneratedDirectory() {
        return Path.of(AccountContext.value("generatedDirectory", generatedDirectory.toString()));
    }

    public void setGeneratedDirectory(Path generatedDirectory) {
        this.generatedDirectory = generatedDirectory;
    }

    public Path getVideoExportDirectory() {
        return Path.of(AccountContext.value("videoExportDirectory", videoExportDirectory.toString()));
    }

    public void setVideoExportDirectory(Path videoExportDirectory) {
        this.videoExportDirectory = videoExportDirectory;
    }

    public Path getAuditRedrawOutputDirectory() {
        return Path.of(AccountContext.value("auditOutputDirectory", auditRedrawOutputDirectory.toString()));
    }

    public void setAuditRedrawOutputDirectory(Path auditRedrawOutputDirectory) {
        this.auditRedrawOutputDirectory = auditRedrawOutputDirectory;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public Duration getTaskTimeout() {
        return taskTimeout;
    }

    public void setTaskTimeout(Duration taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

    public Duration getUploadConnectTimeout() {
        return uploadConnectTimeout;
    }

    public void setUploadConnectTimeout(Duration uploadConnectTimeout) {
        this.uploadConnectTimeout = uploadConnectTimeout;
    }

    public Duration getUploadReadTimeout() {
        return uploadReadTimeout;
    }

    public void setUploadReadTimeout(Duration uploadReadTimeout) {
        this.uploadReadTimeout = uploadReadTimeout;
    }

    public int getUploadMaxAttempts() {
        return uploadMaxAttempts;
    }

    public void setUploadMaxAttempts(int uploadMaxAttempts) {
        this.uploadMaxAttempts = uploadMaxAttempts;
    }

    public Duration getUploadRetryDelay() {
        return uploadRetryDelay;
    }

    public void setUploadRetryDelay(Duration uploadRetryDelay) {
        this.uploadRetryDelay = uploadRetryDelay;
    }

    public long getMaxUploadBytes() {
        return maxUploadBytes;
    }

    public void setMaxUploadBytes(long maxUploadBytes) {
        this.maxUploadBytes = maxUploadBytes;
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

    public Duration getDownloadConnectTimeout() {
        return downloadConnectTimeout;
    }

    public void setDownloadConnectTimeout(Duration downloadConnectTimeout) {
        this.downloadConnectTimeout = downloadConnectTimeout;
    }

    public Duration getDownloadReadTimeout() {
        return downloadReadTimeout;
    }

    public void setDownloadReadTimeout(Duration downloadReadTimeout) {
        this.downloadReadTimeout = downloadReadTimeout;
    }

    public long getMaxDownloadBytes() {
        return maxDownloadBytes;
    }

    public void setMaxDownloadBytes(long maxDownloadBytes) {
        this.maxDownloadBytes = maxDownloadBytes;
    }

    public Duration getVideoPollInterval() {
        return videoPollInterval;
    }

    public void setVideoPollInterval(Duration videoPollInterval) {
        this.videoPollInterval = videoPollInterval;
    }

    public Duration getVideoTaskTimeout() {
        return videoTaskTimeout;
    }

    public void setVideoTaskTimeout(Duration videoTaskTimeout) {
        this.videoTaskTimeout = videoTaskTimeout;
    }

    public long getVideoMaxUploadBytes() {
        return videoMaxUploadBytes;
    }

    public void setVideoMaxUploadBytes(long videoMaxUploadBytes) {
        this.videoMaxUploadBytes = videoMaxUploadBytes;
    }

    public long getVideoMaxDownloadBytes() {
        return videoMaxDownloadBytes;
    }

    public void setVideoMaxDownloadBytes(long videoMaxDownloadBytes) {
        this.videoMaxDownloadBytes = videoMaxDownloadBytes;
    }

    public int getVideoDownloadMaxAttempts() {
        return videoDownloadMaxAttempts;
    }

    public void setVideoDownloadMaxAttempts(int videoDownloadMaxAttempts) {
        this.videoDownloadMaxAttempts = videoDownloadMaxAttempts;
    }

    public Duration getVideoDownloadRetryDelay() {
        return videoDownloadRetryDelay;
    }

    public void setVideoDownloadRetryDelay(Duration videoDownloadRetryDelay) {
        this.videoDownloadRetryDelay = videoDownloadRetryDelay;
    }

    public boolean isVideoDownloadAllowHttpFallback() {
        return videoDownloadAllowHttpFallback;
    }

    public void setVideoDownloadAllowHttpFallback(boolean videoDownloadAllowHttpFallback) {
        this.videoDownloadAllowHttpFallback = videoDownloadAllowHttpFallback;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getFfprobePath() {
        return ffprobePath;
    }

    public void setFfprobePath(String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    public Duration getFfmpegTimeout() {
        return ffmpegTimeout;
    }

    public void setFfmpegTimeout(Duration ffmpegTimeout) {
        this.ffmpegTimeout = ffmpegTimeout;
    }

    public int getVideoMotionInputWidth() {
        return videoMotionInputWidth;
    }

    public void setVideoMotionInputWidth(int videoMotionInputWidth) {
        this.videoMotionInputWidth = videoMotionInputWidth;
    }

    public int getVideoMotionInputHeight() {
        return videoMotionInputHeight;
    }

    public void setVideoMotionInputHeight(int videoMotionInputHeight) {
        this.videoMotionInputHeight = videoMotionInputHeight;
    }

    public long getVideoMotionInputMaxPixels() {
        return videoMotionInputMaxPixels;
    }

    public void setVideoMotionInputMaxPixels(long videoMotionInputMaxPixels) {
        this.videoMotionInputMaxPixels = videoMotionInputMaxPixels;
    }

    public int getVideoMotionInputFrameRate() {
        return videoMotionInputFrameRate;
    }

    public void setVideoMotionInputFrameRate(int videoMotionInputFrameRate) {
        this.videoMotionInputFrameRate = videoMotionInputFrameRate;
    }

    public int getVideoMotionMaxFrames() {
        return videoMotionMaxFrames;
    }

    public void setVideoMotionMaxFrames(int videoMotionMaxFrames) {
        this.videoMotionMaxFrames = videoMotionMaxFrames;
    }

    public long getVideoMotionMaxPixelFrames() {
        return videoMotionMaxPixelFrames;
    }

    public void setVideoMotionMaxPixelFrames(long videoMotionMaxPixelFrames) {
        this.videoMotionMaxPixelFrames = videoMotionMaxPixelFrames;
    }

    public String getVideoMotionResolutionPreset() {
        return videoMotionResolutionPreset;
    }

    public void setVideoMotionResolutionPreset(String videoMotionResolutionPreset) {
        this.videoMotionResolutionPreset = videoMotionResolutionPreset;
    }

    public int getVideoOutputWidth() {
        return videoOutputWidth;
    }

    public void setVideoOutputWidth(int videoOutputWidth) {
        this.videoOutputWidth = videoOutputWidth;
    }

    public int getVideoOutputHeight() {
        return videoOutputHeight;
    }

    public void setVideoOutputHeight(int videoOutputHeight) {
        this.videoOutputHeight = videoOutputHeight;
    }

    public int getVideoOutputFrameRate() {
        return videoOutputFrameRate;
    }

    public void setVideoOutputFrameRate(int videoOutputFrameRate) {
        this.videoOutputFrameRate = videoOutputFrameRate;
    }

    public Duration getVideoTransitionDuration() {
        return videoTransitionDuration;
    }

    public void setVideoTransitionDuration(Duration videoTransitionDuration) {
        this.videoTransitionDuration = videoTransitionDuration;
    }

    public String requiredApiKey() {
        if (!isApiKeyConfigured()) {
            throw new IllegalStateException("请先配置 RUNNINGHUB_API_KEY");
        }
        return getApiKey().trim();
    }

    public boolean isApiKeyConfigured() {
        String effective = getApiKey();
        return effective != null && !effective.isBlank();
    }
}
