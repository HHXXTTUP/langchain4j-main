package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.ai.PortraitAiService;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.ai.PortraitQualityReport;
import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class PortraitQualityInspector {

    private final PortraitAiService portraitAiService;

    public PortraitQualityInspector(PortraitAiService portraitAiService) {
        this.portraitAiService = portraitAiService;
    }

    public PortraitQualityReport inspect(
            Path portraitImage,
            PortraitPromptSpec promptSpec,
            String appliedPrompt,
            int attemptNumber,
            PipelineObserver observer) {
        observer.stage(
                PipelineStage.AI_VERIFYING_PORTRAIT,
                "人物质检 Agent 正在检查第 " + attemptNumber + " 张人物底图");
        PortraitQualityReport report = portraitAiService.inspectPortrait(
                portraitImage,
                promptSpec,
                appliedPrompt,
                attemptNumber);
        String conclusion = report.evaluated()
                ? "总分 " + report.overallScore() + "，提示词一致性 " + report.promptAlignmentScore()
                : "质检调用失败，已跳过并继续：" + report.summary();
        observer.stage(
                PipelineStage.AI_VERIFYING_PORTRAIT,
                "第 " + attemptNumber + " 张人物底图质检完成：" + conclusion);
        return report;
    }

    public boolean aiEnabled() {
        return portraitAiService.aiEnabled();
    }
}
