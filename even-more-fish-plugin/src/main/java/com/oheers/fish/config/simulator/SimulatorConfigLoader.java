package com.oheers.fish.config.simulator;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads simulator.yml without touching the legacy EMF config/migration version. */
public final class SimulatorConfigLoader {
    private static final Pattern SAFE_ID = Pattern.compile("[a-zA-Z0-9_-]{1,48}");
    private static final int MAX_ZONES = 256;
    private final EvenMoreFish plugin;
    private SimulatorSettings current;

    public SimulatorConfigLoader(EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    public SimulatorSettings current() { return current; }

    /** Atomic reload: failure retains previous snapshot and all existing sessions. */
    public boolean reload() {
        File file = new File(plugin.getDataFolder(), "simulator.yml");
        if (!file.isFile()) {
            plugin.saveResource("simulator.yml", false);
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file); // Unlike static loadConfiguration, this throws on malformed YAML.
            SimulatorSettings candidate = parse(yaml, current == null ? 1 : current.revision() + 1);
            current = candidate;
            plugin.getLogger().info("[Simulator] Configuration v" + candidate.revision() +
                " loaded: enabled=" + candidate.enabled() + ", zones=" + candidate.zones().size());
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException error) {
            plugin.getLogger().severe("[Simulator] Invalid simulator.yml; keeping last valid snapshot: " + error.getMessage());
            return false;
        }
    }

