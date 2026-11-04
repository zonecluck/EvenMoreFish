package com.oheers.fish.fishing.simulator.zones;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.simulator.SimulatorSettings;
import org.bukkit.Location;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This checks simulator membership only. It does NOT grant fishing permission and
 * must not be used to bypass EMF's existing world/region checks or protections.
 * Call it on the owning Paper server thread (not from a DB/packet worker).
 */
public final class SimulatorZoneResolver {
    private final EvenMoreFish plugin;
    private final Set<String> missingProviderWarnings = new HashSet<>();

    public SimulatorZoneResolver(EvenMoreFish plugin) { this.plugin = plugin; }

    public record Match(@Nullable SimulatorSettings.Zone zone, boolean denied) {
        public static Match none() { return new Match(null, false); }
        public boolean found() { return zone != null; }
    }

    public Match resolve(Location location, SimulatorSettings config) {
        if (location.getWorld() == null) return Match.none();
        List<SimulatorSettings.Zone> candidates = new ArrayList<>();
        for (SimulatorSettings.Zone zone : config.zones()) {
            if (!zone.isInWorld(location.getWorld().getName())) continue;
            if (matches(zone, location)) candidates.add(zone);
        }
        return resolveMatches(candidates);
    }

    /** Pure resolver extracted so deny-overrides and tie-break rules can be tested offline. */
    public static Match resolveMatches(List<SimulatorSettings.Zone> candidates) {
        ZoneSelection.Result result = ZoneSelection.choose(candidates);
        return new Match(result.zone(), result.denied());
    }

    private boolean matches(SimulatorSettings.Zone zone, Location at) {
        return switch (zone.type()) {
            case WORLD -> true;
            case CUBOID -> zone.containsCuboid(at.getX(), at.getY(), at.getZ());
            case WORLDGUARD_REGION -> hasWorldGuardRegion(zone, at);
            case REDPROTECT_REGION -> hasRedProtectRegion(zone, at);
        };
    }

    private boolean hasWorldGuardRegion(SimulatorSettings.Zone zone, Location at) {
        if (!available("WorldGuard", zone.id())) return false;
        try {
            return WorldGuardZoneAdapter.contains(zone, at);
        } catch (LinkageError | RuntimeException failure) {
            warnOnce(zone.id(), "WorldGuard query failed: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private boolean hasRedProtectRegion(SimulatorSettings.Zone zone, Location at) {
        if (!available("RedProtect", zone.id())) return false;
        try {
            return RedProtectZoneAdapter.contains(zone, at);
        } catch (LinkageError | RuntimeException failure) {
            warnOnce(zone.id(), "RedProtect query failed: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private boolean available(String pluginName, String zone) {
        PluginManager pm = plugin.getServer().getPluginManager();
        if (pm.isPluginEnabled(pluginName)) return true;
        warnOnce(zone, pluginName + " is missing, zone disabled (fail-closed)");
        return false;
    }

    private void warnOnce(String zone, String detail) {
        if (missingProviderWarnings.add(zone + ":" + detail)) {
            plugin.getLogger().warning("[Simulator] Zone " + zone + ": " + detail);
        }
    }

    public void resetWarnings() { missingProviderWarnings.clear(); }
}
