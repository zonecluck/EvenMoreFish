package com.oheers.fish.config.simulator;

import java.util.List;
import java.util.Objects;

/** Immutable, validated configuration. Never expose a mutable YAML Section as runtime state. */
public record SimulatorSettings(
    int revision,
    boolean enabled,
    boolean permissionRequired,
    UnavailablePolicy onUnavailable,
    Probe probe,
    List<Zone> zones,
    Ambient ambient,
    AiPreview aiPreview,
    RewardBridge rewardBridge
) {
    public SimulatorSettings {
        Objects.requireNonNull(onUnavailable, "onUnavailable");
        Objects.requireNonNull(probe, "probe");
        zones = List.copyOf(zones);
        Objects.requireNonNull(ambient, "ambient");
        Objects.requireNonNull(aiPreview, "aiPreview");
        Objects.requireNonNull(rewardBridge, "rewardBridge");
    }

    /** Compatibility for the Phase 2/3 test harness and older programmatic callers. */
    public SimulatorSettings(int revision, boolean enabled, boolean permissionRequired,
                             UnavailablePolicy onUnavailable, Probe probe, List<Zone> zones) {
        this(revision, enabled, permissionRequired, onUnavailable, probe, zones, Ambient.disabled(), AiPreview.disabled(), RewardBridge.disabled());
    }

    public SimulatorSettings(int revision, boolean enabled, boolean permissionRequired,
                             UnavailablePolicy onUnavailable, Probe probe, List<Zone> zones, Ambient ambient) {
        this(revision, enabled, permissionRequired, onUnavailable, probe, zones, ambient, AiPreview.disabled(), RewardBridge.disabled());
    }

    /** Compatibility with Phase 6/test callers not yet specifying a reward bridge. */
    public SimulatorSettings(int revision, boolean enabled, boolean permissionRequired,
                             UnavailablePolicy onUnavailable, Probe probe, List<Zone> zones,
                             Ambient ambient, AiPreview aiPreview) {
        this(revision, enabled, permissionRequired, onUnavailable, probe, zones, ambient, aiPreview, RewardBridge.disabled());
    }

    /**
     * Phase 7 reward integration is ADMIN-ONLY and explicitly gated by a runtime
     * Paper verification acknowledgement. It never turns on for ordinary players.
     */
    public record RewardBridge(boolean enabled, boolean paperGatesVerified,
                               int experienceMin, int experienceMax) {
        public static RewardBridge disabled() {return new RewardBridge(false,false,1,6);}
    }

    /** Admin-only bite/reel rehearsal; reward delivery has a separate disabled-by-default gate. */
    public record AiPreview(boolean enabled, int maxActive, int candidatesPerSearch, int maxPathNodes,
                            int searchIntervalTicks, int interestTicks, int reactionWindowTicks,
                            int graceTicks, int maxRetargets, int castCooldownTicks, int maxWaitTicks,
                            double attractionRadius, double swimSpeed, double biteRadius) {
        public static AiPreview disabled() {
            return new AiPreview(false,8,12,96,10,8,26,3,2,10,500,12.0,0.18,0.7);
        }
    }

    /** Independent, admin-only, visual preview; NEVER hooks fish events or issues rewards. */
    public record Ambient(boolean enabled, int scanRadiusChunks, int scanColumnsPerTick,
                          long scanNanosPerTick, int maxCachedChunks, int maxQueuedChunks,
                          int maxCachedCells, int minimumWaterDepth, int maxAgents,
                          int maxPerZone, int maxPrivate, int maxPrivatePerViewer,
                          int maxVisiblePerViewer, int maxPreviewViewers, int packetBudgetPerTick,
                          int spawnIntervalTicks, int movementIntervalTicks, int viewerIntervalTicks,
                          int ttlTicks, double viewDistance, double privateChance) {
        public static Ambient disabled() {
            return new Ambient(false,2,256,600_000,96,96,8192,2,
                160,48,24,2,20,8,800,20,2,5,3600,40,0.06);
        }
    }

    public enum UnavailablePolicy { LEGACY_EMF, DENY }
    public enum ZoneType { WORLD, CUBOID, WORLDGUARD_REGION, REDPROTECT_REGION }

    /** Probe intentionally does not grant fish or EXP. It only validates event ownership. */
    public record Probe(
        boolean enabled,
        int maxActive,
        int settleTicks,
        int maxSessionTicks,
        int waitTicks,
        int pollTicks,
        double maxReelDistance
    ) {}

    public record Zone(
        String id,
        boolean enabled,
        boolean denySimulator,
        ZoneType type,
        String world,
        String regionId,
        int priority,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ
    ) {
        public Zone {
            int lowX = Math.min(minX, maxX), highX = Math.max(minX, maxX);
            int lowY = Math.min(minY, maxY), highY = Math.max(minY, maxY);
            int lowZ = Math.min(minZ, maxZ), highZ = Math.max(minZ, maxZ);
            minX = lowX;
            maxX = highX;
            minY = lowY;
            maxY = highY;
            minZ = lowZ;
            maxZ = highZ;
        }
        public boolean isInWorld(String worldName) {
            return enabled && world.equalsIgnoreCase(worldName);
        }
        public boolean containsCuboid(double x, double y, double z) {
            return x >= minX && x < maxX + 1.0 &&
                   y >= minY && y < maxY + 1.0 &&
                   z >= minZ && z < maxZ + 1.0;
        }
        public double centerX() {
            return (minX + maxX + 1.0) / 2.0;
        }
        public double centerY() {
            return (minY + maxY + 1.0) / 2.0;
        }
        public double centerZ() {
            return (minZ + maxZ + 1.0) / 2.0;
        }
    }
}
