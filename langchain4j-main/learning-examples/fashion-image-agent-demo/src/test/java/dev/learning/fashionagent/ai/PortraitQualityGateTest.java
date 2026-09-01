package dev.learning.fashionagent.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortraitQualityGateTest {

    private final PortraitQualityGate gate = new PortraitQualityGate(new FashionAiProperties());

    @Test
    void shouldRequireTechnicalValidityAndEveryConfiguredScore() {
        assertTrue(gate.passes(true, 80, 80, 80, 80));
        assertFalse(gate.passes(false, 90, 90, 90, 90));
        assertFalse(gate.passes(true, 70, 90, 90, 90));
        assertFalse(gate.passes(true, 90, 60, 90, 90));
        assertFalse(gate.passes(true, 90, 90, 60, 90));
        assertFalse(gate.passes(true, 90, 90, 90, 60));
    }

    @Test
    void fallbackPromptShouldKeepAdultAndFullBodyConstraints() {
        PortraitPromptSpec spec = PortraitPromptSpec.fallback("一个美女", "AI 未配置");

        assertTrue(spec.generationPrompt().contains("年满20岁"));
        assertTrue(spec.generationPrompt().contains("全身完整"));
    }
}
