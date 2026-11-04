package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses a candidate in isolation, and refuses an invalid reload without touching live scenes. */
public final class PremiumCinematicConfigLoader {
    private static final Set<String> PRESET_IDS = Set.of("common", "rare", "epic", "legendary");
    private static final Set<String> PARTICLES = Set.of("WATER_SPLASH", "SPLASH", "BUBBLE_POP",
        "WATER_BUBBLE", "CLOUD", "CRIT", "ENCHANT", "ENCHANTMENT_TABLE", "HAPPY_VILLAGER", "VILLAGER_HAPPY");
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9 _:-]{1,96}");
    private final EvenMoreFish plugin;
    public PremiumCinematicConfigLoader(EvenMoreFish plugin) { this.plugin = plugin; }

    public PremiumCinematicSettings readCandidate() {
        File file = new File(plugin.getDataFolder(), "simulator/cinematics.yml");
        try {
            if (!file.isFile()) plugin.saveResource("simulator/cinematics.yml", false);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            return parse(yaml);
        } catch (IOException | InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().severe("[Simulator] Invalid simulator/cinematics.yml; keeping old snapshot: " + error.getMessage());
            return null;
        }
    }

    public static PremiumCinematicSettings parse(YamlConfiguration yaml) {
        require(yaml.isInt("config-version") && yaml.getInt("config-version") == 1,
            "config-version must equal 1");
        require(yaml.isBoolean("enabled"), "enabled must be boolean");
        int maxFx = number(yaml, "limits.particle-emissions-per-tick", 8, 0, 24);
        var section = yaml.getConfigurationSection("presets");
        require(section != null && section.getKeys(false).equals(PRESET_IDS),
            "presets must contain exactly common, rare, epic and legendary");
        Map<String,PremiumCinematicSettings.Profile> profiles = new HashMap<>();
        for (String id : PRESET_IDS) {
            String base = "presets." + id;
            var durations = new PremiumCinematicTimeline.Durations(
                number(yaml, base + ".stages.anticipation", 0, 0, 30),
                number(yaml, base + ".stages.vortex", 0, 0, 30),
                number(yaml, base + ".stages.surface", 8, 2, 40),
                number(yaml, base + ".stages.reveal", 0, 0, 30),
                number(yaml, base + ".stages.delivery", 6, 2, 40),
                number(yaml, base + ".stages.fade", 2, 1, 20));
            double arc = decimal(yaml, base + ".arc-height", 1, 0, 3);
            double orbit = decimal(yaml, base + ".orbit-width", .3, 0, 1.5);
            int interval = number(yaml, base + ".trail.interval-ticks", 5, 1, 20);
            Particle trail = particle(yaml.getString(base + ".trail.particle", "NONE"), base + ".trail.particle");
            var cues = new EnumMap<Stage,PremiumCinematicSettings.Cue>(Stage.class);
            for (Stage stage : Stage.values()) cues.put(stage, cue(yaml, base + ".cues." + stage.name().toLowerCase(Locale.ROOT)));
            profiles.put(id, new PremiumCinematicSettings.Profile(durations, arc, orbit, interval, trail, cues));
        }
        return new PremiumCinematicSettings(yaml.getBoolean("enabled"), maxFx, Map.copyOf(profiles),
            readMap(yaml, "rarity-presets", profiles), readMap(yaml, "fish-overrides", profiles));
    }

    private static PremiumCinematicSettings.Cue cue(YamlConfiguration y, String base) {
        var sec = y.getConfigurationSection(base);
        require(sec != null, base + " is missing");
        String name = y.getString(base + ".sound", "NONE").toUpperCase(Locale.ROOT);
        Sound.Type sound = null;
        if (!name.equals("NONE")) {
            sound = FishUtils.getSound(name);
            require(sound != null, base + ".sound invalid: " + name);
        }
        return new PremiumCinematicSettings.Cue(sound,
            (float)decimal(y, base + ".volume", .7, 0, 2),
            (float)decimal(y, base + ".pitch", 1, .5, 2),
            particle(y.getString(base + ".particle", "NONE"), base + ".particle"),
            number(y, base + ".count", 0, 0, 16),
            decimal(y, base + ".radius", .6, 0, 2));
    }
    private static Particle particle(String raw, String key) {
        require(raw != null, key + " cannot be null");
        String name = raw.toUpperCase(Locale.ROOT);
        if (name.equals("NONE")) return null;
        require(PARTICLES.contains(name), key + " must be a simple particle");
        String alternative = switch(name) {
            case "SPLASH" -> "WATER_SPLASH";
            case "BUBBLE_POP" -> "WATER_BUBBLE";
            case "ENCHANT" -> "ENCHANTMENT_TABLE";
            case "HAPPY_VILLAGER" -> "VILLAGER_HAPPY";
            default -> name;
        };
        try { return Particle.valueOf(name); }
        catch (IllegalArgumentException ignored) {
            try { return Particle.valueOf(alternative); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(key + " unsupported on this Paper version: " + name); }
        }
    }
    private static Map<String,String> readMap(YamlConfiguration y, String path,
            Map<String,PremiumCinematicSettings.Profile> profiles) {
        ConfigurationSection sec = y.getConfigurationSection(path);
        if (sec == null) return Map.of();
        require(sec.getKeys(false).size() <= 256, path + " has too many entries");
        Map<String,String> entries = new HashMap<>();
        for (String key : sec.getKeys(false)) {
            require(SAFE_KEY.matcher(key).matches(), path + " invalid id: " + key);
            String value = sec.getString(key);
            require(value != null && profiles.containsKey(value.toLowerCase(Locale.ROOT)),
                path + "." + key + " references an unknown preset");
            require(entries.putIfAbsent(key.toLowerCase(Locale.ROOT), value.toLowerCase(Locale.ROOT)) == null,
                path + " has duplicate case-insensitive key: " + key);
        }
        return Map.copyOf(entries);
    }
    private static int number(YamlConfiguration y, String key, int fallback, int min, int max) {
        require(y.isInt(key), key + " must be integer");
        int result = y.getInt(key, fallback);
        require(result >= min && result <= max, key + " must be " + min + ".." + max);
        return result;
    }
    private static double decimal(YamlConfiguration y, String key, double fallback, double min, double max) {
        require(y.isInt(key) || y.isDouble(key), key + " must be numeric");
        double value = y.getDouble(key, fallback);
        require(Double.isFinite(value) && value >= min && value <= max, key + " must be " + min + ".." + max);
        return value;
    }
    private static void require(boolean good, String message) { if (!good) throw new IllegalArgumentException(message); }
}
