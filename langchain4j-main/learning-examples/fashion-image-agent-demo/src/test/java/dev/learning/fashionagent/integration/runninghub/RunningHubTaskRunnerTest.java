package dev.learning.fashionagent.integration.runninghub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.config.RunningHubProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunningHubTaskRunnerTest {

    @Test
    void shouldPollUntilImageIsReady() {
        RunningHubClient client = mock(RunningHubClient.class);
        RunningHubProperties properties = new RunningHubProperties();
        properties.setPollInterval(Duration.ofMillis(1));
        properties.setTaskTimeout(Duration.ofSeconds(1));
        RunningHubTaskRunner runner = new RunningHubTaskRunner(client, properties);

        when(client.submit(eq("app-1"), anyList()))
                .thenReturn(new RunningHubClient.TaskResponse("task-1", "RUNNING", "", "", null, null, null));
        when(client.query("task-1"))
                .thenReturn(new RunningHubClient.TaskResponse(
                        "task-1",
                        "SUCCESS",
                        "",
                        "",
                        null,
                        null,
                        List.of(new RunningHubClient.TaskResult(
                                "https://example.com/result.png", "2", "png", null))));

        RunningHubTaskRunner.TaskOutput output = runner.run("app-1", List.of(), ignored -> {});

        assertEquals("https://example.com/result.png", output.url());
        verify(client).query("task-1");
    }

    @Test
    void shouldClassifyRunningHub805AsContentAuditFailure() {
        RunningHubClient client = mock(RunningHubClient.class);
        RunningHubProperties properties = new RunningHubProperties();
        RunningHubTaskRunner runner = new RunningHubTaskRunner(client, properties);
        when(client.submit(eq("app-1"), anyList()))
                .thenReturn(new RunningHubClient.TaskResponse(
                        "task-1",
                        "FAILED",
                        "805",
                        "工作流运行失败",
                        "{exception_type=audit.RHAuditException, exception_message=Porn}",
                        null,
                        null));

        RunningHubContentAuditException exception = assertThrows(
                RunningHubContentAuditException.class,
                () -> runner.run("app-1", List.of(), ignored -> {}));

        assertEquals("805", exception.errorCode());
        assertTrue(exception.getMessage().contains("内容审核未通过"));
    }

    @Test
    void shouldKeepRunningHub805OutOfMemoryAsWorkflowFailure() {
        RunningHubClient client = mock(RunningHubClient.class);
        RunningHubProperties properties = new RunningHubProperties();
        RunningHubTaskRunner runner = new RunningHubTaskRunner(client, properties);
        when(client.submit(eq("video-app"), anyList(), eq("plus")))
                .thenReturn(new RunningHubClient.TaskResponse(
                        "task-1",
                        "FAILED",
                        "805",
                        "工作流运行失败",
                        "{exception_type=torch.OutOfMemoryError, exception_message=显存不足}",
                        null,
                        null));

        RunningHubException exception = assertThrows(
                RunningHubException.class,
                () -> runner.runVideo("video-app", List.of(), ignored -> {}));

        assertEquals(RunningHubException.class, exception.getClass());
        assertTrue(exception.getMessage().contains("任务失败"));
        assertTrue(exception.getMessage().contains("显存不足"));
    }

    @Test
    void shouldPollVideoAtTheDedicatedIntervalAndReturnMp4() {
        RunningHubClient client = mock(RunningHubClient.class);
        RunningHubProperties properties = new RunningHubProperties();
        properties.setVideoPollInterval(Duration.ZERO);
        properties.setVideoTaskTimeout(Duration.ofSeconds(1));
        RunningHubTaskRunner runner = new RunningHubTaskRunner(client, properties);
        when(client.submit(eq("video-app"), anyList(), eq("plus")))
                .thenReturn(new RunningHubClient.TaskResponse("video-task", "RUNNING", "", "", null, null, null));
        when(client.query("video-task"))
                .thenReturn(new RunningHubClient.TaskResponse(
                        "video-task",
                        "SUCCESS",
                        "",
                        "",
                        null,
                        null,
                        List.of(new RunningHubClient.TaskResult(
                                "https://example.com/result.mp4", "9", "mp4", null))));

        RunningHubTaskRunner.TaskOutput output = runner.runVideo("video-app", List.of(), ignored -> {});

        assertEquals("https://example.com/result.mp4", output.url());
        assertEquals("mp4", output.outputType());
        verify(client).submit(eq("video-app"), anyList(), eq("plus"));
    }
}
