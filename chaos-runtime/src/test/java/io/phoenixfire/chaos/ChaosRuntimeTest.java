package io.phoenixfire.chaos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosRuntimeTest {

    @Test
    void rollIsDeterministicForTestId() {
        int first = ChaosRuntime.roll(42L, "0001.03");
        int second = ChaosRuntime.roll(42L, "0001.03");
        assertTrue(first >= 1 && first <= 100);
        assertEquals(first, second);
    }
}
