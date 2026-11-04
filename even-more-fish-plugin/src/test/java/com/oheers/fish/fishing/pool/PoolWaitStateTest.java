package com.oheers.fish.fishing.pool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoolWaitStateTest {
    @Test void reducesExistingRodAndLureAdjustedCountdownOnlyOnce() {
        PoolWaitState state = new PoolWaitState();
        assertEquals(0, state.reduce(0, 50));
        assertFalse(state.used());
        assertEquals(100, state.reduce(200, 50));
        assertEquals(98, state.reduce(98, 50));
        assertEquals(198, state.restore(98));
        assertEquals(196, state.reduce(196, 85));
    }

    @Test void leavingPoolPreservesElapsedTicksAndCannotRestartAnApproachingFish() {
        PoolWaitState state = new PoolWaitState();
        assertEquals(60, state.reduce(400, 85));
        assertEquals(380, state.restore(40));
        assertEquals(380, state.restore(380));
        PoolWaitState completed = new PoolWaitState();
        completed.reduce(400, 85);
        assertEquals(0, completed.restore(0));
    }

    @Test void invalidOrZeroSpeedNeverConsumesReductionAndWaitNeverBecomesZero() {
        PoolWaitState state = new PoolWaitState();
        assertEquals(1, state.reduce(1, Double.NaN));
        assertEquals(1, state.reduce(1, 0));
        assertFalse(state.used());
        assertEquals(1, state.reduce(1, 85));
    }
}
