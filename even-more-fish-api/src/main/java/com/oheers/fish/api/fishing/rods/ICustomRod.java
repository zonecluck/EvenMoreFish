package com.oheers.fish.api.fishing.rods;

import com.oheers.fish.api.fishing.items.IFish;
import com.oheers.fish.api.fishing.items.IRarity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface ICustomRod {

    @NotNull String getId();

    boolean isDisabled();

    /**
     * Creates an ItemStack of this rod, with the necessary NBT to identify it.
     */
    @NotNull ItemStack create();

    @NotNull List<? extends IRarity> getAllowedRarities();

    @NotNull List<? extends IFish> getAllowedFish();

    /**
     * Additional luck modifier when fishing with this rod.
     * Value 0.5 means +50% luck towards rarer fish.
     */
    default double getLuckModifier() {
        return 0.0;
    }

    /**
     * Lure modifier (wait time reduction percentage) when using this rod.
     * Value 0.2 means 20% faster bite time.
     */
    default double getLureModifier() {
        return 0.0;
    }

    /**
     * Chance (0.0 to 1.0) to catch double fish with this rod.
     */
    default double getDoubleCatchChance() {
        return 0.0;
    }

    /**
     * Experience multiplier when catching fish with this rod (default 1.0).
     */
    default double getExpMultiplier() {
        return 1.0;
    }

    /**
     * Multipliers for specific rarity weights.
     */
    default @NotNull Map<String, Double> getRarityMultipliers() {
        return Map.of();
    }

    /**
     * Whether baits can be applied to this custom rod.
     */
    default boolean isBaitAllowed() {
        return true;
    }

    /**
     * Whether the rod is unbreakable.
     */
    default boolean isUnbreakable() {
        return false;
    }

    /**
     * Optional particle effect name for this rod.
     */
    default @Nullable String getParticleEffect() {
        return null;
    }

}
