package dev.learning.fashionagent.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.learning.fashionagent.ai.ClothingCatalogAnalysis;
import dev.learning.fashionagent.ai.FashionExperienceDraft;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFashionLearningRepository implements FashionLearningRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcFashionLearningRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveClothingProfile(ClothingProfile profile) {
        requireTable("clothing_profile");
        jdbcTemplate.update("""
                INSERT INTO clothing_profile (
                    id, file_name, image_path, sha256, profile_json, search_text, model_name, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    file_name = VALUES(file_name), image_path = VALUES(image_path),
                    sha256 = VALUES(sha256), profile_json = VALUES(profile_json),
                    search_text = VALUES(search_text), model_name = VALUES(model_name),
                    updated_at = VALUES(updated_at)
                """,
                profile.id(),
                profile.fileName(),
                profile.imagePath().toAbsolutePath().normalize().toString(),
                profile.sha256(),
                json(profile.analysis()),
                profile.searchText(),
                profile.modelName(),
                Timestamp.from(profile.updatedAt()));
    }

    @Override
    public List<ClothingProfile> listClothingProfiles() {
        requireTable("clothing_profile");
        return jdbcTemplate.query("""
                        SELECT id, file_name, image_path, sha256, profile_json, model_name, updated_at
                        FROM clothing_profile ORDER BY file_name
                        """,
                (resultSet, rowNumber) -> new ClothingProfile(
                        resultSet.getString("id"),
                        resultSet.getString("file_name"),
                        Path.of(resultSet.getString("image_path")),
                        resultSet.getString("sha256"),
                        fromJson(resultSet.getString("profile_json"), ClothingCatalogAnalysis.class),
                        resultSet.getString("model_name"),
                        resultSet.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public Optional<ClothingProfile> findClothingProfile(String id) {
        requireTable("clothing_profile");
        return jdbcTemplate.query("""
                        SELECT id, file_name, image_path, sha256, profile_json, model_name, updated_at
                        FROM clothing_profile WHERE id = ?
                        """,
                (resultSet, rowNumber) -> new ClothingProfile(
                        resultSet.getString("id"),
                        resultSet.getString("file_name"),
                        Path.of(resultSet.getString("image_path")),
                        resultSet.getString("sha256"),
                        fromJson(resultSet.getString("profile_json"), ClothingCatalogAnalysis.class),
                        resultSet.getString("model_name"),
                        resultSet.getTimestamp("updated_at").toInstant()),
                id).stream().findFirst();
    }

    @Override
    public void deleteClothingProfilesNotIn(List<String> activeIds) {
        requireTable("clothing_profile");
        if (activeIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM clothing_profile");
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(activeIds.size(), "?"));
        jdbcTemplate.update(
                "DELETE FROM clothing_profile WHERE id NOT IN (" + placeholders + ")",
                activeIds.toArray());
    }

    @Override
    public void saveExperience(LearnedFashionExperience experience) {
        requireTable("fashion_learned_experience");
        jdbcTemplate.update("""
                INSERT INTO fashion_learned_experience (
                    id, source_job_id, title, scenario, experience_json,
                    knowledge_text, quality_score, approved, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title), scenario = VALUES(scenario),
                    experience_json = VALUES(experience_json), knowledge_text = VALUES(knowledge_text),
                    quality_score = VALUES(quality_score), approved = VALUES(approved)
                """,
                experience.id(),
                experience.sourceJobId().toString(),
                experience.content().title(),
                experience.content().scenario(),
                json(experience.content()),
                experience.knowledgeText(),
                experience.qualityScore(),
                experience.approved(),
                Timestamp.from(experience.createdAt()));
    }

    @Override
    public boolean experienceExists(UUID sourceJobId) {
        requireTable("fashion_learned_experience");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fashion_learned_experience WHERE source_job_id = ?",
                Integer.class,
                sourceJobId.toString());
        return count != null && count > 0;
    }

    @Override
    public List<LearnedFashionExperience> listApprovedExperiences() {
        requireTable("fashion_learned_experience");
        return jdbcTemplate.query("""
                        SELECT id, source_job_id, experience_json, quality_score, approved, created_at
                        FROM fashion_learned_experience WHERE approved = TRUE ORDER BY created_at
                        """,
                (resultSet, rowNumber) -> new LearnedFashionExperience(
                        resultSet.getString("id"),
                        UUID.fromString(resultSet.getString("source_job_id")),
                        fromJson(resultSet.getString("experience_json"), FashionExperienceDraft.class),
                        resultSet.getInt("quality_score"),
                        resultSet.getBoolean("approved"),
                        resultSet.getTimestamp("created_at").toInstant()));
    }

    private void requireTable(String table) {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "本地 H2 资料表 " + table + " 不可用，请检查 data 目录及 schema-h2.sql", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学习资料 JSON 序列化失败", exception);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学习资料 JSON 解析失败", exception);
        }
    }
}
