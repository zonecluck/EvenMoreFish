package com.oheers.fish.fishing.simulator.environment;

import com.oheers.fish.fishing.simulator.population.WaterPoint;
import java.util.Objects;

/** Coarse, read-only water observation; does not decide whether an EMF fish is eligible. */
public record EnvironmentSnapshot(WaterPoint cell, Depth depth, TimeOfDay time, Weather weather,
                                  String biome, int waterBlocks, long sampledAtTick) {
    public enum Depth { SHALLOW, MEDIUM, DEEP }
    public enum TimeOfDay { DAY, NIGHT }
    public enum Weather { CLEAR, RAIN, THUNDER }

    public EnvironmentSnapshot {
        Objects.requireNonNull(cell);
        Objects.requireNonNull(depth);
        Objects.requireNonNull(time);
        Objects.requireNonNull(weather);
        Objects.requireNonNull(biome);
        if (waterBlocks < 1 || waterBlocks > 32) throw new IllegalArgumentException("waterBlocks");
    }
    public static Depth depthFor(int blocks) {
        return blocks >= 6 ? Depth.DEEP : blocks >= 3 ? Depth.MEDIUM : Depth.SHALLOW;
    }
}
