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
    void saveReplicationVersion(ReplicationVersion version);
    void saveReplicationVersionSegment(ReplicationVersionSegment segment);
    void saveReplicationVersionAsset(ReplicationVersionAsset asset);
    Optional<ReplicationVersion> findReplicationVersion(UUID id);
    List<ReplicationVersion> listReplicationVersions(UUID episodeId);
    List<ReplicationVersionSegment> listReplicationVersionSegments(UUID versionId);
    Optional<ReplicationVersionSegment> findReplicationVersionSegment(UUID id);
    List<ReplicationVersionAsset> listReplicationVersionAssets(UUID versionId);
    void saveCharacterAsset(CharacterAsset asset);
    void saveEpisodeAsset(EpisodeAsset asset);
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
    List<EpisodeAsset> listEpisodeAssets(UUID episodeId);

    record Project(UUID id, UUID sourceJobId, String title, String settings, Instant createdAt, Instant updatedAt) {}
    record Episode(UUID id, UUID projectId, int number, String title, String summary, String content, String status,
                   String message, String error, Instant createdAt, Instant updatedAt) {}
    record Segment(UUID id, UUID episodeId, int number, String content, int durationSeconds, String status,
                   UUID comfyTaskId, String error, Instant createdAt, Instant updatedAt) {}
    record CharacterAsset(UUID id, UUID projectId, String characterName, String roleLevel, String anchor,
                          String imageSourcesJson, int sortOrder, Instant createdAt, Instant updatedAt) {}
    record EpisodeAsset(UUID id, UUID episodeId, String assetType, String assetName, String prompt,
                        String imageSourcesJson, Instant createdAt, Instant updatedAt) {}
    record Prompt(UUID id, UUID episodeId, int version, String sourceType, String sourceLabel, String idea,
                  String promptText, String resultContent, String status, String error,
                  Instant createdAt, Instant updatedAt) {}
    record ReplicationVersion(UUID id, UUID episodeId, int version, String status, String materialJson,
                              Instant createdAt, Instant updatedAt) {}
    record ReplicationVersionSegment(UUID id, UUID versionId, int number, String content, int durationSeconds,
                                     String status, UUID comfyTaskId, String error, Instant createdAt, Instant updatedAt) {}
    record ReplicationVersionAsset(UUID id, UUID versionId, String assetType, String assetName, String prompt,
                                   String imageSourcesJson, Instant createdAt, Instant updatedAt) {}
}
