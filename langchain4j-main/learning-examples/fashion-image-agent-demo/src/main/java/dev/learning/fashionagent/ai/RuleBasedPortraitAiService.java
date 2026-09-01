package dev.learning.fashionagent.ai;

import java.nio.file.Path;

final class RuleBasedPortraitAiService implements PortraitAiService {

    private final String reason;

    RuleBasedPortraitAiService(String reason) {
        this.reason = reason;
    }

    @Override
    public PortraitPromptSpec enhancePrompt(String description) {
        return PortraitPromptSpec.fallback(description, reason);
    }

    @Override
    public PortraitPromptSpec rewritePromptAfterAudit(PortraitPromptSpec rejectedPrompt, int auditRetryNumber) {
        return PortraitPromptSpec.fallback(rejectedPrompt.originalDescription(), reason);
    }

    @Override
    public PortraitQualityReport inspectPortrait(
            Path portraitImage,
            PortraitPromptSpec promptSpec,
            String appliedPrompt,
            int attemptNumber) {
        return PortraitQualityReport.notEvaluated(reason);
    }

    @Override
    public boolean aiEnabled() {
        return false;
    }
}
