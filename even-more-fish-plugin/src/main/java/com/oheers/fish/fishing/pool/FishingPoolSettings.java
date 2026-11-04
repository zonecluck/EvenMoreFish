package com.oheers.fish.fishing.pool;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record FishingPoolSettings(boolean enabled, double verticalTolerance, double defaultSpeed,
                           double defaultMultiplier, boolean particles, int ringPoints,
                           double viewerDistance, Set<String> rareIds, Map<String, FishingPool> pools) {
    static final int MAX_POOLS = 256;

    static FishingPoolSettings load(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("fishing-pools");
        if (root == null) throw new IllegalArgumentException("Missing fishing-pools section");
        boolean enabled = bool(root, "enabled", true);
        double tolerance = number(root, "vertical-tolerance", 2, 0.25, 16);
        double speed = number(root, "default-speed-percent", 25, 0, 85);
        double multiplier = number(root, "default-rare-multiplier", 1.5, 1, 10);
        boolean particles = bool(root, "particles.enabled", true);
        int points = (int) number(root, "particles.ring-points", 32, 8, 64);
        double distance = number(root, "particles.viewer-distance", 48, 8, 96);
        Object rawIds = root.get("rare-rarity-ids", List.of("Uncommon", "Rare", "Epic", "Legendary", "Mythical"));
        if (!(rawIds instanceof List<?> ids)) throw new IllegalArgumentException("rare-rarity-ids must be a list");
        Set<String> rareIds = new LinkedHashSet<>();
        for (Object id : ids) {
            if (!(id instanceof String name) || name.isBlank())
                throw new IllegalArgumentException("rare-rarity-ids must contain nonempty strings");
            if (!name.equalsIgnoreCase("Common")) rareIds.add(name.toLowerCase(Locale.ROOT));
        }
        Map<String, FishingPool> pools = new LinkedHashMap<>();
        if (root.contains("pools") && !root.isConfigurationSection("pools"))
            throw new IllegalArgumentException("pools must be a section");
        ConfigurationSection section = root.getConfigurationSection("pools");
        if (section != null) {
            if (section.getKeys(false).size() > MAX_POOLS) throw new IllegalArgumentException("At most 256 pools are supported");
            for (String id : section.getKeys(false)) {
                ConfigurationSection pool = section.getConfigurationSection(id);
                if (pool == null) throw new IllegalArgumentException("Invalid pool " + id);
                Object world = pool.get("world");
                if (!(world instanceof String name)) throw new IllegalArgumentException("Missing world for " + id);
                FishingPool value = new FishingPool(id, name, requiredNumber(pool, "x"), requiredNumber(pool, "y"),
                    requiredNumber(pool, "z"), requiredNumber(pool, "radius"),
                    number(pool, "speed-percent", speed, 0, 85), number(pool, "rare-multiplier", multiplier, 1, 10));
                if (pools.putIfAbsent(value.id(), value) != null)
                    throw new IllegalArgumentException("Duplicate case-insensitive pool ID " + id);
            }
        }
        return new FishingPoolSettings(enabled, tolerance, speed, multiplier, particles, points,
            distance, Set.copyOf(rareIds), Map.copyOf(pools));
    }

    boolean boosts(String rarity) {
        return rarity != null && !rarity.equalsIgnoreCase("Common") && rareIds.contains(rarity.toLowerCase(Locale.ROOT));
    }

    static void removeDefinition(YamlConfiguration yaml, String id) {
        ConfigurationSection pools = yaml.getConfigurationSection("fishing-pools.pools");
        if (pools == null) return;
        for (String originalKey : pools.getKeys(false)) {
            if (originalKey.equalsIgnoreCase(id)) {
                pools.set(originalKey, null);
                return;
            }
        }
    }

    private static boolean bool(ConfigurationSection section, String key, boolean fallback) {
        Object raw = section.get(key, fallback);
        if (!(raw instanceof Boolean value)) throw new IllegalArgumentException(key + " must be a boolean");
        return value;
    }

    private static double requiredNumber(ConfigurationSection section, String key) {
        Object raw = section.get(key);
        if (!(raw instanceof Number value) || !Double.isFinite(value.doubleValue()))
            throw new IllegalArgumentException(key + " must be a finite number");
        return value.doubleValue();
    }

    private static double number(ConfigurationSection section, String key, double fallback, double min, double max) {
        return FishingPool.bounded(section.contains(key) ? requiredNumber(section, key) : fallback, min, max, key);
    }
}
