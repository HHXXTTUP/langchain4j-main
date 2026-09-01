package dev.learning.fashionagent.job;

import dev.learning.fashionagent.pipeline.OutfitAttempt;
import dev.learning.fashionagent.pipeline.PortraitAttempt;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface JobHistoryRepository {

    boolean available();

    int recoverInterruptedJobs();

    void saveJob(JobPersistenceSnapshot snapshot);

    void appendEvent(UUID jobId, JobStepView event);

    void savePortraitAttempt(UUID jobId, PortraitAttempt attempt);

    void saveOutfitAttempt(UUID jobId, OutfitAttempt attempt);

    Optional<JobView> findJob(UUID jobId);

    List<JobView> listJobs();

    List<JobStepView> listEvents(UUID jobId);

    void delete(UUID jobId);

    Optional<Path> findOriginalImage(UUID jobId);

    Optional<Path> findClothingImage(UUID jobId);

    Optional<Path> findFinalImage(UUID jobId);

    Optional<Path> findPortraitAttemptImage(UUID jobId, int attemptNumber);

    Optional<Path> findOutfitAttemptImage(UUID jobId, int attemptNumber);

    static JobHistoryRepository noop() {
        return new JobHistoryRepository() {
            @Override public boolean available() { return false; }
            @Override public int recoverInterruptedJobs() { return 0; }
            @Override public void saveJob(JobPersistenceSnapshot snapshot) {}
            @Override public void appendEvent(UUID jobId, JobStepView event) {}
            @Override public void savePortraitAttempt(UUID jobId, PortraitAttempt attempt) {}
            @Override public void saveOutfitAttempt(UUID jobId, OutfitAttempt attempt) {}
            @Override public Optional<JobView> findJob(UUID jobId) { return Optional.empty(); }
            @Override public List<JobView> listJobs() { return List.of(); }
            @Override public List<JobStepView> listEvents(UUID jobId) { return List.of(); }
            @Override public void delete(UUID jobId) {}
            @Override public Optional<Path> findOriginalImage(UUID jobId) { return Optional.empty(); }
            @Override public Optional<Path> findClothingImage(UUID jobId) { return Optional.empty(); }
            @Override public Optional<Path> findFinalImage(UUID jobId) { return Optional.empty(); }
            @Override public Optional<Path> findPortraitAttemptImage(UUID jobId, int attemptNumber) { return Optional.empty(); }
            @Override public Optional<Path> findOutfitAttemptImage(UUID jobId, int attemptNumber) { return Optional.empty(); }
        };
    }
}
