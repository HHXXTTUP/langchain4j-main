package dev.learning.fashionagent.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OutfitQualityGateTest {

    private final OutfitQualityGate gate = new OutfitQualityGate(new FashionAiProperties());

    @Test
    void shouldRequireEveryConfiguredScoreWhenReferenceContainsHeadAccessories() {
        assertTrue(gate.passes(80, 80, 80, 80, true));
        assertFalse(gate.passes(80, 70, 80, 80, true));
        assertFalse(gate.passes(80, 80, 60, 80, true));
        assertFalse(gate.passes(80, 80, 80, 60, true));
    }

    @Test
    void shouldIgnoreHeadAccessoryScoreWhenReferenceHasNoHeadAccessory() {
        assertTrue(gate.passes(80, 80, 0, 80, false));
    }
}
