package com.oheers.fish.autofish;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AutoFishProfileTest {

    @Test
    void defaultProfileHasExpectedBaselineStats() {
        AutoFishProfile profile = AutoFishProfile.createDefault(UUID.randomUUID(), 300L);

        assertEquals(300L, profile.getRemainingSeconds());
        assertEquals(25.0, profile.getMissChancePercent(), 1e-6);
        assertEquals(4.5, profile.getCastDelaySeconds(), 1e-6);
        assertEquals(90, profile.getBaseCastDelayTicks());
        assertEquals(0.65, profile.getReactionSeconds(), 1e-6);
        assertEquals(13, profile.getBaseReactionTicks());
        assertEquals(0.0, profile.getDurabilitySavePercent(), 1e-6);
        assertTrue(profile.isAutoSellOnFullInventory());
        assertTrue(profile.isAutoSwitchRod());
        assertEquals(0, profile.getActivePackageTier());
        assertFalse(profile.isFirstPurchaseClaimed());
    }

    @Test
    void maxUpgradesReachTargetBounds() {
        AutoFishProfile profile = new AutoFishProfile(
            UUID.randomUUID(),
            3600L,
            AutoFishProfile.MAX_MISS_REDUCTION_LEVEL,
            AutoFishProfile.MAX_CAST_DELAY_LEVEL,
            AutoFishProfile.MAX_REACTION_LEVEL,
            AutoFishProfile.MAX_DURABILITY_SAVE_LEVEL,
            true,
            true,
            42L
        );

        assertEquals(2.0, profile.getMissChancePercent(), 1e-6);
        assertEquals(1.0, profile.getCastDelaySeconds(), 1e-6);
        assertEquals(20, profile.getBaseCastDelayTicks());
        assertEquals(0.25, profile.getReactionSeconds(), 1e-6);
        assertEquals(5, profile.getBaseReactionTicks());
        assertEquals(50.0, profile.getDurabilitySavePercent(), 1e-6);
    }

    @Test
    void vipPackageTiersGrantExpectedBuffsAndDiscounts() {
        AutoFishProfile profile = AutoFishProfile.createDefault(UUID.randomUUID(), 300L);
        profile.applyPackagePurchaseTier(4);

        assertEquals(4, profile.getActivePackageTier());
        assertEquals(1, profile.getTotalPackagesPurchased());
        assertEquals(2, profile.getLoyaltyBonusTimePercent());
        assertEquals(0.35, profile.getPackageLuckBonus(), 1e-6);
        assertEquals(0.25, profile.getPackageLureBonus(), 1e-6);
        assertEquals(0.20, profile.getPackageDoubleCatchBonus(), 1e-6);
        assertEquals(25.0, profile.getPackageSellBonusPercent(), 1e-6);
        assertEquals(20, profile.getUpgradeDiscountPercent());
        assertEquals(8_000.0, AutoFishMenuManager.applyDiscount(10_000.0, profile.getUpgradeDiscountPercent()), 1e-6);
    }

    @Test
    void timeConsumptionAndFormattingWorkCorrectly() {
        AutoFishProfile profile = new AutoFishProfile(UUID.randomUUID(), 2L, 0, 0, 0, 0, true, true, 0L);
        profile.applyPackagePurchaseTier(3);
        assertEquals(3, profile.getActivePackageTier());

        assertTrue(profile.consumeOneSecond());
        assertEquals(1L, profile.getRemainingSeconds());
        assertEquals(3, profile.getActivePackageTier());

        assertTrue(profile.consumeOneSecond());
        assertEquals(0L, profile.getRemainingSeconds());
        assertEquals(0, profile.getActivePackageTier());
        assertFalse(profile.consumeOneSecond());

        assertEquals("00:00", AutoFishProfile.formatCompactDuration(0));
        assertEquals("05:00", AutoFishProfile.formatCompactDuration(300));
        assertEquals("01:30:15", AutoFishProfile.formatCompactDuration(5415));
    }
}
