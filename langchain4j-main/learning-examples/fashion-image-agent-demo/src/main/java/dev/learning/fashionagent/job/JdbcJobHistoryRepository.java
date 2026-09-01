package dev.learning.fashionagent.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.pipeline.OutfitAttempt;
import dev.learning.fashionagent.pipeline.PipelineStage;
import dev.learning.fashionagent.pipeline.PortraitAttempt;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcJobHistoryRepository implements JobHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcJobHistoryRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean databaseWarningLogged = new AtomicBoolean();

    JdbcJobHistoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean available() {
        return safelyRead("检查本地历史表", () -> {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM generation_job", Integer.class);
            return count != null;
        }, false);
    }

    @Override
    public int recoverInterruptedJobs() {
        List<InterruptedJob> interruptedJobs = safelyRead("查询异常中断任务", () -> jdbcTemplate.query("""
                        SELECT id, snapshot_json, original_image_path, clothing_image_path, final_image_path
                        FROM generation_job
                        WHERE status IN ('QUEUED', 'RUNNING')
                        ORDER BY created_at
                        """,
                (resultSet, rowNumber) -> new InterruptedJob(
                        UUID.fromString(resultSet.getString("id")),
                        fromJson(resultSet.getString("snapshot_json"), JobView.class),
                        toPath(resultSet.getString("original_image_path")),
                        toPath(resultSet.getString("clothing_image_path")),
                        toPath(resultSet.getString("final_image_path")))), List.of());

        Instant recoveredAt = Instant.now();
        for (InterruptedJob interruptedJob : interruptedJobs) {
            JobView recoveredView = interruptedView(interruptedJob.view(), recoveredAt);
            saveJob(new JobPersistenceSnapshot(
                    recoveredView,
                    interruptedJob.originalImage(),
                    interruptedJob.clothingImage(),
                    interruptedJob.finalImage()));
            appendEvent(interruptedJob.id(), new JobStepView(
                    null,
                    "JOB_INTERRUPTED",
                    PipelineStage.FAILED,
                    "应用曾在任务执行期间停止，本次启动已结束该遗留任务",
                    null,
                    recoveredAt));
        }
        return interruptedJobs.size();
    }

    @Override
    public void saveJob(JobPersistenceSnapshot snapshot) {
        safelyWrite("保存任务快照", () -> {
            JobView view = snapshot.view();
            jdbcTemplate.update("""
                    INSERT INTO generation_job (
                        id, prompt, status, current_stage, message,
                        original_image_path, clothing_image_path, final_image_path,
                        reply, error_message, error_details, snapshot_json, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        prompt = VALUES(prompt), status = VALUES(status),
                        current_stage = VALUES(current_stage), message = VALUES(message),
                        original_image_path = VALUES(original_image_path),
                        clothing_image_path = VALUES(clothing_image_path),
                        final_image_path = VALUES(final_image_path), reply = VALUES(reply),
                        error_message = VALUES(error_message), error_details = VALUES(error_details),
                        snapshot_json = VALUES(snapshot_json), updated_at = VALUES(updated_at)
                    """,
                    view.id().toString(),
                    view.prompt(),
                    view.status().name(),
                    view.stage().name(),
                    view.message(),
                    path(snapshot.originalImage()),
                    path(snapshot.clothingImage()),
                    path(snapshot.finalImage()),
                    view.reply(),
                    view.error(),
                    view.errorDetails(),
                    json(view),
                    timestamp(view.createdAt()),
                    timestamp(view.updatedAt()));
        });
    }

    @Override
    public void appendEvent(UUID jobId, JobStepView event) {
        safelyWrite("保存任务步骤", () -> jdbcTemplate.update("""
                INSERT INTO generation_step_event (
                    job_id, event_type, stage, message, result_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                jobId.toString(),
                event.eventType(),
                event.stage().name(),
                event.message(),
                blankToNull(event.resultJson()),
                timestamp(event.createdAt())));
    }

    @Override
    public void savePortraitAttempt(UUID jobId, PortraitAttempt attempt) {
        safelyWrite("保存人物候选明细", () -> jdbcTemplate.update("""
                INSERT INTO portrait_attempt (
                    job_id, attempt_number, image_path, generation_prompt,
                    quality_report_json, selected, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE
                    image_path = VALUES(image_path), generation_prompt = VALUES(generation_prompt),
                    quality_report_json = VALUES(quality_report_json), selected = VALUES(selected),
                    updated_at = CURRENT_TIMESTAMP(6)
                """,
                jobId.toString(),
                attempt.attemptNumber(),
                path(attempt.image()),
                attempt.prompt(),
                json(attempt.qualityReport()),
                attempt.selected()));
    }

    @Override
    public void saveOutfitAttempt(UUID jobId, OutfitAttempt attempt) {
        safelyWrite("保存换装候选明细", () -> jdbcTemplate.update("""
                INSERT INTO outfit_attempt (
                    job_id, attempt_number, image_path, replacement_prompt,
                    quality_report_json, selected, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE
                    image_path = VALUES(image_path), replacement_prompt = VALUES(replacement_prompt),
                    quality_report_json = VALUES(quality_report_json), selected = VALUES(selected),
                    updated_at = CURRENT_TIMESTAMP(6)
                """,
                jobId.toString(),
                attempt.attemptNumber(),
                path(attempt.image()),
                attempt.prompt(),
                json(attempt.qualityReport()),
                attempt.selected()));
    }

    @Override
    public Optional<JobView> findJob(UUID jobId) {
        return safelyRead("查询任务历史", () -> jdbcTemplate.query("""
                        SELECT snapshot_json FROM generation_job WHERE id = ?
                        """,
                (resultSet, rowNumber) -> fromJson(resultSet.getString("snapshot_json"), JobView.class),
                jobId.toString()).stream().findFirst(), Optional.empty());
    }

    @Override
    public List<JobView> listJobs() {
        return safelyRead("查询任务历史列表", () -> jdbcTemplate.query("""
                        SELECT snapshot_json FROM generation_job ORDER BY created_at DESC LIMIT 100
                        """,
                (resultSet, rowNumber) -> fromJson(resultSet.getString("snapshot_json"), JobView.class)), List.of());
    }

    @Override
    public List<JobStepView> listEvents(UUID jobId) {
        return safelyRead("查询任务步骤", () -> jdbcTemplate.query("""
                        SELECT id, event_type, stage, message, result_json, created_at
                        FROM generation_step_event
                        WHERE job_id = ?
                        ORDER BY id
                        """,
                (resultSet, rowNumber) -> new JobStepView(
                        resultSet.getLong("id"),
                        resultSet.getString("event_type"),
                        PipelineStage.valueOf(resultSet.getString("stage")),
                        resultSet.getString("message"),
                        resultSet.getString("result_json"),
                        resultSet.getTimestamp("created_at").toInstant()),
                jobId.toString()), List.of());
    }

    @Override
    public void delete(UUID jobId) {
        jdbcTemplate.update("DELETE FROM generation_job WHERE id = ?", jobId.toString());
    }

    @Override
    public Optional<Path> findOriginalImage(UUID jobId) {
        return findPath("SELECT original_image_path FROM generation_job WHERE id = ?", jobId);
    }

    @Override
    public Optional<Path> findClothingImage(UUID jobId) {
        return findPath("SELECT clothing_image_path FROM generation_job WHERE id = ?", jobId);
    }

    @Override
    public Optional<Path> findFinalImage(UUID jobId) {
        return findPath("SELECT final_image_path FROM generation_job WHERE id = ?", jobId);
    }

    @Override
    public Optional<Path> findPortraitAttemptImage(UUID jobId, int attemptNumber) {
        return findAttemptPath("portrait_attempt", jobId, attemptNumber);
    }

    @Override
    public Optional<Path> findOutfitAttemptImage(UUID jobId, int attemptNumber) {
        return findAttemptPath("outfit_attempt", jobId, attemptNumber);
    }

    private Optional<Path> findAttemptPath(String table, UUID jobId, int attemptNumber) {
        String sql = "SELECT image_path FROM " + table + " WHERE job_id = ? AND attempt_number = ?";
        return safelyRead("查询候选图片", () -> jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> toPath(resultSet.getString("image_path")),
                jobId.toString(),
                attemptNumber).stream().filter(path -> path != null).findFirst(), Optional.empty());
    }

    private Optional<Path> findPath(String sql, UUID jobId) {
        return safelyRead("查询任务图片", () -> jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> toPath(resultSet.getString(1)),
                jobId.toString()).stream().filter(path -> path != null).findFirst(), Optional.empty());
    }

    private void safelyWrite(String operation, Runnable action) {
        try {
            action.run();
            databaseWarningLogged.set(false);
        } catch (RuntimeException exception) {
            logDatabaseUnavailable(operation, exception);
        }
    }

    private <T> T safelyRead(String operation, Supplier<T> action, T fallback) {
        try {
            T result = action.get();
            databaseWarningLogged.set(false);
            return result;
        } catch (RuntimeException exception) {
            logDatabaseUnavailable(operation, exception);
            return fallback;
        }
    }

    private void logDatabaseUnavailable(String operation, RuntimeException exception) {
        if (databaseWarningLogged.compareAndSet(false, true)) {
            LOGGER.warn("{}失败，任务继续使用内存模式。请检查本地 H2 数据库：{}",
                    operation,
                    exception.getMessage());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务结果 JSON 序列化失败", exception);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务历史 JSON 解析失败", exception);
        }
    }

    private static String path(Path value) {
        return value == null ? null : value.toAbsolutePath().normalize().toString();
    }

    private static Path toPath(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static JobView interruptedView(JobView source, Instant recoveredAt) {
        String error = "应用重启导致任务中断";
        String details = "该任务在上次应用停止时仍处于排队或运行状态，但原执行线程已不存在，无法自动续跑。"
                + "请在历史记录中点击重新启动，系统会创建一个新任务并从第一步执行。";
        return new JobView(
                source.id(),
                JobStatus.FAILED,
                PipelineStage.FAILED,
                "上次执行因应用停止而中断，可重新启动任务",
                source.prompt(),
                source.portraitGenerationMode(),
                source.originalImageUrl(),
                source.clothingPreviewUrl(),
                source.clothingFileName(),
                source.clothingMatchName(),
                source.clothingMatchPercentage(),
                source.clothingMatchRule(),
                source.finalImageUrl(),
                source.portraitPrompt(),
                source.portraitAttempts(),
                source.finalPortraitQualityReport(),
                source.fashionAnalysis(),
                source.attempts(),
                source.finalQualityReport(),
                source.reply(),
                error,
                details,
                source.createdAt(),
                recoveredAt);
    }

    private record InterruptedJob(
            UUID id,
            JobView view,
            Path originalImage,
            Path clothingImage,
            Path finalImage) {}
}
