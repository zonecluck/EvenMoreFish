package com.oheers.fish.effects;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RareCatchPolicyTest {
    @Test void commonCannotBecomeCinematicThroughWeightIndexOrExplicitInclude() {
        assertTrue(RareCatchPolicy.eligible("CoMmOn", 9, 1, List.of("Common"), List.of()));
        assertFalse(RareCatchPolicy.eligible("CoMmOn", 9, 1, List.of("Common"), List.of("common")));
    }
    @Test void customCommonExclusionWinsOverHeuristicAndInclude() {
        assertFalse(RareCatchPolicy.eligible("Thuong", 5, 2, List.of("Thuong"), List.of("thuong")));
    }
    @Test void configuredRareIdsAndCustomRareHeuristicWork() {
        assertTrue(RareCatchPolicy.eligible("rare", 0, 100, List.of("Rare"), List.of()));
        assertTrue(RareCatchPolicy.eligible("Ancient", 3, 100, List.of(), List.of()));
        assertTrue(RareCatchPolicy.eligible("Ancient", 0, 5, List.of(), List.of()));
        assertFalse(RareCatchPolicy.eligible("Uncommon", 1, 60, List.of(), List.of()));
        assertFalse(RareCatchPolicy.eligible("Unknown", 0, Double.NaN, List.of(), List.of()));
        assertFalse(RareCatchPolicy.eligible("Disabled", 0, 0, List.of(), List.of()));
    }
}
