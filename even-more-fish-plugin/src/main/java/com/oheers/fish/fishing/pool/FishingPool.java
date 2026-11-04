package com.oheers.fish.fishing.pool;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/** Persistent geometry; worlds stay names so unloaded worlds need not be loaded. */
public record FishingPool(String id, String world, double x, double y, double z,
                          double radius, double speedPercent, double rareMultiplier) {
    public FishingPool {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("Pool ID must use 1-48 letters, digits, _ or -");
        id = id.toLowerCase(Locale.ROOT);
        if (world == null || world.isBlank()) throw new IllegalArgumentException("Missing world");
        finite(x, "x"); finite(y, "y"); finite(z, "z");
        radius = bounded(radius, 1, 64, "radius");
        speedPercent = bounded(speedPercent, 0, 85, "speed-percent");
        rareMultiplier = bounded(rareMultiplier, 1, 10, "rare-multiplier");
    }

    static double bounded(double value, double min, double max, String field) {
        finite(value, field);
        return Math.max(min, Math.min(max, value));
    }

    private static void finite(double value, String field) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    public double distanceSquared(double atX, double atZ) {
        double dx = atX - x, dz = atZ - z;
        return dx * dx + dz * dz;
    }

    public boolean contains(String atWorld, double atX, double atY, double atZ, double verticalTolerance) {
        return world.equals(atWorld) && Double.isFinite(atX) && Double.isFinite(atY) && Double.isFinite(atZ)
            && Math.abs(atY - y) <= verticalTolerance && distanceSquared(atX, atZ) <= radius * radius;
    }

    public static FishingPool select(Collection<FishingPool> pools, String world, double x, double y,
                                     double z, double verticalTolerance) {
        return pools.stream().filter(pool -> pool.contains(world, x, y, z, verticalTolerance))
            .min(Comparator.comparingDouble((FishingPool pool) -> pool.distanceSquared(x, z))
                .thenComparing(FishingPool::id)).orElse(null);
    }

    /** Invalid or zero weights remain ineligible, regardless of configured bonuses. */
    public static double applyWeight(double weight, double multiplier) {
        if (!Double.isFinite(weight) || weight <= 0) return 0;
        if (!Double.isFinite(multiplier) || multiplier < 1 || multiplier > 10) return weight;
        return weight > Double.MAX_VALUE / multiplier ? Double.MAX_VALUE : weight * multiplier;
    }
}
