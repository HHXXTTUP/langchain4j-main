package dev.learning.fashionagent.director;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcShortDramaDirectorRepository implements ShortDramaDirectorRepository {
    private final JdbcTemplate jdbc;
    JdbcShortDramaDirectorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void save(ShortDramaDirectorSnapshot s) {
        jdbc.update("""
                INSERT INTO short_drama_director_job (id, mode, source_type, source_file_name, source_text,
                  action_tier, platform, aspect_ratio, status, message, result_text, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE mode=VALUES(mode), source_type=VALUES(source_type), source_file_name=VALUES(source_file_name),
                  source_text=VALUES(source_text), action_tier=VALUES(action_tier), platform=VALUES(platform), aspect_ratio=VALUES(aspect_ratio),
                  status=VALUES(status), message=VALUES(message), result_text=VALUES(result_text), error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """, s.id().toString(), s.mode(), s.sourceType(), s.sourceFileName(), s.sourceText(),
                s.actionTier(), s.platform(), s.aspectRatio(), s.status(), s.message(), s.result(), s.error(),
                Timestamp.from(s.createdAt()), Timestamp.from(s.updatedAt()));
    }
    @Override public List<ShortDramaDirectorSnapshot> list() {
        return safely(() -> jdbc.query("SELECT * FROM short_drama_director_job ORDER BY created_at DESC LIMIT 200", (rs, row) -> map(rs)), List.of());
    }
    @Override public Optional<ShortDramaDirectorSnapshot> find(UUID id) {
        return safely(() -> jdbc.query("SELECT * FROM short_drama_director_job WHERE id=?", (rs, row) -> map(rs), id.toString()).stream().findFirst(), Optional.empty());
    }
    private static ShortDramaDirectorSnapshot map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShortDramaDirectorSnapshot(UUID.fromString(rs.getString("id")), rs.getString("mode"), rs.getString("source_type"),
                rs.getString("source_file_name"), rs.getString("source_text"), rs.getString("action_tier"), rs.getString("platform"),
                rs.getString("aspect_ratio"), rs.getString("status"), rs.getString("message"), rs.getString("result_text"),
                rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    private static <T> T safely(Supplier<T> action, T fallback) { try { return action.get(); } catch (RuntimeException ignored) { return fallback; } }
}
