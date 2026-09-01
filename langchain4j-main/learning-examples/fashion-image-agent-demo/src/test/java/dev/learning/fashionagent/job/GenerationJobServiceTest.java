package dev.learning.fashionagent.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.learning.fashionagent.integration.runninghub.RunningHubException;
import dev.learning.fashionagent.pipeline.FashionAgentPipeline;
import dev.learning.fashionagent.pipeline.PortraitGenerationMode;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GenerationJobServiceTest {

    @Test
    void shouldQueueEveryValidatedBatchPromptWithTheSamePortraitMode() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        List<Runnable> queuedTasks = new ArrayList<>();
        GenerationJobService service = new GenerationJobService(pipeline, queuedTasks::add);
        List<String> prompts = List.of("卧室美女", "沙滩美女", "办公室美女", "学校美女");

        List<UUID> ids = service.createBatch(prompts, PortraitGenerationMode.ENHANCED);

        assertEquals(4, ids.size());
        assertEquals(4, queuedTasks.size());
        assertEquals(4, ids.stream().distinct().count());
        assertEquals(
                prompts.stream().sorted().toList(),
                ids.stream().map(service::get).map(JobView::prompt).sorted().toList());
        assertTrue(ids.stream()
                .map(service::get)
                .allMatch(job -> job.portraitGenerationMode() == PortraitGenerationMode.ENHANCED));
        verifyNoInteractions(pipeline);
    }

    @Test
    void shouldValidateTheWholeBatchBeforeQueuingAnyTask() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        List<Runnable> queuedTasks = new ArrayList<>();
        GenerationJobService service = new GenerationJobService(pipeline, queuedTasks::add);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createBatch(
                        List.of("有效描述", "过长".repeat(1001), "不会创建"),
                        PortraitGenerationMode.STANDARD));

        assertEquals("描述词不能超过 2000 个字符", exception.getMessage());
        assertTrue(queuedTasks.isEmpty());
        assertTrue(service.list().isEmpty());
        verifyNoInteractions(pipeline);
    }

    @Test
    void shouldRecoverInterruptedDatabaseJobsWhenApplicationStarts() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        JobHistoryRepository historyRepository = mock(JobHistoryRepository.class);
        when(historyRepository.recoverInterruptedJobs()).thenReturn(1);
        GenerationJobService service = new GenerationJobService(
                pipeline,
                Runnable::run,
                historyRepository,
                new ObjectMapper().findAndRegisterModules());

        service.recoverInterruptedJobs();

        verify(historyRepository).recoverInterruptedJobs();
        verifyNoInteractions(pipeline);
    }

    @Test
    void shouldExposeCopyableDiagnosticLogWhenPipelineFails() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        when(pipeline.run(any(UUID.class), eq("测试描述"), any()))
                .thenThrow(new RunningHubException("RunningHub API 请求失败（HTTP 400）：节点参数错误"));
        GenerationJobService service = new GenerationJobService(pipeline, Runnable::run);

        UUID jobId = service.create("测试描述");
        JobView job = service.get(jobId);

        assertEquals(JobStatus.FAILED, job.status());
        assertEquals("RunningHub API 请求失败（HTTP 400）：节点参数错误", job.error());
        assertNotNull(job.errorDetails());
        assertTrue(job.errorDetails().contains("任务 ID: " + jobId));
        assertTrue(job.errorDetails().contains("失败阶段: ACCEPTED"));
        assertTrue(job.errorDetails().contains("RunningHubException"));
    }

    @Test
    void shouldMarkJobFailedWhenPipelineThreadThrowsAnError() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        when(pipeline.run(any(UUID.class), eq("error-test"), any()))
                .thenThrow(new StackOverflowError("multipart encoder overflow"));
        GenerationJobService service = new GenerationJobService(pipeline, Runnable::run);

        UUID jobId = service.create("error-test");
        JobView job = service.get(jobId);

        assertEquals(JobStatus.FAILED, job.status());
        assertEquals("multipart encoder overflow", job.error());
        assertTrue(job.errorDetails().contains("StackOverflowError"));
    }

    @Test
    void shouldCancelQueuedJobBeforePipelineStarts() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        AtomicReference<Runnable> queuedTask = new AtomicReference<>();
        GenerationJobService service = new GenerationJobService(pipeline, queuedTask::set);

        UUID jobId = service.create("等待执行的人物任务");
        JobView cancelled = service.cancel(jobId);
        queuedTask.get().run();

        assertEquals(JobStatus.CANCELLED, cancelled.status());
        assertEquals("任务已手动停止", cancelled.message());
        assertEquals(JobStatus.CANCELLED, service.get(jobId).status());
        assertTrue(service.events(jobId).stream()
                .anyMatch(event -> "JOB_CANCELLED".equals(event.eventType())));
        verifyNoInteractions(pipeline);
    }

    @Test
    void shouldRestartCancelledJobAsNewExecution() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        List<Runnable> queuedTasks = new ArrayList<>();
        GenerationJobService service = new GenerationJobService(pipeline, queuedTasks::add);

        UUID originalId = service.create("重新执行这个人物任务");
        service.cancel(originalId);
        UUID restartedId = service.restart(originalId);

        assertTrue(!originalId.equals(restartedId));
        assertEquals(JobStatus.CANCELLED, service.get(originalId).status());
        assertEquals(JobStatus.QUEUED, service.get(restartedId).status());
        assertEquals("重新执行这个人物任务", service.get(restartedId).prompt());
        assertTrue(service.events(originalId).stream()
                .anyMatch(event -> "JOB_RESTARTED".equals(event.eventType())));
        assertEquals(2, queuedTasks.size());
        verifyNoInteractions(pipeline);
    }

    @Test
    void shouldPreserveEnhancedPortraitModeWhenRestarting() {
        FashionAgentPipeline pipeline = mock(FashionAgentPipeline.class);
        List<Runnable> queuedTasks = new ArrayList<>();
        GenerationJobService service = new GenerationJobService(pipeline, queuedTasks::add);

        UUID originalId = service.create("增强版人物任务", PortraitGenerationMode.ENHANCED);
        service.cancel(originalId);
        UUID restartedId = service.restart(originalId);

        assertEquals(PortraitGenerationMode.ENHANCED, service.get(originalId).portraitGenerationMode());
        assertEquals(PortraitGenerationMode.ENHANCED, service.get(restartedId).portraitGenerationMode());
        assertEquals(2, queuedTasks.size());
        verifyNoInteractions(pipeline);
    }
}
