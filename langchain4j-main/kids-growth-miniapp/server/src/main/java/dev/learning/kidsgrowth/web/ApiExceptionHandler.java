package dev.learning.kidsgrowth.web;

import dev.learning.kidsgrowth.learning.LearningSessionNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求内容不正确");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(LearningSessionNotFoundException.class)
    ResponseEntity<ApiError> notFound(LearningSessionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    ResponseEntity<ApiError> unavailable(ExternalServiceUnavailableException exception) {
        LOGGER.warn("外部 AI 或语音服务暂不可用：{}", exception.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        LOGGER.error("学习接口出现未处理异常", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "学习服务暂时开小差了，请稍后再试");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), message, Instant.now()));
    }

    record ApiError(int status, String message, Instant timestamp) {}
}