    static SimulatorSettings parse(YamlConfiguration yaml, int revision) {
        require(yaml.getInt("config-version", -1) == 1, "config-version must be 1");
        String mode = yaml.getString("simulator.mode", "REGIONS_ONLY");
        require("REGIONS_ONLY".equalsIgnoreCase(mode), "simulator.mode must be REGIONS_ONLY");
        String policy = yaml.getString("simulator.dependency.on-unavailable", "LEGACY_EMF");
        SimulatorSettings.UnavailablePolicy unavailable = enumValue(
            SimulatorSettings.UnavailablePolicy.class, policy, "simulator.dependency.on-unavailable");

        int maxActive = bounded(yaml, "simulator.native-hook-probe.max-active", 8, 1, 32);
        int settleTicks = bounded(yaml, "simulator.native-hook-probe.settle-ticks", 60, 2, 200);
        int maxSessionTicks = bounded(yaml, "simulator.native-hook-probe.session-timeout-ticks", 600, 20, 1200);
        int waitTicks = bounded(yaml, "simulator.native-hook-probe.wait-time-ticks", 1000000, 10000, 2000000);
        int pollTicks = bounded(yaml, "simulator.native-hook-probe.poll-interval-ticks", 2, 1, 20);
        double maxDistance = yaml.getDouble("simulator.native-hook-probe.max-reel-distance", 35.0);
        require(Double.isFinite(maxDistance) && maxDistance >= 4 && maxDistance <= 64,
            "simulator.native-hook-probe.max-reel-distance must be 4..64");
        SimulatorSettings.Probe probe = new SimulatorSettings.Probe(
            yaml.getBoolean("simulator.native-hook-probe.enabled", false),
            maxActive, settleTicks, maxSessionTicks, waitTicks, pollTicks, maxDistance);

        // All budgets are validated even while disabled: invalid reload never replaces the last-good snapshot.
        SimulatorSettings.Ambient ambient = new SimulatorSettings.Ambient(
            yaml.getBoolean("simulator.ambient-preview.enabled", false),
            bounded(yaml, "simulator.ambient-preview.scan-radius-chunks", 2, 0, 4),
            bounded(yaml, "simulator.ambient-preview.scan-columns-per-tick", 256, 1, 2048),
            bounded(yaml, "simulator.ambient-preview.scan-nanos-per-tick", 600000, 100000, 2000000),
            bounded(yaml, "simulator.ambient-preview.max-cached-chunks", 96, 8, 512),
            bounded(yaml, "simulator.ambient-preview.max-queued-chunks", 96, 8, 512),
            bounded(yaml, "simulator.ambient-preview.max-cached-cells", 8192, 256, 65536),
            bounded(yaml, "simulator.ambient-preview.minimum-water-depth", 2, 1, 8),
            bounded(yaml, "simulator.ambient-preview.max-agents", 160, 1, 240),
            bounded(yaml, "simulator.ambient-preview.max-per-zone", 48, 1, 72),
            bounded(yaml, "simulator.ambient-preview.max-private", 24, 0, 64),
            bounded(yaml, "simulator.ambient-preview.max-private-per-viewer", 2, 0, 4),
            bounded(yaml, "simulator.ambient-preview.max-visible-per-viewer", 20, 1, 32),
            bounded(yaml, "simulator.ambient-preview.max-preview-viewers", 8, 1, 32),
            bounded(yaml, "simulator.ambient-preview.packets-per-tick", 800, 64, 1200),
            bounded(yaml, "simulator.ambient-preview.spawn-interval-ticks", 20, 2, 200),
            bounded(yaml, "simulator.ambient-preview.movement-interval-ticks", 2, 1, 20),
            bounded(yaml, "simulator.ambient-preview.viewer-interval-ticks", 5, 1, 40),
            bounded(yaml, "simulator.ambient-preview.ttl-ticks", 3600, 100, 20000),
            between(yaml,"simulator.ambient-preview.view-distance",40.0,8,64),
            between(yaml,"simulator.ambient-preview.private-chance",.06,0,1));
        require(ambient.maxPerZone() <= ambient.maxAgents(), "ambient-preview.max-per-zone exceeds max-agents");
        require(ambient.maxPrivate() <= ambient.maxAgents(), "ambient-preview.max-private exceeds max-agents");

        SimulatorSettings.AiPreview ai = new SimulatorSettings.AiPreview(
            yaml.getBoolean("simulator.ai-preview.enabled", false),
            bounded(yaml,"simulator.ai-preview.max-active",8,1,16),
            bounded(yaml,"simulator.ai-preview.candidates-per-search",12,1,64),
            bounded(yaml,"simulator.ai-preview.max-path-nodes",96,4,512),
            bounded(yaml,"simulator.ai-preview.search-interval-ticks",10,2,100),
            bounded(yaml,"simulator.ai-preview.interest-ticks",8,2,100),
            bounded(yaml,"simulator.ai-preview.reaction-window-ticks",26,4,100),
            bounded(yaml,"simulator.ai-preview.grace-ticks",3,0,20),
            bounded(yaml,"simulator.ai-preview.max-retargets",2,0,5),
            bounded(yaml,"simulator.ai-preview.cast-cooldown-ticks",10,0,200),
            bounded(yaml,"simulator.ai-preview.max-wait-ticks",500,100,1200),
            between(yaml,"simulator.ai-preview.attraction-radius",12.0,2,24),
            between(yaml,"simulator.ai-preview.swim-speed",.18,.04,.8),
            between(yaml,"simulator.ai-preview.bite-radius",.7,.25,2.0));
        require(ai.maxWaitTicks() > ai.reactionWindowTicks()+ai.graceTicks(),
            "simulator.ai-preview.max-wait-ticks must exceed bite reaction window");
        require(!ai.enabled() || (probe.enabled() && ambient.enabled()),
            "simulator.ai-preview.enabled requires native-hook-probe.enabled and ambient-preview.enabled");
        require(ai.maxActive() <= probe.maxActive(),
            "simulator.ai-preview.max-active must not exceed native-hook-probe.max-active");
        require(!ai.enabled() || ai.maxWaitTicks() <= probe.maxSessionTicks(),
            "simulator.ai-preview.max-wait-ticks must not exceed native-hook-probe.session-timeout-ticks");

        SimulatorSettings.RewardBridge rewards = new SimulatorSettings.RewardBridge(
            yaml.getBoolean("simulator.reward-bridge.enabled",false),
            yaml.getBoolean("simulator.reward-bridge.paper-gates-verified",false),
            bounded(yaml,"simulator.reward-bridge.experience-min",1,0,100),
            bounded(yaml,"simulator.reward-bridge.experience-max",6,0,100));
        require(rewards.experienceMin()<=rewards.experienceMax(),
            "simulator.reward-bridge.experience-min cannot exceed experience-max");
        require(!rewards.enabled() || rewards.paperGatesVerified(),
            "simulator.reward-bridge.enabled requires paper-gates-verified: true (admin-only Paper test!)");
        require(!rewards.enabled() || ai.enabled(),
            "simulator.reward-bridge.enabled requires ai-preview.enabled: true");

        ConfigurationSection zoneSection = yaml.getConfigurationSection("zones");
        List<SimulatorSettings.Zone> zones = new ArrayList<>();
        Set<String> normalizedIds = new HashSet<>();
        if (zoneSection != null) {
            require(zoneSection.getKeys(false).size() <= MAX_ZONES, "zones: at most " + MAX_ZONES + " permitted");
            for (String id : zoneSection.getKeys(false)) {
                require(SAFE_ID.matcher(id).matches(), "zones." + id + ": invalid zone ID");
                require(normalizedIds.add(id.toLowerCase(Locale.ROOT)), "zones." + id + ": duplicate zone ID");
                ConfigurationSection section = zoneSection.getConfigurationSection(id);
                require(section != null, "zones." + id + ": expected section");
                String path = "zones." + id;
                boolean enabled = section.getBoolean("enabled", false);
                int priority = section.getInt("priority", 0);
                require(priority >= -1000 && priority <= 1000, path + ".priority must be -1000..1000");
                SimulatorSettings.ZoneType type = enumValue(SimulatorSettings.ZoneType.class,
                    section.getString("type", "WORLD"), path + ".type");
                String world = section.getString("world", "").trim();
                // Multi-world plugins can load valid configured worlds after EMF enables.
                // Runtime locations and holograms already wait for the actual world/chunk.
                require(!enabled || (section.isString("world") && !world.isBlank()),
                    path + ".world must be a nonblank world name");
                String regionId = section.getString("region-id", "").trim();
                require(!enabled || (type != SimulatorSettings.ZoneType.WORLDGUARD_REGION &&
                    type != SimulatorSettings.ZoneType.REDPROTECT_REGION) || !regionId.isBlank(),
                    path + ".region-id required for provider zone");
                int rawMinX = section.getInt("min.x", 0), rawMinY = section.getInt("min.y", 0), rawMinZ = section.getInt("min.z", 0);
                int rawMaxX = section.getInt("max.x", 0), rawMaxY = section.getInt("max.y", 0), rawMaxZ = section.getInt("max.z", 0);
                int minX = Math.min(rawMinX, rawMaxX), minY = Math.min(rawMinY, rawMaxY), minZ = Math.min(rawMinZ, rawMaxZ);
                int maxX = Math.max(rawMinX, rawMaxX), maxY = Math.max(rawMinY, rawMaxY), maxZ = Math.max(rawMinZ, rawMaxZ);
                if (enabled && type == SimulatorSettings.ZoneType.CUBOID) {
                    require(section.isInt("min.x") && section.isInt("min.y") && section.isInt("min.z") &&
                        section.isInt("max.x") && section.isInt("max.y") && section.isInt("max.z"),
                        path + ": CUBOID needs all 6 integer bounds");
                    require(minY >= -64 && maxY <= 320, path + ": Y must be -64..320");
                    require(Math.abs((long) minX) <= 30000000 && Math.abs((long) maxX) <= 30000000 &&
                        Math.abs((long) minZ) <= 30000000 && Math.abs((long) maxZ) <= 30000000,
                        path + ": coordinates exceed Minecraft world bounds");
                }
                zones.add(new SimulatorSettings.Zone(id, enabled, section.getBoolean("deny-simulator", false),
                    type, world, regionId, priority, minX, minY, minZ, maxX, maxY, maxZ));
            }
        }
        return new SimulatorSettings(revision,
            yaml.getBoolean("simulator.enabled", false),
            yaml.getBoolean("simulator.policy.permission-required", false), unavailable, probe, zones, ambient, ai, rewards);
    }

    private static double between(YamlConfiguration yaml,String key,double def,double min,double max) {
        require(!yaml.contains(key) || yaml.isDouble(key) || yaml.isInt(key), key + " must be a number");
        double value=yaml.getDouble(key,def);
        require(Double.isFinite(value) && value>=min && value<=max,key+" must be "+min+".."+max);
        return value;
    }
    private static int bounded(YamlConfiguration yaml, String key, int def, int min, int max) {
        require(!yaml.contains(key) || yaml.isInt(key), key + " must be an integer");
        int number = yaml.getInt(key, def);
        require(number >= min && number <= max, key + " must be " + min + ".." + max);
        return number;
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String key) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(key + ": unsupported value " + value);
        }
    }
    private static void require(boolean ok, String error) {
        if (!ok) throw new IllegalArgumentException(error);
    }
}
