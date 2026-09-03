package dev.learning.fashionagent.video;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcQwenVideoScriptRepository implements QwenVideoScriptRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcQwenVideoScriptRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean warningLogged = new AtomicBoolean();

    JdbcQwenVideoScriptRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public void save(QwenVideoScriptSnapshot snapshot) {
        safelyWrite(() -> jdbcTemplate.update("""
                INSERT INTO qwen_video_script_job
                    (id, source_address, source_file_name, video_path, status, message, script_text,
                     error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    source_file_name=VALUES(source_file_name), video_path=VALUES(video_path),
                    status=VALUES(status), message=VALUES(message), script_text=VALUES(script_text),
                    error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """,
                snapshot.id().toString(), snapshot.address(), snapshot.sourceFileName(), path(snapshot.videoPath()),
                snapshot.status(), snapshot.message(), snapshot.script(), snapshot.error(),
                Timestamp.from(snapshot.createdAt()), Timestamp.from(snapshot.updatedAt())));
    }

    @Override
    public List<QwenVideoScriptSnapshot> list() {
        return safelyRead(() -> jdbcTemplate.query("""
                SELECT id, source_address, source_file_name, video_path, status, message, script_text,
                       error_message, created_at, updated_at
                FROM qwen_video_script_job ORDER BY created_at DESC LIMIT 200
                """, (rs, row) -> map(rs)), List.of());
    }

    @Override
    public Optional<QwenVideoScriptSnapshot> find(UUID id) {
        return safelyRead(() -> jdbcTemplate.query("""
                SELECT id, source_address, source_file_name, video_path, status, message, script_text,
                       error_message, created_at, updated_at
                FROM qwen_video_script_job WHERE id = ?
                """, (rs, row) -> map(rs), id.toString()).stream().findFirst(), Optional.empty());
    }

    private QwenVideoScriptSnapshot map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QwenVideoScriptSnapshot(UUID.fromString(rs.getString("id")),
                rs.getString("source_address"), rs.getString("source_file_name"), toPath(rs.getString("video_path")),
                rs.getString("status"), rs.getString("message"), rs.getString("script_text"),
                rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
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
            LOGGER.warn("视频脚本数据库不可用，请检查本地 H2 数据库：{}", e.getMessage());
        }
    }

    private static String path(Path value) { return value == null ? null : value.toAbsolutePath().normalize().toString(); }
    private static Path toPath(String value) { return value == null || value.isBlank() ? null : Path.of(value); }
}
