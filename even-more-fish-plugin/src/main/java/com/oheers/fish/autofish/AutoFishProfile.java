package com.oheers.fish.autofish;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Persistent player progression, AI smart toggles, and VIP package perks
 * for the human-like Auto-Fishing system.
 */
public final class AutoFishProfile {

    public static final int MAX_MISS_REDUCTION_LEVEL = 10;
    public static final int MAX_CAST_DELAY_LEVEL = 10;
    public static final int MAX_REACTION_LEVEL = 5;
    public static final int MAX_DURABILITY_SAVE_LEVEL = 5;
    public static final int MAX_PACKAGE_TIER = 4;

    private final UUID playerId;
    private long remainingSeconds;
    private int missReductionLevel;
    private int castDelayLevel;
    private int reactionLevel;
    private int durabilitySaveLevel;
    private boolean stopOnFullInventory;
    private boolean autoRecastOnMiss;
    private boolean autoSellOnFullInventory;
    private boolean autoSwitchRod;
    private int activePackageTier;
    private int totalPackagesPurchased;
    private boolean firstPurchaseClaimed;
    private long totalAutoCatches;
    private boolean dirty;

    public AutoFishProfile(
            @NotNull UUID playerId,
            long remainingSeconds,
            int missReductionLevel,
            int castDelayLevel,
            int reactionLevel,
            int durabilitySaveLevel,
            boolean stopOnFullInventory,
            boolean autoRecastOnMiss,
            long totalAutoCatches
    ) {
        this(
                playerId,
                remainingSeconds,
                missReductionLevel,
                castDelayLevel,
                reactionLevel,
                durabilitySaveLevel,
                stopOnFullInventory,
                autoRecastOnMiss,
                true,
                true,
                0,
                0,
                false,
                totalAutoCatches
        );
    }

    public AutoFishProfile(
            @NotNull UUID playerId,
            long remainingSeconds,
            int missReductionLevel,
            int castDelayLevel,
            int reactionLevel,
            int durabilitySaveLevel,
            boolean stopOnFullInventory,
            boolean autoRecastOnMiss,
            boolean autoSellOnFullInventory,
            boolean autoSwitchRod,
            int activePackageTier,
            int totalPackagesPurchased,
            boolean firstPurchaseClaimed,
            long totalAutoCatches
    ) {
        this.playerId = playerId;
        this.remainingSeconds = Math.max(0L, remainingSeconds);
        this.missReductionLevel = clamp(missReductionLevel, 0, MAX_MISS_REDUCTION_LEVEL);
        this.castDelayLevel = clamp(castDelayLevel, 0, MAX_CAST_DELAY_LEVEL);
        this.reactionLevel = clamp(reactionLevel, 0, MAX_REACTION_LEVEL);
        this.durabilitySaveLevel = clamp(durabilitySaveLevel, 0, MAX_DURABILITY_SAVE_LEVEL);
        this.stopOnFullInventory = stopOnFullInventory;
        this.autoRecastOnMiss = autoRecastOnMiss;
        this.autoSellOnFullInventory = autoSellOnFullInventory;
        this.autoSwitchRod = autoSwitchRod;
        this.activePackageTier = clamp(activePackageTier, 0, MAX_PACKAGE_TIER);
        this.totalPackagesPurchased = Math.max(0, totalPackagesPurchased);
        this.firstPurchaseClaimed = firstPurchaseClaimed;
        this.totalAutoCatches = Math.max(0L, totalAutoCatches);
    }

    public static AutoFishProfile createDefault(@NotNull UUID playerId, long freeTrialSeconds) {
        AutoFishProfile profile = new AutoFishProfile(
                playerId,
                Math.max(0L, freeTrialSeconds),
                0,
                0,
                0,
                0,
                true,
                true,
                true,
                true,
                0,
                0,
                false,
                0L
        );
        profile.dirty = true;
        return profile;
    }

    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    public synchronized long getRemainingSeconds() {
        return remainingSeconds;
    }

    public synchronized void addSeconds(long secondsToAdd) {
        if (secondsToAdd <= 0L) return;
        this.remainingSeconds = Math.max(0L, this.remainingSeconds + secondsToAdd);
        this.dirty = true;
    }

    public synchronized boolean consumeOneSecond() {
        if (this.remainingSeconds <= 0L) {
            return false;
        }
        this.remainingSeconds--;
        if (this.remainingSeconds == 0L) {
            this.activePackageTier = 0;
        }
        this.dirty = true;
        return true;
    }

    public synchronized int getMissReductionLevel() {
        return missReductionLevel;
    }

    public synchronized void setMissReductionLevel(int level) {
        this.missReductionLevel = clamp(level, 0, MAX_MISS_REDUCTION_LEVEL);
        this.dirty = true;
    }

    public synchronized int getCastDelayLevel() {
        return castDelayLevel;
    }

