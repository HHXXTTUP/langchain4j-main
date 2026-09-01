package dev.learning.fashionagent.comfyui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Timestamp;
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
class JdbcComfyUiVideoRepository implements ComfyUiVideoRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcComfyUiVideoRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean warningLogged = new AtomicBoolean();

    JdbcComfyUiVideoRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ComfyUiVideoSnapshot snapshot) {
        safelyWrite(() -> {
            ComfyUiVideoView view = snapshot.view();
            jdbcTemplate.update("""
                    INSERT INTO comfyui_video_generation_job
                        (id, account_id, prompt, duration_seconds, resolution, image_count, status, message,
                         remote_task_id, remote_result_url, final_video_path, error_message,
                         snapshot_json, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        account_id=VALUES(account_id), status=VALUES(status), message=VALUES(message), remote_task_id=VALUES(remote_task_id),
                        remote_result_url=VALUES(remote_result_url), final_video_path=VALUES(final_video_path),
                        error_message=VALUES(error_message), snapshot_json=VALUES(snapshot_json), updated_at=VALUES(updated_at)
                    """,
                    view.id().toString(), snapshot.accountId(), view.prompt(), view.duration(), view.resolution(), view.imageCount(),
                    view.status().name(), view.message(), view.remoteTaskId(), view.remoteResultUrl(),
                    path(snapshot.finalVideo()), view.error(), json(view), Timestamp.from(view.createdAt()),
                    Timestamp.from(view.updatedAt()));
        });
    }

    @Override
    public List<ComfyUiVideoView> list(String accountId) {
        return safelyRead(() -> accountId == null
                ? jdbcTemplate.query("SELECT snapshot_json FROM comfyui_video_generation_job ORDER BY created_at DESC LIMIT 200", (rs, rowNum) -> fromJson(rs.getString(1)))
                : jdbcTemplate.query("SELECT snapshot_json FROM comfyui_video_generation_job WHERE account_id=? ORDER BY created_at DESC LIMIT 200", (rs, rowNum) -> fromJson(rs.getString(1)), accountId), List.of());
    }

    @Override
    public Optional<ComfyUiVideoView> find(UUID id, String accountId) {
        return safelyRead(() -> accountId == null
                ? jdbcTemplate.query("SELECT snapshot_json FROM comfyui_video_generation_job WHERE id = ?", (rs, rowNum) -> fromJson(rs.getString(1)), id.toString()).stream().findFirst()
                : jdbcTemplate.query("SELECT snapshot_json FROM comfyui_video_generation_job WHERE id = ? AND account_id=?", (rs, rowNum) -> fromJson(rs.getString(1)), id.toString(), accountId).stream().findFirst(), Optional.empty());
    }

    @Override
    public Optional<Path> finalVideo(UUID id, String accountId) {
        return safelyRead(() -> (accountId == null
                ? jdbcTemplate.query("SELECT final_video_path FROM comfyui_video_generation_job WHERE id = ?", (rs, rowNum) -> rs.getString(1), id.toString())
                : jdbcTemplate.query("SELECT final_video_path FROM comfyui_video_generation_job WHERE id = ? AND account_id=?", (rs, rowNum) -> rs.getString(1), id.toString(), accountId)).stream()
                .filter(value -> value != null && !value.isBlank()).map(Path::of).findFirst(), Optional.empty());
    }

    @Override
    public void delete(UUID id, String accountId) {
        safelyWrite(() -> { if (accountId == null) jdbcTemplate.update("DELETE FROM comfyui_video_generation_job WHERE id = ?", id.toString()); else jdbcTemplate.update("DELETE FROM comfyui_video_generation_job WHERE id = ? AND account_id=?", id.toString(), accountId); });
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("serialize comfyui video task failed", e); }
    }

    private ComfyUiVideoView fromJson(String value) {
        try { return objectMapper.readValue(value, ComfyUiVideoView.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("parse comfyui video task failed", e); }
    }

    private void safelyWrite(Runnable action) {
        try { action.run(); warningLogged.set(false); }
        catch (RuntimeException e) { logUnavailable(e); }
    }

    private <T> T safelyRead(Supplier<T> action, T fallback) {
        try { T value = action.get(); warningLogged.set(false); return value; }
        catch (RuntimeException e) { logUnavailable(e); return fallback; }
    }

    private void logUnavailable(RuntimeException e) {
        if (warningLogged.compareAndSet(false, true)) {
            LOGGER.warn("ComfyUI video history unavailable; check local H2 database: {}", e.getMessage());
        }
    }

    private static String path(Path value) { return value == null ? null : value.toAbsolutePath().normalize().toString(); }
}
