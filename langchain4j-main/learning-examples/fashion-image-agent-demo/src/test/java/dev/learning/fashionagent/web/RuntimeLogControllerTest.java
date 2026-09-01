package dev.learning.fashionagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class RuntimeLogControllerTest {

    @Test
    void shouldReturnLogTailEvenWhenOldLogContainsMalformedUtf8() throws Exception {
        Path tempDirectory = Path.of("target", "test-runtime-logs", UUID.randomUUID().toString());
        Files.createDirectories(tempDirectory);
        Path logFile = tempDirectory.resolve("mixed-encoding.log");
        Files.write(logFile, new byte[] {
                'o', 'l', 'd', ':', (byte) 0xC3, (byte) 0x28, '\n',
                'n', 'e', 'w', ':', 'o', 'k', '\n'
        });
        RuntimeLogController controller = new RuntimeLogController(logFile.toString());

        ResponseEntity<String> response = controller.logs(100);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("new:ok"));
        assertTrue(response.getBody().contains("\uFFFD"));
    }
}
