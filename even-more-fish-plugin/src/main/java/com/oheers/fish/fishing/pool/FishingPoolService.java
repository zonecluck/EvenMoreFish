package com.oheers.fish.fishing.pool;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.fishing.FishingLakeProvider;
import com.oheers.fish.api.fishing.FishingLakeProvider.Bonus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-thread ordinary fishing bonuses; no Simulator or reward ownership. */
public final class FishingPoolService {
    private final EvenMoreFish plugin;
    private final Path file;
    private final Map<UUID, TrackedHook> hooks = new HashMap<>();
    private final Map<UUID, TrackedHook> ownerHooks = new HashMap<>();
    private FishingPoolSettings settings;
    private YamlConfiguration document;
    private MyScheduledTask task;
    private int ticks;
    private int renderCursor;

    public FishingPoolService(EvenMoreFish plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.file = plugin.getDataFolder().toPath().resolve("fishing-pools.yml");
    }

    /** Invalid reloads retain both the active configuration and hook overrides. */
    public boolean reload() {
        try {
            if (!Files.exists(file)) plugin.saveResource("fishing-pools.yml", false);
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file.toFile());
            FishingPoolSettings parsed = FishingPoolSettings.load(candidate);
            clearHooks();
            if (task != null) task.cancel();
            task = null;
            settings = parsed;
            document = candidate;
            ensureTicker();
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("fishing-pools.yml unchanged: " + failure.getMessage());
            return false;
        }
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        clearHooks();
    }

    /** Call after custom rod/buff min/max adjustments, never from a cancelled cast. */
    public void track(FishHook hook) {
        if (settings == null || !settings.enabled() || !(hook.getShooter() instanceof Player player)) return;
        if (hooks.containsKey(hook.getUniqueId())) return;
        // Only one hook per caster; also prevents abandoned hooks from retaining their owner.
        quit(player.getUniqueId());
        if (hooks.size() >= 2048) return;
        hooks.put(hook.getUniqueId(), new TrackedHook(hook, player.getUniqueId()));
        ownerHooks.put(player.getUniqueId(), hooks.get(hook.getUniqueId()));
        ensureTicker();
    }

    public void untrack(UUID hookId) {
        TrackedHook hook = hooks.remove(hookId);
        if (hook != null) {
            ownerHooks.remove(hook.owner, hook);
            hook.restore();
        }
        stopIdleTicker();
    }

    public void quit(UUID playerId) {
        TrackedHook hook = ownerHooks.get(playerId);
        if (hook != null) untrack(hook.hook.getUniqueId());
    }

    /** Location must be the caught hook location; Common and air positions never gain a boost. */
    public double multiplier(Location location, String rarityId) {
        if (settings == null || !settings.enabled() || !settings.boosts(rarityId) || !isWater(location)) return 1;
        FishingPool pool = at(location);
        if (pool != null) return pool.rareMultiplier();
        Bonus zoneBonus = resolveSimulatorZoneBonus(location);
        return zoneBonus == null ? 1 : zoneBonus.rareMultiplier();
    }

    /** Player-aware native catch path. An API query or untracked hook cannot receive lake bonuses. */
    public double multiplier(Player player, Location location, String rarityId) {
        if (settings == null || !settings.boosts(rarityId) || player == null || location == null) return 1;
        TrackedHook tracked = ownerHooks.get(player.getUniqueId());
        if (tracked == null || !tracked.hook.isValid()) return 1;
        Location actual = tracked.hook.getLocation();
        if (!Objects.equals(actual.getWorld(), location.getWorld()) || actual.distanceSquared(location) > .000001) return 1;
        Bonus bonus = bonus(player, tracked.hook);
        return bonus == null ? 1 : bonus.rareMultiplier();
    }

    /** Only admitted, still-current native casts may resolve a bonus, including BITE cosmetics. */
    public Bonus bonus(Player player, FishHook hook) {
        if (settings == null || !settings.enabled() || player == null || hook == null
            || !player.isOnline() || !hook.isValid() || !player.getWorld().equals(hook.getWorld())) return null;
        TrackedHook tracked = ownerHooks.get(player.getUniqueId());
        if (tracked == null || tracked.hook != hook || !hooks.containsKey(hook.getUniqueId())) return null;
        return resolve(player, hook.getLocation());
    }

    private Bonus resolve(Player player, Location location) {
        if (!isWater(location)) return null;
        try {
            // ServicesManager chooses the highest-priority registration in O(1), including hot registrations.
            var registration = plugin.getServer().getServicesManager().getRegistration(FishingLakeProvider.class);
            if (registration != null && registration.getPlugin().isEnabled()) {
                Bonus lake = registration.getProvider().resolve(player, location.clone());
                if (lake != null) return lake;
            }
        } catch (RuntimeException | LinkageError failure) {
            // Optional providers cannot interfere with the normal EMF catch or static pool fallback.
        }
        FishingPool pool = at(location);
        if (pool != null) return new Bonus("pool:" + pool.id(), pool.speedPercent(), pool.rareMultiplier(), false);
        return resolveSimulatorZoneBonus(location);
    }

    private Bonus resolveSimulatorZoneBonus(Location location) {
        var simulator = plugin.getSimulatorModule();
        var simSettings = simulator == null ? null : simulator.settings();
        if (simSettings == null || simulator.zoneResolver() == null) return null;
        var match = simulator.zoneResolver().resolve(location, simSettings);
        if (!match.found() || match.denied()) return null;
        return new Bonus("zone:" + match.zone().id(), 25.0, 1.4, false);
    }

    public Collection<FishingPool> pools() { return settings == null ? java.util.List.of() : settings.pools().values(); }
    public FishingPool get(String id) { return settings == null ? null : settings.pools().get(id.toLowerCase(java.util.Locale.ROOT)); }
    public double defaultSpeed() { return settings == null ? 25 : settings.defaultSpeed(); }
    public double defaultMultiplier() { return settings == null ? 1.5 : settings.defaultMultiplier(); }
    public boolean enabled() { return settings != null && settings.enabled(); }

    public FishingPool create(String id, Location center, double radius, double speed, double multiplier) throws IOException {
        if (document == null || settings == null) throw new IllegalStateException("Pool configuration is not loaded");
        if (center.getWorld() == null) throw new IllegalArgumentException("Pool world is unavailable");
        FishingPool pool = new FishingPool(id, center.getWorld().getName(), center.getX(), center.getY(),
            center.getZ(), radius, speed, multiplier);
        if (settings.pools().containsKey(pool.id())) throw new IllegalArgumentException("Pool ID already exists");
        if (settings.pools().size() >= FishingPoolSettings.MAX_POOLS) throw new IllegalArgumentException("Pool limit reached (256)");
        YamlConfiguration candidate = copyDocument();
        String key = "fishing-pools.pools." + pool.id() + ".";
        candidate.set(key + "world", pool.world()); candidate.set(key + "x", pool.x());
        candidate.set(key + "y", pool.y()); candidate.set(key + "z", pool.z());
        candidate.set(key + "radius", pool.radius()); candidate.set(key + "speed-percent", pool.speedPercent());
        candidate.set(key + "rare-multiplier", pool.rareMultiplier());
        save(candidate);
        return pool;
    }

    public boolean remove(String id) throws IOException {
        FishingPool pool = get(id);
        if (pool == null) return false;
        YamlConfiguration candidate = copyDocument();
        FishingPoolSettings.removeDefinition(candidate, pool.id());
        save(candidate);
        return true;
    }

    private YamlConfiguration copyDocument() throws IOException {
        YamlConfiguration copy = new YamlConfiguration();
        try { copy.loadFromString(document.saveToString()); }
        catch (org.bukkit.configuration.InvalidConfigurationException invalid) { throw new IOException(invalid); }
        return copy;
    }

    private void save(YamlConfiguration candidate) throws IOException {
        FishingPoolSettings parsed = FishingPoolSettings.load(candidate);
        FishingPoolStore.write(file, candidate.saveToString());
        document = candidate;
        settings = parsed;
        ensureTicker();
        stopIdleTicker();
    }

    private FishingPool at(Location location) {
        World world = location.getWorld();
        if (world == null || settings == null) return null;
        return FishingPool.select(settings.pools().values(), world.getName(), location.getX(), location.getY(),
            location.getZ(), settings.verticalTolerance());
    }

    public static boolean isWater(Location location) {
        if (location == null || location.getWorld() == null || !Double.isFinite(location.getX())
            || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())) return false;
        World world = location.getWorld();
        int x = location.getBlockX(), z = location.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        return containsWater(world, x, location.getBlockY(), z)
            || containsWater(world, x, (int) Math.floor(location.getY() - 0.35), z);
    }

    private static boolean containsWater(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        Block block = world.getBlockAt(x, y, z);
        return switch (block.getType()) {
            case WATER, BUBBLE_COLUMN, SEAGRASS, TALL_SEAGRASS, KELP, KELP_PLANT -> true;
            default -> block.getBlockData() instanceof Waterlogged data && data.isWaterlogged();
        };
    }

    private void tick() {
        Iterator<TrackedHook> iterator = hooks.values().iterator();
        while (iterator.hasNext()) {
            TrackedHook tracked = iterator.next();
            FishHook hook = tracked.hook;
            Player owner = Bukkit.getPlayer(tracked.owner);
            if (!hook.isValid() || owner == null || !owner.isOnline() || hook.getTicksLived() > 6000
                || !owner.getWorld().equals(hook.getWorld())) {
                tracked.restore(); ownerHooks.remove(tracked.owner, tracked); iterator.remove(); continue;
            }
            Location location = hook.getLocation();
            Bonus pool = hook.getState() == FishHook.HookState.BOBBING ? resolve(owner, location) : null;
            if (tracked.active != null && (!tracked.active.equals(pool) || hook.getWaitTime() <= 0)) tracked.restore();
            // Wait for native countdown initialization; it already includes enchantment Lure.
            if (pool != null && !tracked.wait.used() && pool.speedPercent() > 0 && hook.getWaitTime() > 0) {
                int current = hook.getWaitTime();
                int reduced = tracked.wait.reduce(current, pool.speedPercent());
                tracked.active = pool;
                hook.setApplyLure(false);
                hook.setWaitTime(reduced);
            }
        }
        ticks += 2;
        if (ticks % 20 == 0 && settings.particles()) renderRings();
        stopIdleTicker();
    }

    private boolean needsTicker() {
        return settings != null && settings.enabled()
            && (!hooks.isEmpty() || settings.particles() && !settings.pools().isEmpty());
    }

    private void ensureTicker() {
        if (task == null && needsTicker()) task = EvenMoreFish.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    private void stopIdleTicker() {
        if (task != null && !needsTicker()) { task.cancel(); task = null; }
    }

    private void renderRings() {
        var pools = settings.pools().values().stream().sorted(java.util.Comparator.comparing(FishingPool::id)).toList();
        if (pools.isEmpty()) return;
        int budget = 768;
        // Rotate pools so the global particle budget cannot permanently starve later IDs.
        int start = renderCursor++ % pools.size();
        for (int n = 0; n < pools.size() && budget > 0; n++) {
            FishingPool pool = pools.get((start + n) % pools.size());
            World world = Bukkit.getWorld(pool.world());
            if (world == null) continue;
            double distance = pool.radius() + settings.viewerDistance();
            for (Player viewer : world.getPlayers()) {
                if (budget <= 0) break;
                Location eye = viewer.getLocation();
                if (pool.distanceSquared(eye.getX(), eye.getZ()) > distance * distance
                    || Math.abs(eye.getY() - pool.y()) > settings.viewerDistance()) continue;
                for (int i = 0; i < settings.ringPoints() && budget > 0; i++) {
                    double angle = i * Math.PI * 2 / settings.ringPoints();
                    double x = pool.x() + Math.cos(angle) * pool.radius(), z = pool.z() + Math.sin(angle) * pool.radius();
                    if (!world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4)) continue;
                    viewer.spawnParticle(Particle.END_ROD, x, pool.y() + 0.08, z, 1, 0, 0, 0, 0);
                    budget--;
                }
            }
        }
    }

    private void clearHooks() {
        for (TrackedHook hook : hooks.values()) hook.restore();
        hooks.clear();
        ownerHooks.clear();
    }

    private static final class TrackedHook {
        final FishHook hook;
        final UUID owner;
        final int minWait, maxWait;
        final boolean applyLure;
        final PoolWaitState wait = new PoolWaitState();
        Bonus active;

        TrackedHook(FishHook hook, UUID owner) {
            this.hook = hook; this.owner = owner;
            this.minWait = hook.getMinWaitTime(); this.maxWait = hook.getMaxWaitTime();
            this.applyLure = hook.getApplyLure();
        }

        void restore() {
            if (active == null) return;
            if (hook.isValid()) {
                hook.setWaitTime(minWait, maxWait);
                hook.setApplyLure(applyLure);
                hook.setWaitTime(wait.restore(hook.getWaitTime()));
            }
            active = null;
        }
    }
}
