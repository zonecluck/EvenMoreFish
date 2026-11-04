package com.oheers.fish.api.fishing;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Optional owner-thread Bukkit service for a player's ordinary native fishing lake. */
public interface FishingLakeProvider {
    /** Return null when the actual hook location or player is not eligible. */
    @Nullable Bonus resolve(Player player, Location hookLocation);

    /** One lake's bounded bonus; consumers must never stack it with static pool bonuses. */
    record Bonus(String id, double speedPercent, double rareMultiplier, boolean hookedVisual) {
        public Bonus {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Lake ID must not be blank");
            if (!Double.isFinite(speedPercent) || speedPercent < 0 || speedPercent > 85)
                throw new IllegalArgumentException("Lake speed must be finite and between 0 and 85");
            if (!Double.isFinite(rareMultiplier) || rareMultiplier < 1 || rareMultiplier > 10)
                throw new IllegalArgumentException("Lake rarity multiplier must be finite and between 1 and 10");
        }
    }
}
