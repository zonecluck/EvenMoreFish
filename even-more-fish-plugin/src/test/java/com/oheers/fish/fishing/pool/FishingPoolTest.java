package com.oheers.fish.fishing.pool;

import com.oheers.fish.utils.WeightedRandom;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FishingPoolTest {
    private FishingPool pool(String id, double x, double y, double z) {
        return new FishingPool(id, "world", x, y, z, 5, 25, 2);
    }

    @Test void radiusIncludesEdgeButRejectsWrongWorldAndVerticalFloor() {
        FishingPool pool = pool("lake", 10, 64, -5);
        assertTrue(pool.contains("world", 13, 66, -1, 2));
        assertFalse(pool.contains("nether", 10, 64, -5, 2));
        assertFalse(pool.contains("world", 10, 66.01, -5, 2));
        assertFalse(pool.contains("world", 15.01, 64, -5, 2));
        assertFalse(pool.contains("world", Double.NaN, 64, -5, 2));
    }

    @Test void nearestOverlapWinsAndIdsResolveTiesWithoutStacking() {
        FishingPool a = pool("a", 0, 64, 0), b = pool("b", 2, 64, 0);
        assertEquals(b, FishingPool.select(List.of(a, b), "world", 2, 64, 0, 2));
        assertEquals(a, FishingPool.select(List.of(b, a), "world", 1, 64, 0, 2));
        assertNull(FishingPool.select(List.of(a, b), "world", 100, 64, 0, 2));
    }

    @Test void finiteOutOfRangeBonusesClampAndNonfiniteGeometryFails() {
        FishingPool pool = new FishingPool("LAKE", "world", 0, 64, 0, 1000, 1000, 1000);
        assertEquals("lake", pool.id());
        assertEquals(64, pool.radius()); assertEquals(85, pool.speedPercent()); assertEquals(10, pool.rareMultiplier());
        assertThrows(IllegalArgumentException.class, () -> new FishingPool("lake", "world", 0, 64, 0, 5, Double.NaN, 2));
        assertThrows(IllegalArgumentException.class, () -> pool("lake", Double.POSITIVE_INFINITY, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> pool("lake.bad", 0, 64, 0));
    }

    @Test void zeroWeightsStayZeroAndRareMultiplierChangesActualSelection() {
        assertEquals(0, FishingPool.applyWeight(0, 10));
        assertEquals(0, FishingPool.applyWeight(-1, 10));
        assertEquals(0, FishingPool.applyWeight(Double.NaN, 10));
        assertEquals(5, FishingPool.applyWeight(5, Double.NaN));
        assertEquals(5, FishingPool.applyWeight(5, Double.POSITIVE_INFINITY));
        Random random = new Random(8912);
        int rare = 0;
        for (int i = 0; i < 20_000; i++) {
            String selected = WeightedRandom.pick(List.of("disabled", "common", "rare"),
                id -> FishingPool.applyWeight(id.equals("disabled") ? 0 : 10, id.equals("rare") ? 2 : 1), random);
            assertNotEquals("disabled", selected);
            if (selected.equals("rare")) rare++;
        }
        assertTrue(rare > 12800 && rare < 13900, "x2 rare weight yields about two thirds; actual " + rare);
    }
}
