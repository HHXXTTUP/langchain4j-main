package dev.learning.kidsgrowth.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kids.edge-tts")
public class EdgeTtsProperties {

    private boolean enabled = true;
    private String pythonCommand;
    private String englishVoice = "en-US-AnaNeural";
    private String chineseVoice = "zh-CN-XiaoyiNeural";
    private String rate = "-8%";
    private String pitch = "+4Hz";
    private Duration timeout = Duration.ofSeconds(30);

    public String resolvedPythonCommand() {
        if (pythonCommand != null && !pythonCommand.isBlank()) {
            return normalizeExecutable(pythonCommand.trim());
        }
        for (Path root : searchRoots()) {
            Path windowsVenv = root.resolve(".venv-edge-tts").resolve("Scripts").resolve("python.exe");
            if (Files.isRegularFile(windowsVenv)) {
                return windowsVenv.toAbsolutePath().normalize().toString();
            }
            Path unixVenv = root.resolve(".venv-edge-tts").resolve("bin").resolve("python");
            if (Files.isRegularFile(unixVenv)) {
                return unixVenv.toAbsolutePath().normalize().toString();
            }
        }
        return "python";
    }

    private static Set<Path> searchRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        addRootAndParents(roots, Path.of(System.getProperty("user.dir", ".")));
        try {
            Path codeLocation = Path.of(EdgeTtsProperties.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            addRootAndParents(roots, Files.isRegularFile(codeLocation)
                    ? codeLocation.getParent()
                    : codeLocation);
        } catch (Exception ignored) {
            // Fall back to the working directory when running from an unusual launcher.
        }
        return roots;
    }

    private static void addRootAndParents(Set<Path> roots, Path path) {
        if (path == null) {
            return;
        }
        Path current = path.toAbsolutePath().normalize();
        for (int level = 0; level < 6 && current != null; level++) {
            roots.add(current);
            roots.add(current.resolve("server"));
            roots.add(current.resolve("kids-growth-miniapp").resolve("server"));
            current = current.getParent();
        }
    }

    private static String normalizeExecutable(String executable) {
        if (executable.contains("/") || executable.contains("\\")) {
            Path path = Path.of(executable);
            return (path.isAbsolute() ? path : path.toAbsolutePath()).normalize().toString();
        }
        return executable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    public void setPythonCommand(String pythonCommand) {
        this.pythonCommand = pythonCommand;
    }

    public String getEnglishVoice() {
        return englishVoice;
    }

    public void setEnglishVoice(String englishVoice) {
        this.englishVoice = englishVoice;
    }

    public String getChineseVoice() {
        return chineseVoice;
    }

    public void setChineseVoice(String chineseVoice) {
        this.chineseVoice = chineseVoice;
    }

    public String getRate() {
        return rate;
    }

    public void setRate(String rate) {
        this.rate = rate;
    }

    public String getPitch() {
        return pitch;
    }

    public void setPitch(String pitch) {
        this.pitch = pitch;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
