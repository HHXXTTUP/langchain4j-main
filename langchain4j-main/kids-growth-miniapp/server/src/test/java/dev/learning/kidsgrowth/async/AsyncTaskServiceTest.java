package dev.learning.kidsgrowth.async;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsyncTaskServiceTest {

    private final AsyncTaskService service = new AsyncTaskService(Runnable::run);

    @Test
    void returnsCompletedTaskResult() {
        var submission = service.submit(() -> "ready");

        var task = service.get(submission.taskId());

        assertThat(task.status()).isEqualTo(AsyncTaskService.TaskStatus.SUCCEEDED);
        assertThat(task.result()).isEqualTo("ready");
        assertThat(task.error()).isNull();
    }

    @Test
    void exposesFriendlyFailureMessage() {
        var submission = service.submit(() -> {
            throw new IllegalStateException("服务开小差了~");
        });

        var task = service.get(submission.taskId());

        assertThat(task.status()).isEqualTo(AsyncTaskService.TaskStatus.FAILED);
        assertThat(task.error()).isEqualTo("服务开小差了~");
        assertThat(task.result()).isNull();
    }
}
