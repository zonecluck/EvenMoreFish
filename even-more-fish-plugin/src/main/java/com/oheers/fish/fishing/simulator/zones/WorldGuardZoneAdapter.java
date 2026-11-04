package com.oheers.fish.fishing.simulator.zones;

import com.oheers.fish.config.simulator.SimulatorSettings;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;

/** Optional hard-reference kept outside the always-loaded zone resolver. */
final class WorldGuardZoneAdapter {
    private WorldGuardZoneAdapter() {}

    static boolean contains(SimulatorSettings.Zone zone, Location at) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
            .get(BukkitAdapter.adapt(at.getWorld()));
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(zone.regionId());
        return region != null && region.contains(at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }
}
