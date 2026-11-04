package com.oheers.fish.fishing.simulator.zones;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import br.net.fabiozumbi12.RedProtect.Bukkit.Region;
import com.oheers.fish.config.simulator.SimulatorSettings;
import org.bukkit.Location;

/** Optional hard-reference kept outside the always-loaded zone resolver. */
final class RedProtectZoneAdapter {
    private RedProtectZoneAdapter() {}

    static boolean contains(SimulatorSettings.Zone zone, Location at) {
        Region region = RedProtect.get().getAPI().getRegion(at);
        return region != null && region.getName().equalsIgnoreCase(zone.regionId());
    }
}
