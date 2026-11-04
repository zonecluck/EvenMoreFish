package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import com.oheers.fish.fishing.simulator.population.FishKey;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Immutable and completely validated scene configuration. */
public record PremiumCinematicSettings(boolean enabled, int maxParticleEmissionsPerTick,
        Map<String,Profile> presets, Map<String,String> rarityPresets, Map<String,String> fishOverrides) {
    public PremiumCinematicSettings {
        presets = Map.copyOf(presets);
        rarityPresets = Map.copyOf(rarityPresets);
        fishOverrides = Map.copyOf(fishOverrides);
    }
    public static PremiumCinematicSettings disabled() {
        return new PremiumCinematicSettings(false, 6, Map.of(), Map.of(), Map.of());
    }
    public Profile profile(FishKey key) {
        String exact = key.rarityId().toLowerCase(Locale.ROOT) + ":" + key.fishId().toLowerCase(Locale.ROOT);
        String tier = fishOverrides.getOrDefault(exact,
            rarityPresets.getOrDefault(key.rarityId().toLowerCase(Locale.ROOT), "common"));
        return presets.getOrDefault(tier, presets.get("common"));
    }
    public Profile preview(String id) { return presets.get(id.toLowerCase(Locale.ROOT)); }
    /** Preview fish keys are synthetic; the resolved preset owns the visual style. */
    public PremiumCinematicTimeline.Style style(Profile profile) {
        if (profile != null && profile == presets.get("legendary")) return PremiumCinematicTimeline.Style.LEGENDARY;
        if (profile != null && profile == presets.get("epic")) return PremiumCinematicTimeline.Style.EPIC;
        return PremiumCinematicTimeline.Style.STANDARD;
    }
    public record Cue(@Nullable Sound.Type sound, float volume, float pitch,
                      @Nullable Particle particle, int count, double radius) {}
    public record Profile(PremiumCinematicTimeline.Durations durations, double arcHeight, double orbit,
                          int trailInterval, @Nullable Particle trail, Map<Stage,Cue> cues) {
        public Profile {
            cues = Map.copyOf(cues);
        }
        public Cue cue(Stage stage) { return cues.get(stage); }
    }
}
