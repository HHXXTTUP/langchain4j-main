package dev.learning.fashionagent.browser;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "video-browser")
public class VideoBrowserProperties {
    private String browserExecutable = "";
    private String extensionDirectory = "";
    private String profileRoot = "video-browser-profiles";
    private String startUrl = "https://www.dola.com/";

    public String getBrowserExecutable() { return browserExecutable; }
    public void setBrowserExecutable(String browserExecutable) { this.browserExecutable = browserExecutable == null ? "" : browserExecutable.trim(); }
    public String getExtensionDirectory() { return extensionDirectory; }
    public void setExtensionDirectory(String extensionDirectory) { this.extensionDirectory = extensionDirectory == null ? "" : extensionDirectory.trim(); }
    public String getProfileRoot() { return profileRoot; }
    public void setProfileRoot(String profileRoot) { this.profileRoot = profileRoot == null || profileRoot.isBlank() ? "video-browser-profiles" : profileRoot.trim(); }
    public String getStartUrl() { return startUrl; }
    public void setStartUrl(String startUrl) { this.startUrl = startUrl == null || startUrl.isBlank() ? "https://www.dola.com/" : startUrl.trim(); }
    public Path profileRootPath() { return Path.of(profileRoot).toAbsolutePath().normalize(); }
}
