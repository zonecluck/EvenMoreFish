package com.oheers.fish.fishing.simulator.effects;

import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;
import java.util.Map;

/** Validated snapshot. Never hand YAML sections to the hot path. */
public record SimulatorEffectsSettings(
    boolean enabled,
    boolean hudEnabled, int hudIntervalTicks, int hudSegments,
    String filledGlyph, String emptyGlyph,
    boolean cinematicEnabled, int maxConcurrentCinematics, int cinematicPacketBudgetPerTick,
    double cinematicMaxDistance,
    Map<String, Preset> presets, Map<String, String> rarityMapping, Map<String, String> fishOverrides
) {
    public SimulatorEffectsSettings {
        presets = Map.copyOf(presets);
        rarityMapping = Map.copyOf(rarityMapping);
        fishOverrides = Map.copyOf(fishOverrides);
    }
    public static SimulatorEffectsSettings disabled() {
        return new SimulatorEffectsSettings(false, false, 5, 12, "▰", "▱", false,
            4, 12, 32, Map.of(), Map.of(), Map.of());
    }
    public Preset forFish(String rarityId, String fishId) {
        String key = (rarityId + ":" + fishId).toLowerCase(Locale.ROOT);
        String preset = fishOverrides.getOrDefault(key,
            rarityMapping.getOrDefault(rarityId.toLowerCase(Locale.ROOT), "common"));
        return presets.getOrDefault(preset, presets.get("common"));
    }
    public record Cue(@Nullable Sound.Type sound, float volume, float pitch,
                      @Nullable Particle particle, int count) {}
    public record Preset(Cue interest, Cue bite, Cue success, Cue failure,
                         int cinematicDurationTicks, double cinematicArcHeight) {}
}
