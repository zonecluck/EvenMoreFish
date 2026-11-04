package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Style;
import com.oheers.fish.fishing.simulator.population.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/** Paper owner thread only; both renderers share this ceiling in actual server ticks. */
public final class CinematicParticleEffects {
    public static final int MAX_PARTICLES_PER_TICK = 24;
    private static final CinematicParticleBudget BUDGET = new CinematicParticleBudget(MAX_PARTICLES_PER_TICK);
    private static final Particle DUST = particle("DUST", "REDSTONE");
    private static final Particle SPARKLE = particle("ENCHANT", "ENCHANTMENT_TABLE");
    private static final Particle BUBBLE = particle("BUBBLE_POP", "WATER_BUBBLE");
    private static final Particle.DustOptions EPIC_DUST = new Particle.DustOptions(Color.fromRGB(190, 94, 255), .8f);
    private static final Particle.DustOptions LEGENDARY_DUST = new Particle.DustOptions(Color.fromRGB(255, 208, 92), .8f);
    public record Emission(int consumed, Throwable failure) {}
    private CinematicParticleEffects() {}

    public static int reserve(int wanted) {
        return BUDGET.reserve(Bukkit.getCurrentTick(), wanted);
    }

    public static Emission emit(Player viewer, Style style, Stage stage, Vec3 water, Vec3 fish,
                                long elapsed, double progress, int allowance) {
        var points = CinematicParticlePattern.points(style, stage, water, fish, elapsed, progress, allowance);
        if (points.isEmpty()) return new Emission(0, null);
        int allowed = reserve(points.size()); // Failed sends retain their reservation.
        if (allowed == 0) return new Emission(0, null);
        try {
            var options = style == Style.LEGENDARY ? LEGENDARY_DUST : EPIC_DUST;
            for (int i = 0; i < allowed; i++) {
                Vec3 point = points.get(i);
                Location location = new Location(viewer.getWorld(), point.x(), point.y(), point.z());
                boolean waterStage = stage == Stage.ANTICIPATION || stage == Stage.VORTEX;
                if (waterStage && i % 3 == 0 && BUBBLE != null)
                    viewer.spawnParticle(BUBBLE, location, 1, 0, 0, 0, 0);
                else if (DUST != null)
                    viewer.spawnParticle(DUST, location, 1, 0, 0, 0, 0, options);
                else if (SPARKLE != null)
                    viewer.spawnParticle(SPARKLE, location, 1, 0, 0, 0, 0);
            }
            return new Emission(allowed, null);
        } catch (RuntimeException | LinkageError failure) {
            return new Emission(allowed, failure);
        }
    }

    private static Particle particle(String... names) {
        for (String name : names) {
            try { return Particle.valueOf(name); } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }
}