    public synchronized void setCastDelayLevel(int level) {
        this.castDelayLevel = clamp(level, 0, MAX_CAST_DELAY_LEVEL);
        this.dirty = true;
    }

    public synchronized int getReactionLevel() {
        return reactionLevel;
    }

    public synchronized void setReactionLevel(int level) {
        this.reactionLevel = clamp(level, 0, MAX_REACTION_LEVEL);
        this.dirty = true;
    }

    public synchronized int getDurabilitySaveLevel() {
        return durabilitySaveLevel;
    }

    public synchronized void setDurabilitySaveLevel(int level) {
        this.durabilitySaveLevel = clamp(level, 0, MAX_DURABILITY_SAVE_LEVEL);
        this.dirty = true;
    }

    public synchronized boolean isStopOnFullInventory() {
        return stopOnFullInventory;
    }

    public synchronized void setStopOnFullInventory(boolean stopOnFullInventory) {
        this.stopOnFullInventory = stopOnFullInventory;
        this.dirty = true;
    }

    public synchronized boolean isAutoRecastOnMiss() {
        return autoRecastOnMiss;
    }

    public synchronized void setAutoRecastOnMiss(boolean autoRecastOnMiss) {
        this.autoRecastOnMiss = autoRecastOnMiss;
        this.dirty = true;
    }

    public synchronized boolean isAutoSellOnFullInventory() {
        return autoSellOnFullInventory;
    }

    public synchronized void setAutoSellOnFullInventory(boolean autoSellOnFullInventory) {
        this.autoSellOnFullInventory = autoSellOnFullInventory;
        this.dirty = true;
    }

    public synchronized boolean isAutoSwitchRod() {
        return autoSwitchRod;
    }

    public synchronized void setAutoSwitchRod(boolean autoSwitchRod) {
        this.autoSwitchRod = autoSwitchRod;
        this.dirty = true;
    }

    public synchronized int getActivePackageTier() {
        return remainingSeconds > 0L ? activePackageTier : 0;
    }

    public synchronized int getStoredPackageTier() {
        return activePackageTier;
    }

    public synchronized void applyPackagePurchaseTier(int packageTier) {
        int clamped = clamp(packageTier, 0, MAX_PACKAGE_TIER);
        if (this.remainingSeconds <= 0L) {
            this.activePackageTier = clamped;
        } else {
            this.activePackageTier = Math.max(this.activePackageTier, clamped);
        }
        this.totalPackagesPurchased++;
        this.dirty = true;
    }

    public synchronized int getTotalPackagesPurchased() {
        return totalPackagesPurchased;
    }

    public synchronized boolean isFirstPurchaseClaimed() {
        return firstPurchaseClaimed;
    }

    public synchronized void setFirstPurchaseClaimed(boolean firstPurchaseClaimed) {
        this.firstPurchaseClaimed = firstPurchaseClaimed;
        this.dirty = true;
    }

    /**
     * Loyalty bonus percentage (+2% extra time per previously purchased package, up to +30%).
     */
    public synchronized int getLoyaltyBonusTimePercent() {
        return Math.min(30, totalPackagesPurchased * 2);
    }

    @NotNull
    public synchronized String getActiveTierDisplayName() {
        return getTierDisplayName(getActivePackageTier());
    }

    @NotNull
    public static String getTierDisplayName(int tier) {
        return switch (clamp(tier, 0, MAX_PACKAGE_TIER)) {
            case 1 -> "<aqua>VIP Đồng (Gói 15P)</aqua>";
            case 2 -> "<green>VIP Bạc (Gói 1H)</green>";
            case 3 -> "<light_purple>VIP Vàng (Gói 3H)</light_purple>";
            case 4 -> "<gold><bold>VIP Kim Cương (Gói 8H)</bold></gold>";
            default -> "<gray>Dùng Thử / Cơ Bản</gray>";
        };
    }

    /**
     * Active rare-fish Luck bonus (0.0 .. 0.35) from VIP Auto Package tier.
     */
    public synchronized double getPackageLuckBonus() {
        return computeTierLuckBonus(getActivePackageTier());
    }

    public static double computeTierLuckBonus(int tier) {
        return switch (clamp(tier, 0, MAX_PACKAGE_TIER)) {
            case 1 -> 0.05D;
            case 2 -> 0.12D;
            case 3 -> 0.22D;
            case 4 -> 0.35D;
            default -> 0.0D;
        };
    }

    /**
     * Active bite-speed Lure bonus (0.0 .. 0.25) from VIP Auto Package tier.
     */
    public synchronized double getPackageLureBonus() {
        return computeTierLureBonus(getActivePackageTier());
    }

    public static double computeTierLureBonus(int tier) {
        return switch (clamp(tier, 0, MAX_PACKAGE_TIER)) {
            case 1 -> 0.05D;
            case 2 -> 0.10D;
            case 3 -> 0.18D;
            case 4 -> 0.25D;
            default -> 0.0D;
        };
    }

