package com.oheers.fish.fishing.rods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MMOItemsRodStatsTest {
    @Test
    void fallbackByItemIdProvidesProgressiveStatsForFishermanAndLevelRods() {
        MMOItemsRodStats rod1 = MMOItemsRodStats.fallbackByItemId("FISHING_ROD_1");
        MMOItemsRodStats rod5 = MMOItemsRodStats.fallbackByItemId("FISHING_ROD_5");
        assertFalse(rod1.isEmpty());
        assertFalse(rod5.isEmpty());
        assertTrue(rod5.luckModifier() > rod1.luckModifier());
        assertTrue(rod5.lureModifier() > rod1.lureModifier());
        assertTrue(rod5.doubleCatchChance() > rod1.doubleCatchChance());
        assertTrue(rod5.rareFishModifier() > rod1.rareFishModifier());
        assertTrue(rod5.expBonus() > rod1.expBonus());

        MMOItemsRodStats lvl1 = MMOItemsRodStats.fallbackByItemId("ROD_1");
        MMOItemsRodStats lvl50 = MMOItemsRodStats.fallbackByItemId("ROD_50");
        assertFalse(lvl1.isEmpty());
        assertFalse(lvl50.isEmpty());
        assertTrue(lvl50.luckModifier() > lvl1.luckModifier());
        assertTrue(lvl50.lureModifier() > lvl1.lureModifier());
        assertTrue(lvl50.doubleCatchChance() > lvl1.doubleCatchChance());
        assertTrue(lvl50.rareFishModifier() > lvl1.rareFishModifier());
        assertTrue(lvl50.expBonus() > lvl1.expBonus());

        assertTrue(MMOItemsRodStats.fallbackByItemId("UNKNOWN_ROD").isEmpty());
        assertSame(MMOItemsRodStats.ZERO, MMOItemsRodStats.resolve((org.bukkit.inventory.ItemStack) null));
    }
}
