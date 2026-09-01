package dev.learning.kidsgrowth.web;

import dev.learning.kidsgrowth.animation.AnimationSceneService;
import dev.learning.kidsgrowth.async.AsyncTaskService;
import dev.learning.kidsgrowth.async.AsyncTaskService.TaskSnapshot;
import dev.learning.kidsgrowth.async.AsyncTaskService.TaskSubmission;
import dev.learning.kidsgrowth.learning.LearningSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class AsyncOperationController {

    private final AsyncTaskService tasks;
    private final LearningSessionService learning;
    private final AnimationSceneService animation;

    public AsyncOperationController(
            AsyncTaskService tasks,
            LearningSessionService learning,
            AnimationSceneService animation) {
        this.tasks = tasks;
        this.learning = learning;
        this.animation = animation;
    }

    @PostMapping("/learning/sessions")
    ResponseEntity<TaskSubmission> createLesson(@Valid @RequestBody CreateLessonRequest request) {
        return accepted(() -> learning.createLesson(request.chineseText()));
    }

    @PostMapping("/learning/sessions/{sessionId}/questions/next")
    ResponseEntity<TaskSubmission> nextQuestion(@PathVariable String sessionId) {
        return accepted(() -> learning.nextQuestion(sessionId));
    }

    @PostMapping("/learning/sessions/{sessionId}/praise")
    ResponseEntity<TaskSubmission> praise(@PathVariable String sessionId) {
        return accepted(() -> learning.praiseWithoutRecognition(sessionId));
    }

    @PostMapping(
            value = "/learning/sessions/{sessionId}/attempts",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<TaskSubmission> evaluateAttempt(
            @PathVariable String sessionId,
            @RequestBody byte[] audio) {
        if (audio.length == 0) {
            throw new IllegalArgumentException("录音内容为空");
        }
        return accepted(() -> learning.evaluateAttempt(sessionId, audio));
    }

    @PostMapping("/animation/scenes")
    ResponseEntity<TaskSubmission> createScene(@Valid @RequestBody CreateAnimationRequest request) {
        return accepted(() -> animation.generate(
                request.chineseText(),
                request.englishText(),
                request.style()));
    }

    @GetMapping("/{taskId}")
    TaskSnapshot task(@PathVariable String taskId) {
        return tasks.get(taskId);
    }

    private ResponseEntity<TaskSubmission> accepted(SupplierWithResult operation) {
        return ResponseEntity.accepted().body(tasks.submit(operation::get));
    }

    @FunctionalInterface
    private interface SupplierWithResult {
        Object get();
    }

    public record CreateLessonRequest(
            @NotBlank(message = "请先输入想学习的中文单词")
            @Size(max = 30, message = "请输入30个字以内的单词或短语")
            String chineseText) {}

    public record CreateAnimationRequest(
            @NotBlank(message = "请先输入想做成动画的中文单词")
            @Size(max = 30, message = "请输入30个字以内的单词或短语")
            String chineseText,
            @Size(max = 80, message = "英文单词或短语不能超过80个字符")
            String englishText,
            String style) {}
}
