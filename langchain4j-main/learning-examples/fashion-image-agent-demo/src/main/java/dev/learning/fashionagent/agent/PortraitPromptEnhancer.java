package dev.learning.fashionagent.agent;

import dev.learning.fashionagent.ai.PortraitAiService;
import dev.learning.fashionagent.ai.PortraitPromptSpec;
import dev.learning.fashionagent.pipeline.PipelineObserver;
import dev.learning.fashionagent.pipeline.PipelineStage;
import org.springframework.stereotype.Component;

@Component
public class PortraitPromptEnhancer {

    private final PortraitAiService portraitAiService;

    public PortraitPromptEnhancer(PortraitAiService portraitAiService) {
        this.portraitAiService = portraitAiService;
    }

    public PortraitPromptSpec enhance(String description, PipelineObserver observer) {
        observer.stage(PipelineStage.AI_ENRICHING_PORTRAIT_PROMPT, "提示词导演正在补充人物、环境、光线和构图");
        PortraitPromptSpec spec = portraitAiService.enhancePrompt(description);
        if (!spec.aiEnhanced()) {
            throw new IllegalStateException("人物提示词不是 LangChain4j AI 生成结果，任务已停止且不会降级执行");
        }
        observer.portraitPrompt(spec);
        observer.stage(PipelineStage.AI_ENRICHING_PORTRAIT_PROMPT, "人物提示词准备完成（LangChain4j AI 扩写）");
        return spec;
    }

    public PortraitPromptSpec rewriteAfterAudit(
            PortraitPromptSpec rejectedPrompt,
            int auditRetryNumber,
            PipelineObserver observer) {
        observer.stage(
                PipelineStage.AI_REFINING_PORTRAIT_PROMPT,
                "RunningHub 内容审核未通过，提示词导演正在执行第 " + auditRetryNumber + " 次安全重写");
        PortraitPromptSpec rewritten = portraitAiService.rewritePromptAfterAudit(rejectedPrompt, auditRetryNumber);
        if (!rewritten.aiEnhanced()) {
            throw new IllegalStateException("审核后的安全提示词不是 LangChain4j AI 生成结果，任务已停止且不会降级执行");
        }
        observer.portraitPrompt(rewritten);
        observer.stage(
                PipelineStage.AI_REFINING_PORTRAIT_PROMPT,
                "第 " + auditRetryNumber + " 次安全提示词重写完成，准备重新提交人物生成任务");
        return rewritten;
    }
}
