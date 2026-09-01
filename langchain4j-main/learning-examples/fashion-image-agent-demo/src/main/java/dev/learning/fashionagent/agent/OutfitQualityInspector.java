package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.FashionVisionService;
import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class OutfitQualityInspector {

    private final FashionVisionService fashionVisionService;

    public OutfitQualityInspector(FashionVisionService fashionVisionService) {
        this.fashionVisionService = fashionVisionService;
    }

    public OutfitQualityReport inspect(
            Path originalImage,
            Path clothingImage,
            Path resultImage,
            FashionReferenceSpec referenceSpec,
            String appliedPrompt,
            int attemptNumber,
            PipelineObserver observer) {
        observer.stage(
                PipelineStage.AI_VERIFYING_OUTFIT,
                "视觉质检 Agent 正在检查第 " + attemptNumber + " 次换装结果");
        OutfitQualityReport report = fashionVisionService.inspectResult(
                originalImage,
                clothingImage,
                resultImage,
                referenceSpec,
                appliedPrompt,
                attemptNumber);
        String conclusion = report.evaluated()
                ? "总分 " + report.overallScore() + "，头饰匹配 " + report.headAccessoryMatchScore()
                : "质检调用失败，已跳过并继续：" + report.summary();
        observer.stage(PipelineStage.AI_VERIFYING_OUTFIT, "第 " + attemptNumber + " 次质检完成：" + conclusion);
        return report;
    }
}
