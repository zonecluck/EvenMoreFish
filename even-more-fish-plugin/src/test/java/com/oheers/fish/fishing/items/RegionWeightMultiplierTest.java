package com.oheers.fish.fishing.items;

import com.oheers.fish.utils.WeightedRandom;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RegionWeightMultiplierTest {
    @Test
    void preservesFractionsWithoutRoundingOrDuplicating() {
        assertEquals(5.0, RegionWeightMultiplier.apply(10, 0.5));
        assertEquals(18.0, RegionWeightMultiplier.apply(10, 1.8));
        assertEquals(10.0, RegionWeightMultiplier.apply(10, 1.0));
        assertEquals(0.0, RegionWeightMultiplier.apply(10, 0));
        assertEquals(0.0, RegionWeightMultiplier.apply(10, -3));
        assertEquals(0.0, RegionWeightMultiplier.apply(10, Double.NaN));
        assertEquals(0.0, RegionWeightMultiplier.apply(10, Double.POSITIVE_INFINITY));
    }

    @Test
    void fractionalMultiplierChangesEffectiveDistribution() {
        int rare = 0;
        Random rng = new Random(12);
        for (int i = 0; i < 20_000; i++) {
            String pick = WeightedRandom.pick(List.of("COMMON", "RARE"),
                id -> RegionWeightMultiplier.apply(10, id.equals("RARE") ? 0.5 : 1.0), rng);
            if ("RARE".equals(pick)) rare++;
        }
        assertTrue(rare > 6000 && rare < 7400,
            "0.5 multiplier should yield about 1/3 rare catches, got " + rare);
    }
}
