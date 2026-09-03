package dev.learning.fashionagent.video;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class JdbcVideoGenerationRepository implements VideoGenerationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcVideoGenerationRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean warningLogged = new AtomicBoolean();

    JdbcVideoGenerationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(VideoGenerationSnapshot snapshot) {
        safelyWrite("保存视频任务", () -> {
            VideoGenerationView view = snapshot.view();
            jdbcTemplate.update("""
                    INSERT INTO video_generation_job (
                        id, source_job_id, status, message, source_video_path, final_video_path,
                        quality_score, quality_report_json, error_message, snapshot_json, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        status = VALUES(status), message = VALUES(message),
                        source_video_path = VALUES(source_video_path), final_video_path = VALUES(final_video_path),
                        quality_score = VALUES(quality_score), quality_report_json = VALUES(quality_report_json),
                        error_message = VALUES(error_message), snapshot_json = VALUES(snapshot_json),
                        updated_at = VALUES(updated_at)
                    """,
                    view.id().toString(),
                    view.sourceJobId().toString(),
                    view.status().name(),
                    view.message(),
                    path(snapshot.sourceVideo()),
                    path(snapshot.finalVideo()),
                    view.qualityReport() == null ? null : view.qualityReport().overallScore(),
                    view.qualityReport() == null ? null : json(view.qualityReport()),
                    view.error(),
                    json(view),
                    Timestamp.from(view.createdAt()),
                    Timestamp.from(view.updatedAt()));
        });
    }

    @Override
    public Optional<VideoGenerationView> find(UUID id) {
        return safelyRead("查询视频任务", () -> jdbcTemplate.query(
                "SELECT snapshot_json FROM video_generation_job WHERE id = ?",
                (rs, rowNum) -> fromJson(rs.getString(1)),
                id.toString()).stream().findFirst(), Optional.empty());
    }

    @Override
    public List<VideoGenerationView> list() {
        return safelyRead("查询视频任务列表", () -> jdbcTemplate.query(
                "SELECT snapshot_json FROM video_generation_job ORDER BY created_at DESC LIMIT 100",
                (rs, rowNum) -> fromJson(rs.getString(1))), List.of());
    }

    @Override
    public List<VideoGenerationView> findBySourceJobId(UUID sourceJobId) {
        return safelyRead("查询图片任务关联的视频任务", () -> jdbcTemplate.query(
                "SELECT snapshot_json FROM video_generation_job WHERE source_job_id = ? ORDER BY created_at",
                (rs, rowNum) -> fromJson(rs.getString(1)),
                sourceJobId.toString()), List.of());
    }

    @Override
    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM video_generation_job WHERE id = ?", id.toString());
    }

    @Override
    public Optional<Path> finalVideo(UUID id) {
        return safelyRead("查询最终视频", () -> jdbcTemplate.query(
                "SELECT final_video_path FROM video_generation_job WHERE id = ?",
                (rs, rowNum) -> rs.getString(1),
                id.toString()).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(Path::of)
                .findFirst(), Optional.empty());
    }

    @Override
    public int recoverInterrupted() {
        List<VideoGenerationView> interrupted = safelyRead("恢复中断视频任务", () -> jdbcTemplate.query("""
                        SELECT snapshot_json FROM video_generation_job
                        WHERE status NOT IN ('SUCCESS', 'FAILED')
                        """, (rs, rowNum) -> fromJson(rs.getString(1))), List.of());
        for (VideoGenerationView source : interrupted) {
            boolean downloadRetryable = source.firstSegmentRemoteUrl() != null
                    && !source.firstSegmentRemoteUrl().isBlank()
                    && source.secondSegmentRemoteUrl() != null
                    && !source.secondSegmentRemoteUrl().isBlank();
            VideoGenerationView failed = new VideoGenerationView(
                    source.id(),
                    source.sourceJobId(),
                    VideoGenerationStatus.FAILED,
                    downloadRetryable
                            ? "应用重启中断下载，可点击重新下载"
                            : "应用重启导致视频任务中断，请重新点击生成视频",
                    source.sourceVideoFileName(),
                    source.firstSegmentStatus(),
                    source.secondSegmentStatus(),
                    null,
                    null,
                    null,
                    downloadRetryable
                            ? "应用重启导致下载线程丢失，已保留 RunningHub 视频地址"
                            : "应用重启导致本地执行线程丢失；RunningHub 远程任务可能仍在执行",
                    source.createdAt(),
                    Instant.now(),
                    source.sourceVideoPath(),
                    source.firstSegmentRemoteUrl(),
                    source.secondSegmentRemoteUrl(),
                    downloadRetryable);
            save(new VideoGenerationSnapshot(failed, null, null));
        }
        return interrupted.size();
    }

    private void safelyWrite(String operation, Runnable action) {
        try {
            action.run();
            warningLogged.set(false);
        } catch (RuntimeException exception) {
            logUnavailable(operation, exception);
        }
    }

    private <T> T safelyRead(String operation, Supplier<T> action, T fallback) {
        try {
            T result = action.get();
            warningLogged.set(false);
            return result;
        } catch (RuntimeException exception) {
            logUnavailable(operation, exception);
            return fallback;
        }
    }

    private void logUnavailable(String operation, RuntimeException exception) {
        if (warningLogged.compareAndSet(false, true)) {
            LOGGER.warn("{}失败，视频任务继续使用内存模式。请检查本地 H2 数据库：{}",
                    operation, exception.getMessage());
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("视频任务 JSON 序列化失败", exception);
        }
    }

    private VideoGenerationView fromJson(String value) {
        try {
            return objectMapper.readValue(value, VideoGenerationView.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("视频任务 JSON 解析失败", exception);
        }
    }

    private static String path(Path value) {
        return value == null ? null : value.toAbsolutePath().normalize().toString();
    }
}
