package dev.learning.kidsgrowth.async;

import dev.learning.kidsgrowth.learning.LearningSessionNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AsyncTaskService {

    private static final long MAX_AGE_SECONDS = 60 * 60;

    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final Executor executor;

    public AsyncTaskService(@Qualifier("learningTaskExecutor") Executor executor) {
        this.executor = executor;
    }

    public TaskSubmission submit(Supplier<?> operation) {
        cleanupExpired();
        String taskId = UUID.randomUUID().toString();
        TaskState task = new TaskState(taskId);
        tasks.put(taskId, task);
        executor.execute(() -> run(task, operation));
        return new TaskSubmission(taskId);
    }

    public TaskSnapshot get(String taskId) {
        cleanupExpired();
        TaskState task = tasks.get(taskId);
        if (task == null) {
            throw new LearningSessionNotFoundException("任务不存在或已经过期，请重新操作");
        }
        return task.snapshot();
    }

    private static void run(TaskState task, Supplier<?> operation) {
        task.status = TaskStatus.RUNNING;
        try {
            task.result = operation.get();
            task.status = TaskStatus.SUCCEEDED;
        } catch (Exception exception) {
            task.error = friendlyMessage(exception);
            task.status = TaskStatus.FAILED;
        }
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minusSeconds(MAX_AGE_SECONDS);
        tasks.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(cutoff));
    }

    private static String friendlyMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "服务开小差了~";
        }
        return message;
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record TaskSubmission(String taskId) {}

    public record TaskSnapshot(
            String taskId,
            TaskStatus status,
            Object result,
            String error) {}

    private static final class TaskState {
        private final String taskId;
        private final Instant createdAt = Instant.now();
        private volatile TaskStatus status = TaskStatus.PENDING;
        private volatile Object result;
        private volatile String error;

        private TaskState(String taskId) {
            this.taskId = taskId;
        }

        private TaskSnapshot snapshot() {
            return new TaskSnapshot(taskId, status, result, error);
        }
    }
}
