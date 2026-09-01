package dev.learning.fashionagent.web;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/logs")
class RuntimeLogController {

    private static final int MAX_TAIL_BYTES = 4 * 1024 * 1024;

    private final Path logFile;

    RuntimeLogController(@Value("${logging.file.name:logs/fashion-image-agent-demo.log}") String logFile) {
        this.logFile = Path.of(logFile).toAbsolutePath().normalize();
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> logs(@RequestParam(defaultValue = "500") int lines) throws IOException {
        if (!Files.isRegularFile(logFile)) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("日志文件尚未创建");
        }
        int requestedLines = Math.max(50, Math.min(lines, 2000));
        List<String> allLines = readTail(logFile);
        int fromIndex = Math.max(0, allLines.size() - requestedLines);
        String content = String.join(System.lineSeparator(), allLines.subList(fromIndex, allLines.size()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content);
    }

    private static List<String> readTail(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            long start = Math.max(0, fileSize - MAX_TAIL_BYTES);
            int byteCount = Math.toIntExact(fileSize - start);
            ByteBuffer buffer = ByteBuffer.allocate(byteCount);
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Continue until the selected tail has been read or EOF is reached.
            }
            buffer.flip();
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(buffer)
                    .toString();
            if (start > 0) {
                int firstLineBreak = text.indexOf('\n');
                text = firstLineBreak < 0 ? "" : text.substring(firstLineBreak + 1);
            }
            return Arrays.asList(text.split("\\R", -1));
        }
    }
}
