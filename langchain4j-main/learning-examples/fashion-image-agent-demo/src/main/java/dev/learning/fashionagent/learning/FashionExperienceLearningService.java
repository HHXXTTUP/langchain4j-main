package dev.learning.fashionagent.learning;

import dev.learning.fashionagent.ai.FashionExperienceDraft;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.learning.fashionagent.ai.FashionVisionService;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.pipeline.OutfitAttempt;
import dev.learning.fashionagent.pipeline.PipelineResult;
import dev.learning.fashionagent.rag.FashionKnowledgeRetriever;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class FashionExperienceLearningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FashionExperienceLearningService.class);

    private final FashionVisionService visionService;
    private final FashionLearningRepository repository;
    private final FashionKnowledgeRetriever knowledgeRetriever;
    private final FashionAiProperties properties;

    public FashionExperienceLearningService(
            @Qualifier("fashionVisionService") FashionVisionService visionService,
            FashionLearningRepository repository,
            FashionKnowledgeRetriever knowledgeRetriever,
            FashionAiProperties properties) {
        this.visionService = visionService;
        this.repository = repository;
        this.knowledgeRetriever = knowledgeRetriever;
        this.properties = properties;
    }

    public ExperienceLearningResult learn(UUID jobId, String userDescription, PipelineResult result) {
        OutfitQualityReport quality = result.finalQualityReport();
        if (quality == null || !quality.evaluated()) {
            return ExperienceLearningResult.skipped("换装视觉质检未执行，缺少可靠证据，本次不更新知识库");
        }
        if (quality.overallScore() < properties.getOutfitAcceptAndLearnScore()) {
            return ExperienceLearningResult.skipped(
                    "换装综合评分 " + quality.overallScore() + " 分，低于 "
                            + properties.getOutfitAcceptAndLearnScore() + " 分，本次不更新知识库");
        }
        if (repository.experienceExists(jobId)) {
            return ExperienceLearningResult.skipped("该任务已经提取过经验，不重复写入知识库");
        }
        OutfitAttempt selectedAttempt = result.attempts().stream()
                .filter(OutfitAttempt::selected)
                .findFirst()
                .orElseGet(() -> result.attempts().get(result.attempts().size() - 1));
        FashionExperienceDraft draft = visionService.extractSuccessfulExperience(
                userDescription,
                result.fashionAnalysis(),
                quality,
                selectedAttempt.prompt());
        LearnedFashionExperience experience = new LearnedFashionExperience(
                UUID.randomUUID().toString(),
                jobId,
                draft,
                quality.overallScore(),
                true,
                Instant.now());
        repository.saveExperience(experience);
        knowledgeRetriever.addExperience(experience);
        LOGGER.info("任务换装经验学习完成 jobId={} experienceId={} qualityScore={} passed={} missing={}",
                jobId, experience.id(), experience.qualityScore(), quality.passed(), quality.missingElements());
        return ExperienceLearningResult.learned(experience);
    }

    public List<LearnedFashionExperience> listApproved() {
        return repository.listApprovedExperiences();
    }
}
