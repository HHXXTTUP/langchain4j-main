package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.ai.OutfitQualityReport;
import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class                                                                                                                                                                                                                                                                                                                                                                                        Agent4ResultPresenter {

    public PresentedResult present(
            Path finalImage,
            Path clothingImage,
            OutfitQualityReport qualityReport,
            int attemptCount,
            PipelineObserver observer) {
        observer.stage(PipelineStage.AGENT4_PRESENTING_RESULT, "Agent4 正在整理最终结果");
        String reply;
        if (!qualityReport.evaluated()) {
            reply = "换装完成，已随机使用服装：" + clothingImage.getFileName()
                    + "。多模态质检调用失败并已跳过，流程继续完成：" + qualityReport.summary();
        } else if (qualityReport.passed()) {
            reply = "换装完成并通过 AI 视觉质检，共生成 " + attemptCount + " 次，综合评分 "
                    + qualityReport.overallScore() + "，头饰匹配评分 "
                    + qualityReport.headAccessoryMatchScore() + "。";
        } else {
            reply = "换装已完成，共生成 " + attemptCount + " 次；AI 质检仍发现问题："
                    + qualityReport.summary() + "。已停止自动重试，请检查底层换装工作流的头部遮罩能力。";
        }
        return new PresentedResult(finalImage, reply);
    }

    public record PresentedResult(Path image, String reply) {}
}
