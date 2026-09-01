package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountContext;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "story.video")
public class StoryVideoProperties {
    private String whisperCommand;
    private Path replicationDirectory = Path.of("E:/AI影视复刻");
    public String getWhisperCommand() { return whisperCommand; }
    public void setWhisperCommand(String whisperCommand) { this.whisperCommand = whisperCommand; }
    public Path getReplicationDirectory() { return Path.of(AccountContext.value("storyOutputDirectory", replicationDirectory.toString())); }
    public void setReplicationDirectory(Path replicationDirectory) { this.replicationDirectory = replicationDirectory; }
}
