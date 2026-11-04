package com.oheers.fish.autofish;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe persistent store for player Auto-Fishing profiles.
 * Backed by plugins/EvenMoreFish/autofish-data.yml with periodic async saving.
 */
public final class AutoFishDataStore {

    private static final long DEFAULT_FREE_TRIAL_SECONDS = 300L; // 5 minutes starter trial

    private final EvenMoreFish plugin;
    private final File dataFile;
    private final File tempFile;
    private final Map<UUID, AutoFishProfile> profiles = new ConcurrentHashMap<>();

    public AutoFishDataStore(@NotNull EvenMoreFish plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "autofish-data.yml");
        this.tempFile = new File(plugin.getDataFolder(), "autofish-data.yml.tmp");
        loadAll();
    }

    @NotNull
    public AutoFishProfile getOrCreate(@NotNull UUID playerId) {
        return profiles.computeIfAbsent(playerId, id -> AutoFishProfile.createDefault(id, DEFAULT_FREE_TRIAL_SECONDS));
    }

    public synchronized void loadAll() {
        if (!dataFile.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection playersSection = yaml.getConfigurationSection("players");
            if (playersSection == null) {
                return;
            }
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection sec = playersSection.getConfigurationSection(key);
                    if (sec == null) continue;
                    AutoFishProfile profile = new AutoFishProfile(
                            uuid,
                            sec.getLong("remaining-seconds", 0L),
                            sec.getInt("miss-reduction-level", 0),
                            sec.getInt("cast-delay-level", 0),
                            sec.getInt("reaction-level", 0),
                            sec.getInt("durability-save-level", 0),
                            sec.getBoolean("stop-on-full-inventory", true),
                            sec.getBoolean("auto-recast-on-miss", true),
                            sec.getBoolean("auto-sell-on-full-inventory", true),
                            sec.getBoolean("auto-switch-rod", true),
                            sec.getInt("active-package-tier", 0),
                            sec.getInt("total-packages-purchased", 0),
                            sec.getBoolean("first-purchase-claimed", false),
                            sec.getLong("total-auto-catches", 0L)
                    );
                    profiles.put(uuid, profile);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (RuntimeException error) {
            plugin.getLogger().warning("[AutoFish] Failed to load autofish-data.yml: " + error.getMessage());
        }
    }

    public synchronized void saveAllSync() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, AutoFishProfile> entry : profiles.entrySet()) {
                AutoFishProfile p = entry.getValue();
                String path = "players." + entry.getKey() + ".";
                synchronized (p) {
                    yaml.set(path + "remaining-seconds", p.getRemainingSeconds());
                    yaml.set(path + "miss-reduction-level", p.getMissReductionLevel());
                    yaml.set(path + "cast-delay-level", p.getCastDelayLevel());
                    yaml.set(path + "reaction-level", p.getReactionLevel());
                    yaml.set(path + "durability-save-level", p.getDurabilitySaveLevel());
                    yaml.set(path + "stop-on-full-inventory", p.isStopOnFullInventory());
                    yaml.set(path + "auto-recast-on-miss", p.isAutoRecastOnMiss());
                    yaml.set(path + "auto-sell-on-full-inventory", p.isAutoSellOnFullInventory());
                    yaml.set(path + "auto-switch-rod", p.isAutoSwitchRod());
                    yaml.set(path + "active-package-tier", p.getStoredPackageTier());
                    yaml.set(path + "total-packages-purchased", p.getTotalPackagesPurchased());
                    yaml.set(path + "first-purchase-claimed", p.isFirstPurchaseClaimed());
                    yaml.set(path + "total-auto-catches", p.getTotalAutoCatches());
                    p.clearDirty();
                }
            }
            yaml.save(tempFile);
            Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException fallbackError) {
            try {
                if (tempFile.exists()) {
                    Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void saveIfDirtyAsync() {
        boolean anyDirty = false;
        for (AutoFishProfile profile : profiles.values()) {
            if (profile.isDirty()) {
                anyDirty = true;
                break;
            }
        }
        if (!anyDirty) {
            return;
        }
        EvenMoreFish.getScheduler().runTaskAsynchronously(this::saveAllSync);
    }
}
