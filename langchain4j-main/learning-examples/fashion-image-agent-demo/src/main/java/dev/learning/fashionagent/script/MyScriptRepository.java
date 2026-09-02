package dev.learning.fashionagent.script;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MyScriptRepository {
    void saveProject(Project project);
    void saveEpisode(Episode episode);
    void saveSegment(Segment segment);
    void deleteSegments(UUID episodeId);
    void saveReplicationMaterial(UUID episodeId, String materialJson);
    void deleteReplicationMaterial(UUID episodeId);
    Optional<String> findReplicationMaterial(UUID episodeId);
    void saveCharacterAsset(CharacterAsset asset);
    void savePrompt(Prompt prompt);
    Optional<Prompt> findPrompt(UUID id);
    List<Prompt> listPrompts(UUID episodeId);
    List<Project> listProjects();
    Optional<Project> findProject(UUID id);
    Optional<Episode> findEpisode(UUID id);
    Optional<Segment> findSegment(UUID id);
    List<Episode> listEpisodes(UUID projectId);
    List<Segment> listSegments(UUID episodeId);
    List<CharacterAsset> listCharacterAssets(UUID projectId);

    record Project(UUID id, UUID sourceJobId, String title, String settings, Instant createdAt, Instant updatedAt) {}
    record Episode(UUID id, UUID projectId, int number, String title, String content, String status,
                   String message, String error, Instant createdAt, Instant updatedAt) {}
    record Segment(UUID id, UUID episodeId, int number, String content, int durationSeconds, String status,
                   UUID comfyTaskId, String error, Instant createdAt, Instant updatedAt) {}
    record CharacterAsset(UUID id, UUID projectId, String characterName, String roleLevel, String anchor,
                          String imageSourcesJson, int sortOrder, Instant createdAt, Instant updatedAt) {}
    record Prompt(UUID id, UUID episodeId, int version, String sourceType, String sourceLabel, String idea,
                  String promptText, String resultContent, String status, String error,
                  Instant createdAt, Instant updatedAt) {}
}
