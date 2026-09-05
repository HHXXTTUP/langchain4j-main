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
                INSERT INTO my_script_episode (id, project_id, episode_number, title, summary_text, content, status, message, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE title=VALUES(title), summary_text=VALUES(summary_text), content=VALUES(content), status=VALUES(status), message=VALUES(message), error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """, e.id().toString(), e.projectId().toString(), e.number(), e.title(), e.summary(), e.content(), e.status(), e.message(), e.error(), Timestamp.from(e.createdAt()), Timestamp.from(e.updatedAt()));
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
    @Override public void saveReplicationMaterial(UUID episodeId, String materialJson) {
        jdbc.update("""
                INSERT INTO script_replication_episode_material (episode_id, material_json, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE material_json=VALUES(material_json), updated_at=VALUES(updated_at)
                """, episodeId.toString(), materialJson);
    }
    @Override public void deleteReplicationMaterial(UUID episodeId) {
        jdbc.update("DELETE FROM script_replication_episode_material WHERE episode_id=?", episodeId.toString());
    }
    @Override public Optional<String> findReplicationMaterial(UUID episodeId) {
        return safely(() -> jdbc.query("SELECT material_json FROM script_replication_episode_material WHERE episode_id=?",
                (rs, n) -> rs.getString("material_json"), episodeId.toString()).stream().findFirst(), Optional.empty());
    }
    @Override public void saveReplicationVersion(ReplicationVersion v) {
        jdbc.update("""
                INSERT INTO script_replication_version (id, episode_id, version_number, status, material_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status=VALUES(status), material_json=VALUES(material_json), updated_at=VALUES(updated_at)
                """, v.id().toString(), v.episodeId().toString(), v.version(), v.status(), v.materialJson(),
                Timestamp.from(v.createdAt()), Timestamp.from(v.updatedAt()));
    }
    @Override public void saveReplicationVersionSegment(ReplicationVersionSegment s) {
        jdbc.update("""
                INSERT INTO script_replication_version_segment (id, version_id, segment_number, content, duration_seconds, status, comfy_task_id, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE content=VALUES(content), duration_seconds=VALUES(duration_seconds), status=VALUES(status), comfy_task_id=VALUES(comfy_task_id), error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """, s.id().toString(), s.versionId().toString(), s.number(), s.content(), s.durationSeconds(), s.status(),
                s.comfyTaskId() == null ? null : s.comfyTaskId().toString(), s.error(), Timestamp.from(s.createdAt()), Timestamp.from(s.updatedAt()));
    }
    @Override public void saveReplicationVersionAsset(ReplicationVersionAsset a) {
        jdbc.update("""
                INSERT INTO script_replication_version_asset (id, version_id, asset_type, asset_name, prompt_text, image_sources_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE prompt_text=VALUES(prompt_text), image_sources_json=VALUES(image_sources_json), updated_at=VALUES(updated_at)
                """, a.id().toString(), a.versionId().toString(), a.assetType(), a.assetName(), a.prompt(), a.imageSourcesJson(),
                Timestamp.from(a.createdAt()), Timestamp.from(a.updatedAt()));
    }
    @Override public Optional<ReplicationVersion> findReplicationVersion(UUID id) {
        return safely(() -> jdbc.query("SELECT * FROM script_replication_version WHERE id=?", (rs, n) -> replicationVersion(rs), id.toString()).stream().findFirst(), Optional.empty());
    }
    @Override public List<ReplicationVersion> listReplicationVersions(UUID episodeId) {
        return safely(() -> jdbc.query("SELECT * FROM script_replication_version WHERE episode_id=? ORDER BY version_number DESC", (rs, n) -> replicationVersion(rs), episodeId.toString()), List.of());
    }
    @Override public List<ReplicationVersionSegment> listReplicationVersionSegments(UUID versionId) {
        return safely(() -> jdbc.query("SELECT * FROM script_replication_version_segment WHERE version_id=? ORDER BY segment_number", (rs, n) -> replicationVersionSegment(rs), versionId.toString()), List.of());
    }
    @Override public Optional<ReplicationVersionSegment> findReplicationVersionSegment(UUID id) {
        return safely(() -> jdbc.query("SELECT * FROM script_replication_version_segment WHERE id=?", (rs, n) -> replicationVersionSegment(rs), id.toString()).stream().findFirst(), Optional.empty());
    }
    @Override public List<ReplicationVersionAsset> listReplicationVersionAssets(UUID versionId) {
        return safely(() -> jdbc.query("SELECT * FROM script_replication_version_asset WHERE version_id=? ORDER BY asset_type, asset_name", (rs, n) -> replicationVersionAsset(rs), versionId.toString()), List.of());
    }
    @Override public void saveCharacterAsset(CharacterAsset a) {
        jdbc.update("""
                INSERT INTO my_script_character_asset (id, project_id, character_name, role_level, anchor_text, image_sources_json, sort_order, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE role_level=VALUES(role_level), anchor_text=VALUES(anchor_text), image_sources_json=VALUES(image_sources_json), sort_order=VALUES(sort_order), updated_at=VALUES(updated_at)
                """, a.id().toString(), a.projectId().toString(), a.characterName(), a.roleLevel(), a.anchor(), a.imageSourcesJson(), a.sortOrder(), Timestamp.from(a.createdAt()), Timestamp.from(a.updatedAt()));
    }
    @Override public void saveEpisodeAsset(EpisodeAsset a) {
        jdbc.update("""
                INSERT INTO my_script_episode_asset (id, episode_id, asset_type, asset_name, prompt_text, image_sources_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE prompt_text=VALUES(prompt_text), image_sources_json=VALUES(image_sources_json), updated_at=VALUES(updated_at)
                """, a.id().toString(), a.episodeId().toString(), a.assetType(), a.assetName(), a.prompt(), a.imageSourcesJson(), Timestamp.from(a.createdAt()), Timestamp.from(a.updatedAt()));
    }
    @Override public void savePrompt(Prompt p) {
        jdbc.update("""
                INSERT INTO my_script_episode_prompt (id, episode_id, version_number, source_type, source_label, idea_text, prompt_text, result_content, status, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE source_type=VALUES(source_type), source_label=VALUES(source_label), idea_text=VALUES(idea_text), prompt_text=VALUES(prompt_text), result_content=VALUES(result_content), status=VALUES(status), error_message=VALUES(error_message), updated_at=VALUES(updated_at)
                """, p.id().toString(), p.episodeId().toString(), p.version(), p.sourceType(), p.sourceLabel(), p.idea(), p.promptText(), p.resultContent(), p.status(), p.error(), Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()));
    }
    @Override public Optional<Prompt> findPrompt(UUID id) {
        return safely(() -> jdbc.query("SELECT * FROM my_script_episode_prompt WHERE id=?", (rs, n) -> prompt(rs), id.toString()).stream().findFirst(), Optional.empty());
    }
    @Override public List<Prompt> listPrompts(UUID episodeId) {
        return safely(() -> jdbc.query("SELECT * FROM my_script_episode_prompt WHERE episode_id=? ORDER BY version_number", (rs, n) -> prompt(rs), episodeId.toString()), List.of());
    }
    @Override public List<Project> listProjects() { return safely(() -> jdbc.query("SELECT * FROM my_script_project ORDER BY updated_at DESC", (rs, n) -> new Project(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("source_job_id")), rs.getString("title"), rs.getString("settings_text"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())), List.of()); }
    @Override public Optional<Project> findProject(UUID id) { return safely(() -> jdbc.query("SELECT * FROM my_script_project WHERE id=?", (rs, n) -> new Project(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("source_job_id")), rs.getString("title"), rs.getString("settings_text"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id.toString()).stream().findFirst(), Optional.empty()); }
    @Override public Optional<Episode> findEpisode(UUID id) { return safely(() -> jdbc.query("SELECT * FROM my_script_episode WHERE id=?", (rs, n) -> episode(rs), id.toString()).stream().findFirst(), Optional.empty()); }
    @Override public Optional<Segment> findSegment(UUID id) { return safely(() -> jdbc.query("SELECT * FROM script_replication_segment WHERE id=?", (rs, n) -> segment(rs), id.toString()).stream().findFirst(), Optional.empty()); }
    @Override public List<Episode> listEpisodes(UUID projectId) { return safely(() -> jdbc.query("SELECT * FROM my_script_episode WHERE project_id=? ORDER BY episode_number", (rs, n) -> episode(rs), projectId.toString()), List.of()); }
    @Override public List<Segment> listSegments(UUID episodeId) { return safely(() -> jdbc.query("SELECT * FROM script_replication_segment WHERE episode_id=? ORDER BY segment_number", (rs, n) -> segment(rs), episodeId.toString()), List.of()); }
    @Override public List<CharacterAsset> listCharacterAssets(UUID projectId) { return safely(() -> jdbc.query("SELECT * FROM my_script_character_asset WHERE project_id=? ORDER BY sort_order", (rs, n) -> character(rs), projectId.toString()), List.of()); }
    @Override public List<EpisodeAsset> listEpisodeAssets(UUID episodeId) { return safely(() -> jdbc.query("SELECT * FROM my_script_episode_asset WHERE episode_id=? ORDER BY asset_type, asset_name", (rs, n) -> episodeAsset(rs), episodeId.toString()), List.of()); }
    private static Segment segment(java.sql.ResultSet rs) throws java.sql.SQLException { return new Segment(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("episode_id")), rs.getInt("segment_number"), rs.getString("content"), rs.getInt("duration_seconds"), rs.getString("status"), rs.getString("comfy_task_id") == null ? null : UUID.fromString(rs.getString("comfy_task_id")), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static ReplicationVersion replicationVersion(java.sql.ResultSet rs) throws java.sql.SQLException { return new ReplicationVersion(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("episode_id")), rs.getInt("version_number"), rs.getString("status"), rs.getString("material_json"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static ReplicationVersionSegment replicationVersionSegment(java.sql.ResultSet rs) throws java.sql.SQLException { return new ReplicationVersionSegment(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("version_id")), rs.getInt("segment_number"), rs.getString("content"), rs.getInt("duration_seconds"), rs.getString("status"), rs.getString("comfy_task_id") == null ? null : UUID.fromString(rs.getString("comfy_task_id")), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static ReplicationVersionAsset replicationVersionAsset(java.sql.ResultSet rs) throws java.sql.SQLException { return new ReplicationVersionAsset(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("version_id")), rs.getString("asset_type"), rs.getString("asset_name"), rs.getString("prompt_text"), rs.getString("image_sources_json"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static CharacterAsset character(java.sql.ResultSet rs) throws java.sql.SQLException { return new CharacterAsset(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("project_id")), rs.getString("character_name"), rs.getString("role_level"), rs.getString("anchor_text"), rs.getString("image_sources_json"), rs.getInt("sort_order"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static EpisodeAsset episodeAsset(java.sql.ResultSet rs) throws java.sql.SQLException { return new EpisodeAsset(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("episode_id")), rs.getString("asset_type"), rs.getString("asset_name"), rs.getString("prompt_text"), rs.getString("image_sources_json"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static Episode episode(java.sql.ResultSet rs) throws java.sql.SQLException { return new Episode(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("project_id")), rs.getInt("episode_number"), rs.getString("title"), rs.getString("summary_text"), rs.getString("content"), rs.getString("status"), rs.getString("message"), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static Prompt prompt(java.sql.ResultSet rs) throws java.sql.SQLException { return new Prompt(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("episode_id")), rs.getInt("version_number"), rs.getString("source_type"), rs.getString("source_label"), rs.getString("idea_text"), rs.getString("prompt_text"), rs.getString("result_content"), rs.getString("status"), rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static <T> T safely(Supplier<T> action, T fallback) { try { return action.get(); } catch (RuntimeException ignored) { return fallback; } }
}
