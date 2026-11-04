package com.oheers.fish.fishing.simulator.population;

import java.util.UUID;

/** Immutable coordinates, deliberately independent of Bukkit Location/Chunk objects. */
public record WaterPoint(UUID worldId, int x, int y, int z) {
    public int chunkX() { return x >> 4; }
    public int chunkZ() { return z >> 4; }
    public Vec3 swimmingPosition() { return new Vec3(x + .5, y + .32, z + .5); }
}
