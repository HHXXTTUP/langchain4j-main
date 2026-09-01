package dev.learning.fashionagent.integration.runninghub;

import dev.learning.fashionagent.config.RunningHubProperties;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class RunningHubTaskRunner {

    private final RunningHubClient client;
    private final RunningHubProperties properties;

    public RunningHubTaskRunner(RunningHubClient client, RunningHubProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public TaskOutput run(String appId, List<NodeInput> inputs, Consumer<String> progress) {
        return runMedia(
                appId,
                inputs,
                properties.getPollInterval(),
                properties.getTaskTimeout(),
                true,
                progress);
    }

    public TaskOutput runVideo(String appId, List<NodeInput> inputs, Consumer<String> progress) {
        return runMedia(
                appId,
                inputs,
                properties.getVideoPollInterval(),
                properties.getVideoTaskTimeout(),
                false,
                progress);
    }

    private TaskOutput runMedia(
            String appId,
            List<NodeInput> inputs,
            java.time.Duration pollInterval,
            java.time.Duration timeout,
            boolean imageOnly,
            Consumer<String> progress) {
        RunningHubClient.TaskResponse submitted = requireResponse(
                imageOnly
                        ? client.submit(appId, inputs)
                        : client.submit(appId, inputs, properties.getVideoInstanceType()),
                "提交任务");
        if (submitted.taskId() == null || submitted.taskId().isBlank()) {
            throw new RunningHubException("RunningHub 未返回 taskId：" + errorOf(submitted));
        }

        Instant deadline = Instant.now().plus(timeout);
        RunningHubClient.TaskResponse current = submitted;
        while (true) {
            String status = normalizedStatus(current.status());
            progress.accept(status);
            if ("SUCCESS".equals(status)) {
                return firstMedia(current, imageOnly);
            }
            String error = errorOf(current);
            if (isContentAuditFailure(current)) {
                throw new RunningHubContentAuditException(
                        "RunningHub 内容审核未通过：" + error,
                        current.errorCode());
            }
            if ("FAILED".equals(status)) {
                throw new RunningHubException("RunningHub 任务失败：" + error);
            }
            if (!"QUEUED".equals(status) && !"RUNNING".equals(status)) {
                throw new RunningHubException("RunningHub 返回未知任务状态：" + current.status());
            }
            if (Instant.now().isAfter(deadline)) {
                throw new RunningHubException("RunningHub 任务等待超时：" + submitted.taskId());
            }

            sleep(pollInterval);
            current = requireResponse(client.query(submitted.taskId()), "查询任务");
        }
    }

    private static RunningHubClient.TaskResponse requireResponse(
            RunningHubClient.TaskResponse response, String operation) {
        if (response == null) {
            throw new RunningHubException(operation + "失败：响应为空");
        }
        return response;
    }

    private static TaskOutput firstMedia(RunningHubClient.TaskResponse response, boolean imageOnly) {
        if (response.results() == null) {
            throw new RunningHubException("RunningHub 任务成功但没有返回结果");
        }
        return response.results().stream()
                .filter(result -> result.url() != null && !result.url().isBlank())
                .filter(result -> result.outputType() == null
                        || (imageOnly ? isImage(result.outputType()) : isVideo(result.outputType())))
                .map(result -> new TaskOutput(result.url(), result.outputType()))
                .findFirst()
                .orElseThrow(() -> new RunningHubException(
                        "RunningHub 任务成功但没有返回" + (imageOnly ? "图片" : "视频") + " URL"));
    }

    private static boolean isImage(String outputType) {
        return switch (outputType.toLowerCase(Locale.ROOT)) {
            case "png", "jpg", "jpeg", "webp" -> true;
            default -> false;
        };
    }

    private static boolean isVideo(String outputType) {
        return switch (outputType.toLowerCase(Locale.ROOT)) {
            case "mp4", "mov", "webm", "mkv" -> true;
            default -> false;
        };
    }

    private static String normalizedStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isContentAuditFailure(RunningHubClient.TaskResponse response) {
        String details = (normalized(response.errorMessage()) + " "
                + normalized(response.failedReason() == null ? null : response.failedReason().toString()) + " "
                + normalized(response.promptTips())).toLowerCase(Locale.ROOT);
        return details.contains("rhauditexception")
                || details.contains("exception_message=porn")
                || details.contains("平台禁止以下内容");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static String errorOf(RunningHubClient.TaskResponse response) {
        StringJoiner details = new StringJoiner("；");
        if (response.errorMessage() != null && !response.errorMessage().isBlank()) {
            details.add(response.errorMessage());
        }
        if (response.errorCode() != null && !response.errorCode().isBlank()) {
            details.add("errorCode=" + response.errorCode());
        }
        if (response.failedReason() != null && !"{}".equals(response.failedReason().toString())) {
            details.add("failedReason=" + response.failedReason());
        }
        if (response.promptTips() != null && !response.promptTips().isBlank()) {
            details.add("promptTips=" + response.promptTips());
        }
        return details.length() == 0 ? "未提供错误信息" : details.toString();
    }

    private void sleep(java.time.Duration interval) {
        try {
            Thread.sleep(Math.max(0, interval.toMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RunningHubException("等待 RunningHub 任务时被中断", exception);
        }
    }

    public record TaskOutput(String url, String outputType) {}
}