    /**
     * Active double-catch chance bonus (0.0 .. 0.20) from VIP Auto Package tier.
     */
    public synchronized double getPackageDoubleCatchBonus() {
        return computeTierDoubleCatchBonus(getActivePackageTier());
    }

    public static double computeTierDoubleCatchBonus(int tier) {
        return switch (clamp(tier, 0, MAX_PACKAGE_TIER)) {
            case 2 -> 0.05D;
            case 3 -> 0.12D;
            case 4 -> 0.20D;
            default -> 0.0D;
        };
    }

    /**
     * Active fish sell price bonus percentage (0% .. 25%) from VIP Auto Package tier.
     */
    public synchronized double getPackageSellBonusPercent() {
        return computeTierSellBonusPercent(getActivePackageTier());
    }

    public static double computeTierSellBonusPercent(int tier) {
        return switch (clamp(tier, 0, MAX_PACKAGE_TIER)) {
            case 1 -> 3.0D;
            case 2 -> 8.0D;
            case 3 -> 15.0D;
            case 4 -> 25.0D;
            default -> 0.0D;
        };
    }

    /**
     * Active skill upgrade cost discount percentage (0% .. 20%) from VIP Auto Package tier.
     */
    public synchronized int getUpgradeDiscountPercent() {
        return computeTierUpgradeDiscountPercent(getActivePackageTier());
    }

    public static int computeTierUpgradeDiscountPercent(int tier) {
        return switch (clamp(tier, 0, MAX_PACKAGE_TIER)) {
            case 2 -> 5;
            case 3 -> 12;
            case 4 -> 20;
            default -> 0;
        };
    }

    public synchronized long getTotalAutoCatches() {
        return totalAutoCatches;
    }

    public synchronized void incrementTotalAutoCatches() {
        this.totalAutoCatches++;
        this.dirty = true;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void clearDirty() {
        this.dirty = false;
    }

    /**
     * Miss chance percentage (0..100).
     * Level 0: 25.0% -> Level 10: 2.0% (-2.3% per level).
     */
    public synchronized double getMissChancePercent() {
        return computeMissChancePercent(this.missReductionLevel);
    }

    public static double computeMissChancePercent(int level) {
        int clamped = clamp(level, 0, MAX_MISS_REDUCTION_LEVEL);
        return Math.max(2.0D, Math.round((25.0D - clamped * 2.3D) * 10.0D) / 10.0D);
    }

    /**
     * Base delay between casts in seconds.
     * Level 0: 4.5s -> Level 10: 1.0s (-0.35s per level).
     */
    public synchronized double getCastDelaySeconds() {
        return computeCastDelaySeconds(this.castDelayLevel);
    }

    public static double computeCastDelaySeconds(int level) {
        int clamped = clamp(level, 0, MAX_CAST_DELAY_LEVEL);
        return Math.max(1.0D, Math.round((4.5D - clamped * 0.35D) * 100.0D) / 100.0D);
    }

    public synchronized int getBaseCastDelayTicks() {
        return Math.max(20, (int) Math.round(getCastDelaySeconds() * 20.0D));
    }

    /**
     * Mean human reaction time when a fish bites, in seconds.
     * Level 0: 0.65s -> Level 5: 0.25s (-0.08s per level).
     */
    public synchronized double getReactionSeconds() {
        return computeReactionSeconds(this.reactionLevel);
    }

    public static double computeReactionSeconds(int level) {
        int clamped = clamp(level, 0, MAX_REACTION_LEVEL);
        return Math.max(0.25D, Math.round((0.65D - clamped * 0.08D) * 100.0D) / 100.0D);
    }

    public synchronized int getBaseReactionTicks() {
        return Math.max(4, (int) Math.round(getReactionSeconds() * 20.0D));
    }

    /**
     * Chance (0..100) to preserve fishing rod durability on an auto-catch.
     * Level 0: 0% -> Level 5: 50% (+10% per level).
     */
    public synchronized double getDurabilitySavePercent() {
        return computeDurabilitySavePercent(this.durabilitySaveLevel);
    }

    public static double computeDurabilitySavePercent(int level) {
        int clamped = clamp(level, 0, MAX_DURABILITY_SAVE_LEVEL);
        return clamped * 10.0D;
    }

    public static String formatDuration(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);
        long hours = clamped / 3600L;
        long minutes = (clamped % 3600L) / 60L;
        long seconds = clamped % 60L;
        if (hours > 0L) {
            return String.format("%d giờ %02d phút %02d giây", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format("%d phút %02d giây", minutes, seconds);
        }
        return String.format("%d giây", seconds);
    }

    public static String formatCompactDuration(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);
        long hours = clamped / 3600L;
        long minutes = (clamped % 3600L) / 60L;
        long seconds = clamped % 60L;
        if (hours > 0L) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
