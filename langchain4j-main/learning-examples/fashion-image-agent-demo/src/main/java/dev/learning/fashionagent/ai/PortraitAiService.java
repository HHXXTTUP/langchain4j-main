package dev.learning.fashionagent.ai;

import java.nio.file.Path;

public interface PortraitAiService {

    PortraitPromptSpec enhancePrompt(String description);

    PortraitPromptSpec rewritePromptAfterAudit(PortraitPromptSpec rejectedPrompt, int auditRetryNumber);

    PortraitQualityReport inspectPortrait(
            Path portraitImage,
            PortraitPromptSpec promptSpec,
            String appliedPrompt,
            int attemptNumber);

    boolean aiEnabled();
}
