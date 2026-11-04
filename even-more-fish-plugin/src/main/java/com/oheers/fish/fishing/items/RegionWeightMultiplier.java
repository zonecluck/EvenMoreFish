package com.oheers.fish.fishing.items;

/**
 * Region boosts are real-valued weight multipliers, not list repetition counts.
 * A 0 boost excludes the rarity; invalid multipliers are treated as disabled.
 */
public final class RegionWeightMultiplier {
    private RegionWeightMultiplier() {}

    public static double apply(double weight, double multiplier) {
        if (!Double.isFinite(weight) || weight <= 0 ||
            !Double.isFinite(multiplier) || multiplier <= 0) return 0.0;
        double result = weight * multiplier;
        return Double.isFinite(result) && result >= 0 ? result : 0.0;
    }
}
