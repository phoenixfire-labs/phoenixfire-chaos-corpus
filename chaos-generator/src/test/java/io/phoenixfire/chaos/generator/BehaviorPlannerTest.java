package io.phoenixfire.chaos.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorPlannerTest {

    @Test
    void smokeTierProducesExpectedVolume() {
        GenerationResult result = new BehaviorPlanner(42).plan(TierName.SMOKE);
        assertEquals(20, result.classes().size());
        assertEquals(100, result.totalTests());
        assertTrue(result.outcomes().stream().anyMatch(o -> o.behavior().contains("STATELESS_FLAKE")));
        assertTrue(result.outcomes().stream().anyMatch(o -> o.behavior().contains("POLLUTION_CRASH")));
    }

    @Test
    void seedIsStable() {
        GenerationResult a = new BehaviorPlanner(99).plan(TierName.SMOKE);
        GenerationResult b = new BehaviorPlanner(99).plan(TierName.SMOKE);
        assertEquals(a.outcomes().get(0).testId(), b.outcomes().get(0).testId());
        assertEquals(a.outcomes().get(0).behavior(), b.outcomes().get(0).behavior());
    }
}
