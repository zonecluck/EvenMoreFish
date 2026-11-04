package com.oheers.fish.fishing.simulator.zones;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.simulator.SimulatorSettings;
import com.oheers.fish.fishing.pool.FishingPool;
import com.oheers.fish.fishing.pool.FishingPoolService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Manages center TextDisplay holograms for active CUBOID simulator zones and fishing pools.
 */
public final class ZoneHologramService implements Listener {
    private static final double DEFAULT_Y_OFFSET = 1.6D;
    private static final List<String> DEFAULT_LINES = List.of(
        "<aqua><bold>[KHU VỰC HỒ CÂU]</bold></aqua>",
        " ",
        "<gold>+{luck}% May mắn</gold>",
        "<green>+{speed}% Tốc độ câu</green>"
    );

    private record Target(String key, String world, double x, double y, double z,
                          int minY, int maxY, boolean cuboid, int luckPercent, int speedPercent) {
        int chunkX() {
            return ((int) Math.floor(x)) >> 4;
        }
        int chunkZ() {
            return ((int) Math.floor(z)) >> 4;
        }
    }

    private final EvenMoreFish plugin;
    private final NamespacedKey markerKey;
    private final Map<String, TextDisplay> displays = new HashMap<>();
    private final Map<String, Target> targets = new HashMap<>();
    private boolean enabled = true;
    private double yOffset = DEFAULT_Y_OFFSET;
    private List<String> templateLines = DEFAULT_LINES;

    public ZoneHologramService(EvenMoreFish plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "lake_hologram_id");
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void refresh(@Nullable SimulatorSettings settings) {
        clear();
        loadHologramConfig();
        if (!enabled) return;

        if (settings != null) {
            for (SimulatorSettings.Zone zone : settings.zones()) {
                if (!zone.enabled() || zone.denySimulator() || zone.type() != SimulatorSettings.ZoneType.CUBOID) {
                    continue;
                }
                String key = "zone:" + zone.id().toLowerCase(Locale.ROOT);
                targets.put(key, new Target(
                    key,
                    zone.world(),
                    zone.centerX(),
                    zone.centerY(),
                    zone.centerZ(),
                    zone.minY(),
                    zone.maxY(),
                    true,
                    40,
                    25
                ));
            }
        }

        FishingPoolService poolService = plugin.getFishingPools();
        if (poolService != null && poolService.enabled()) {
            for (FishingPool pool : poolService.pools()) {
                if (isNearExistingZoneTarget(pool.world(), pool.x(), pool.z())) continue;
                String key = "pool:" + pool.id().toLowerCase(Locale.ROOT);
                int luck = (int) Math.round((pool.rareMultiplier() - 1.0) * 100.0);
                int speed = (int) Math.round(pool.speedPercent());
                targets.put(key, new Target(
                    key,
                    pool.world(),
                    pool.x(),
                    pool.y(),
                    pool.z(),
                    (int) Math.floor(pool.y() - 2),
                    (int) Math.ceil(pool.y() + 2),
                    false,
                    luck > 0 ? luck : 40,
                    speed > 0 ? speed : 25
                ));
            }
        }

        spawnAllLoaded();
    }

    public void ensureSpawned() {
        if (!enabled || targets.isEmpty()) return;
        for (Target target : targets.values()) {
            TextDisplay existing = displays.get(target.key());
            if (existing != null && existing.isValid()) continue;
            spawnIfChunkLoaded(target);
        }
    }

