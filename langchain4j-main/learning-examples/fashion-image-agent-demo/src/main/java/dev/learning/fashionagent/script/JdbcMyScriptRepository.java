package dev.learning.fashionagent.script;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMyScriptRepository implements MyScriptRepository {
    private final JdbcTemplate jdbc;
    JdbcMyScriptRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void saveProject(Project p) {
        jdbc.update("""
                INSERT INTO my_script_project (id, source_job_id, title, settings_text, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE title=VALUES(title), settings_text=VALUES(settings_text), updated_at=VALUES(updated_at)
                """, p.id().toString(), p.sourceJobId().toString(), p.title(), p.settings(), Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()));
    }
    @Override public void saveEpisode(Episode e) {
        jdbc.update("""
                INSERT INTO my_script_episode (id, project_id, episode_number, title, content, status, message, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), status=VALUES(status), message=VALUES(message), error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """, e.id().toString(), e.projectId().toString(), e.number(), e.title(), e.content(), e.status(), e.message(), e.error(), Timestamp.from(e.createdAt()), Timestamp.from(e.updatedAt()));
    }
    @Override public void saveSegment(Segment s) {
        jdbc.update("""
                INSERT INTO script_replication_segment (id, episode_id, segment_number, content, duration_seconds, status, comfy_task_id, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE content=VALUES(content), duration_seconds=VALUES(duration_seconds), status=VALUES(status), comfy_task_id=VALUES(comfy_task_id), error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """, s.id().toString(), s.episodeId().toString(), s.number(), s.content(), s.durationSeconds(), s.status(), s.comfyTaskId() == null ? null : s.comfyTaskId().toString(), s.error(), Timestamp.from(s.createdAt()), Timestamp.from(s.updatedAt()));
    }
    @Override public void deleteSegments(UUID episodeId) {
        jdbc.update("DELETE FROM script_replication_segment WHERE episode_id=?", episodeId.toString());
    }
    @Override public void saveCharacterAsset(CharacterAsset a) {
        jdbc.update("""
                INSERT INTO my_script_character_asset (id, project_id, character_name, role_level, anchor_text, image_sources_json, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE role_level=VALUES(role_level), anchor_text=VALUES(anchor_text), image_sources_json=VALUES(image_sources_json), sort_order=VALUES(sort_order), updated_at=VALUES(updated_at)
                """, a.id().toString(), a.projectId().toString(), a.characterName(), a.roleLevel(), a.anchor(), a.imageSourcesJson(), a.sortOrder(), Timestamp.from(a.createdAt()), Timestamp.from(a.updatedAt()));
    }
    @Override public List<Project> listProjects() { return safely(() -> jdbc.query("SELECT * FROM my_script_project ORDER BY updated_at DESC", (rs, n) -> new Project(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("source_job_id")), rs.getString("title"), rs.getString("settings_text"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())), List.of()); }
    @Override public Optional<Project> findProject(UUID id) { return safely(() -> jdbc.query("SELECT * FROM my_script_project WHERE id=?", (rs, n) -> new Project(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("source_job_id")), rs.getString("title"), rs.getString("settings_text"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id.toString()).stream().findFirst(), Optional.empty()); }
    @Override public Optional<Episode> findEpisode(UUID id) { return safely(() -> jdbc.query("SELECT * FROM my_script_episode WHERE id=?", (rs, n) -> episode(rs), id.toString()).stream().findFirst(), Optional.empty()); }
    @Override public Optional<Segment> findSegment(UUID id) { return safely(() -> jdbc.query("SELECT * FROM script_replication_segment WHERE id=?", (rs, n) -> segment(rs), id.toString()).stream().findFirst(), Optional.empty()); }
    @Override public List<Episode> listEpisodes(UUID projectId) { return safely(() -> jdbc.query("SELECT * FROM my_script_episode WHERE project_id=? ORDER BY episode_number", (rs, n) -> episode(rs), projectId.toString()), List.of()); }
    @Override public List<Segment> listSegments(UUID episodeId) { return safely(() -> jdbc.query("SELECT * FROM script_replication_segment WHERE episode_id=? ORDER BY segment_number", (rs, n) -> segment(rs), episodeId.toString()), List.of()); }
    @Override public List<CharacterAsset> listCharacterAssets(UUID projectId) { return safely(() -> jdbc.query("SELECT * FROM my_script_character_asset WHERE project_id=? ORDER BY sort_order", (rs, n) -> character(rs), projectId.toString()), List.of()); }
    private static Segment segment(java.sql.ResultSet rs) throws java.sql.SQLException { return new Segment(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("episode_id")), rs.getInt("segment_number"), rs.getString("content"), rs.getInt("duration_seconds"), rs.getString("status"), rs.getString("comfy_task_id") == null ? null : UUID.fromString(rs.getString("comfy_task_id")), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static CharacterAsset character(java.sql.ResultSet rs) throws java.sql.SQLException { return new CharacterAsset(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("project_id")), rs.getString("character_name"), rs.getString("role_level"), rs.getString("anchor_text"), rs.getString("image_sources_json"), rs.getInt("sort_order"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static Episode episode(java.sql.ResultSet rs) throws java.sql.SQLException { return new Episode(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("project_id")), rs.getInt("episode_number"), rs.getString("title"), rs.getString("content"), rs.getString("status"), rs.getString("message"), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static <T> T safely(Supplier<T> action, T fallback) { try { return action.get(); } catch (RuntimeException ignored) { return fallback; } }
}
