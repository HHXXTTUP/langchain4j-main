package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.ai.FashionVisionService;
import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class FashionReferenceAnalyzer {

    private final FashionVisionService fashionVisionService;

    public FashionReferenceAnalyzer(FashionVisionService fashionVisionService) {
        this.fashionVisionService = fashionVisionService;
    }

    public FashionReferenceSpec analyze(Path clothingImage, PipelineObserver observer) {
        observer.stage(PipelineStage.AI_ANALYZING_CLOTHING, "视觉分析 Agent 正在识别服装与头部配饰");
        FashionReferenceSpec spec = fashionVisionService.analyzeClothing(clothingImage);
        if (!spec.aiAnalyzed()) {
            throw new IllegalStateException("服装分析不是 LangChain4j AI 结果，任务已停止且不会降级执行");
        }
        observer.fashionAnalysis(spec);
        observer.stage(
                PipelineStage.AI_ANALYZING_CLOTHING,
                "服装分析完成（多模态 AI），识别头部配饰：" + spec.headAccessories());
        return spec;
    }
}