    public void clear() {
        for (TextDisplay display : displays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
        targets.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled || targets.isEmpty()) return;
        String worldName = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (Target target : targets.values()) {
            if (target.world().equalsIgnoreCase(worldName) && target.chunkX() == chunkX && target.chunkZ() == chunkZ) {
                spawnInLoadedChunk(event.getWorld(), target);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!enabled || targets.isEmpty()) return;
        String worldName = event.getWorld().getName();
        for (Target target : targets.values()) {
            if (target.world().equalsIgnoreCase(worldName)) {
                spawnIfChunkLoaded(target);
            }
        }
    }

    private void loadHologramConfig() {
        enabled = true;
        yOffset = DEFAULT_Y_OFFSET;
        templateLines = DEFAULT_LINES;
        File file = new File(plugin.getDataFolder(), "simulator.yml");
        if (!file.isFile()) return;
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            enabled = yaml.getBoolean("hologram.enabled", true);
            double configuredOffset = yaml.getDouble("hologram.y-offset", DEFAULT_Y_OFFSET);
            if (Double.isFinite(configuredOffset) && configuredOffset >= 0.2 && configuredOffset <= 8.0) {
                yOffset = configuredOffset;
            }
            List<String> lines = yaml.getStringList("hologram.lines");
            if (!lines.isEmpty()) {
                templateLines = List.copyOf(lines);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isNearExistingZoneTarget(String world, double x, double z) {
        for (Target target : targets.values()) {
            if (!target.cuboid() || !target.world().equalsIgnoreCase(world)) continue;
            double dx = target.x() - x;
            double dz = target.z() - z;
            if (dx * dx + dz * dz <= 16.0) return true;
        }
        return false;
    }

    private void spawnAllLoaded() {
        for (Target target : targets.values()) {
            spawnIfChunkLoaded(target);
        }
    }

    private void spawnIfChunkLoaded(Target target) {
        World world = Bukkit.getWorld(target.world());
        if (world == null || !world.isChunkLoaded(target.chunkX(), target.chunkZ())) return;
        spawnInLoadedChunk(world, target);
    }

    private void spawnInLoadedChunk(World world, Target target) {
        TextDisplay current = displays.remove(target.key());
        if (current != null && current.isValid()) {
            current.remove();
        }
        double surfaceY = resolveSurfaceY(world, target);
        Location location = new Location(world, target.x(), surfaceY + yOffset, target.z());
        removeStaleDisplays(world, location, target.key());
        Component text = buildText(target.luckPercent(), target.speedPercent());
        TextDisplay spawned = world.spawn(location, TextDisplay.class, entity -> {
            entity.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, target.key());
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setSeeThrough(false);
            entity.setLineWidth(220);
            entity.setViewRange(48F);
        });
        displays.put(target.key(), spawned);
    }

    private void removeStaleDisplays(World world, Location center, String targetKey) {
        for (Entity entity : world.getNearbyEntities(center, 6.0, 6.0, 6.0)) {
            if (!(entity instanceof TextDisplay display)) continue;
            String tag = display.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
            if (targetKey.equals(tag) || tag != null && !targets.containsKey(tag)) {
                display.remove();
            }
        }
    }

    private double resolveSurfaceY(World world, Target target) {
        if (!target.cuboid()) {
            return target.y();
        }
        int cx = (int) Math.floor(target.x());
        int cz = (int) Math.floor(target.z());
        int topY = Math.min(world.getMaxHeight() - 1, target.maxY());
        int bottomY = Math.max(world.getMinHeight(), target.minY());
        for (int y = topY; y >= bottomY; y--) {
            if (isWaterBlock(world, cx, y, cz)) {
                return y + 1.0;
            }
        }
        // Check immediate neighbors in case center has a lily pad/pillar
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            int nx = cx + offset[0];
            int nz = cz + offset[1];
            if (!world.isChunkLoaded(nx >> 4, nz >> 4)) continue;
            for (int y = topY; y >= bottomY; y--) {
                if (isWaterBlock(world, nx, y, nz)) {
                    return y + 1.0;
                }
            }
        }
        return target.y();
    }

    private static boolean isWaterBlock(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        return switch (block.getType()) {
            case WATER, BUBBLE_COLUMN, SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT -> true;
            default -> block.getBlockData() instanceof Waterlogged data && data.isWaterlogged();
        };
    }

    private Component buildText(int luckPercent, int speedPercent) {
        List<Component> rendered = new ArrayList<>(templateLines.size());
        MiniMessage mm = MiniMessage.miniMessage();
        for (String raw : templateLines) {
            String replaced = raw
                .replace("{luck}", Integer.toString(luckPercent))
                .replace("{speed}", Integer.toString(speedPercent));
            rendered.add(mm.deserialize(replaced).decoration(TextDecoration.ITALIC, false));
        }
        return Component.join( net.kyori.adventure.text.JoinConfiguration.newlines(), rendered);
    }
}
