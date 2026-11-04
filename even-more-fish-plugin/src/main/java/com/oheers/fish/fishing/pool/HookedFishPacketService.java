package com.oheers.fish.fishing.pool;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.visual.PacketCleanupQueue;
import com.oheers.fish.fishing.simulator.visual.PacketEntityIdAllocator;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import org.bukkit.Location;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Private native-BITE decoration. Never enters Simulator population, sessions, selection or rewards. */
public final class HookedFishPacketService {
    static final int FRAME_TICKS = 2, DURATION_TICKS = 60, MAX_SCENES = 16, MAX_CLEANUP = 64;
    private record Scene(Player viewer, FishHook hook, Location anchor, FishAgent fish, long start) {}
    private final EvenMoreFish plugin;
    private final PacketTransport transport;
    private final Map<UUID, Scene> scenes = new HashMap<>();
    private final Map<UUID, UUID> shownHooks = new HashMap<>();
    private final PacketCleanupQueue cleanup = new PacketCleanupQueue(MAX_CLEANUP);
    private MyScheduledTask task;
    private long ticks;

    public HookedFishPacketService(EvenMoreFish plugin, PacketTransport transport) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.transport = java.util.Objects.requireNonNull(transport);
    }

    /** Caller must have accepted this exact BITE at MONITOR; pool admission is rechecked here. */
    public boolean bite(Player player, FishHook hook) {
        try {
            var pools = plugin.getFishingPools();
            var bonus = pools == null ? null : pools.bonus(player, hook);
            if (bonus == null || !bonus.hookedVisual() || !preferences(player) || !transport.available()) return false;
            UUID viewer = player.getUniqueId(), hookId = hook.getUniqueId();
            Location anchor = hook.getLocation();
            if (hookId.equals(shownHooks.get(viewer)) || scenes.containsKey(viewer) || scenes.size() >= MAX_SCENES
                || shownHooks.size() >= 2048 || cleanup.pendingFor(viewer)
                || cleanup.pending() + scenes.size() >= MAX_CLEANUP
                || player.getLocation().distanceSquared(anchor) > 32 * 32) return false;
            // The key is cosmetic metadata only; this agent is never passed to a fish catalog/reward bridge.
            var fish = new FishAgent(UUID.randomUUID(), PacketEntityIdAllocator.next(),
                new FishKey("cosmetic", "native-hook"), FishVisualModel.COD, bonus.id(),
                anchor.getWorld().getUID(), viewer, ticks + DURATION_TICKS,
                new Vec3(anchor.getX(), anchor.getY() - .25, anchor.getZ()));
            shownHooks.put(viewer, hookId);
            Scene scene = new Scene(player, hook, anchor.clone(), fish, ticks);
            scenes.put(viewer, scene);
            try {
                ensureTicker();
                if (!transport.spawn(viewer, fish)) { stopScene(viewer); return false; }
                return true;
            } catch (RuntimeException | LinkageError failure) {
                stopScene(viewer);
                return false;
            }
        } catch (RuntimeException | LinkageError failure) {
            return false; // Optional cosmetics must never abort the ordinary catch.
        }
    }

    private boolean preferences(Player viewer) {
        var module = plugin.getSimulatorModule();
        return module == null || module.ownEffectsEnabled(viewer.getUniqueId())
            && module.ownHudProfile(viewer.getUniqueId()).cinematic();
    }

    private void ensureTicker() {
        if (task == null) task = EvenMoreFish.getScheduler().runTaskTimer(plugin, this::tick, FRAME_TICKS, FRAME_TICKS);
    }

    void tick() {
        ticks += FRAME_TICKS;
        cleanup.retry(transport, 8);
        for (var entry : List.copyOf(scenes.entrySet())) {
            Scene scene = entry.getValue();
            try {
                Player viewer = scene.viewer();
                Location hook = scene.hook().getLocation();
                var bonus = plugin.getFishingPools().bonus(viewer, scene.hook());
                if (!viewer.isOnline() || !scene.hook().isValid() || scene.fish().isExpired(ticks)
                    || !viewer.getWorld().getUID().equals(scene.fish().worldId())
                    || !scene.hook().getWorld().getUID().equals(scene.fish().worldId())
                    || !preferences(viewer) || !FishingPoolService.isWater(hook)
                    || hook.distanceSquared(scene.anchor()) > 1 || viewer.getLocation().distanceSquared(hook) > 32 * 32
                    || bonus == null || !bonus.hookedVisual()) {
                    stopScene(entry.getKey());
                    continue;
                }
                double angle = (ticks - scene.start()) * .25;
                // Bob the fish slightly above/below the surface so the orbit reads as a live catch.
                double bob = Math.sin((ticks - scene.start()) * .14) * .06;
                scene.fish().waypoint(new Vec3(hook.getX() + Math.sin(angle) * .12,
                    hook.getY() - .25 + bob, hook.getZ() + Math.cos(angle) * .12));
                scene.fish().move(ticks, .16);
                if (!transport.teleport(entry.getKey(), scene.fish())) stopScene(entry.getKey());
            } catch (RuntimeException | LinkageError failure) {
                stopScene(entry.getKey());
            }
        }
        if (scenes.isEmpty() && cleanup.pending() == 0 && task != null) { task.cancel(); task = null; }
    }

    private void stopScene(UUID viewer) {
        Scene scene = scenes.remove(viewer);
        if (scene == null) return;
        scene.fish().despawn();
        boolean destroyed;
        try { destroyed = transport.destroy(viewer, scene.fish().entityId()); }
        catch (RuntimeException | LinkageError failure) { destroyed = false; }
        if (!destroyed) cleanup.record(viewer, scene.fish().entityId());
    }

    /** Terminal event, recast or world change; timeout retains the hook marker to prevent repeat spawns. */
    public void stop(UUID viewer) { stopScene(viewer); shownHooks.remove(viewer); }
    public void quit(UUID viewer) { stop(viewer); shownHooks.remove(viewer); cleanup.forgetDisconnected(viewer); }

    /** Reload retains failed destroys and its bounded retry ticker. */
    public void clear() {
        for (UUID viewer : List.copyOf(scenes.keySet())) stop(viewer);
        shownHooks.clear();
        cleanup.retry(transport, MAX_CLEANUP);
        if (cleanup.pending() == 0 && task != null) { task.cancel(); task = null; }
    }

    public void shutdown() {
        clear();
        if (task != null) { task.cancel(); task = null; }
    }
    int activeScenes() { return scenes.size(); }
    int pendingCleanup() { return cleanup.pending(); }
}
