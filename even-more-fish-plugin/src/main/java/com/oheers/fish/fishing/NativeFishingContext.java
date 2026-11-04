package com.oheers.fish.fishing;

import com.oheers.fish.api.fishing.FishingType;
import com.oheers.fish.api.requirement.RequirementContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Identifies a real EMF rod catch so pool bonuses cannot affect fish hunting or API queries. */
public final class NativeFishingContext extends RequirementContext {
    public NativeFishingContext(Location hook, Player player, FishingType type) {
        super(hook.getWorld(), hook, player, null, null, type);
    }
}
