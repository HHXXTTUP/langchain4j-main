package dev.learning.imagegen;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", rootMessage(exception)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> handleGenerationFailure(Exception exception) {
        LOGGER.error("Image generation request failed", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", rootMessage(exception)));
    }

    private static String rootMessage(Throwable throwable) {
        String lastMessage = null;
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                lastMessage = current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return lastMessage == null ? "图片生成失败" : lastMessage;
    }
}
