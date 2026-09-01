package dev.learning.kidsgrowth.speech;

import static org.assertj.core.api.Assertions.assertThat;

import dev.learning.kidsgrowth.config.EdgeTtsProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EdgeTtsClientTest {

    @Test
    void shouldKeepSignedVoiceSettingsInSingleArguments() {
        EdgeTtsProperties properties = new EdgeTtsProperties();
        properties.setPythonCommand("python");
        properties.setRate("-8%");
        properties.setPitch("+4Hz");
        EdgeTtsClient client = new EdgeTtsClient(properties);

        var command = client.buildCommand(
                "Hello, little star!",
                SpeechVoice.ENGLISH_CHILD,
                Path.of("lesson.mp3"));

        assertThat(command).contains("--rate=-8%", "--pitch=+4Hz");
        assertThat(command).doesNotContain("--rate", "-8%", "--pitch", "+4Hz");
    }
}
