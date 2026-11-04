package com.oheers.fish.fishing.simulator.ai;

import com.oheers.fish.api.fishing.CatchType;
import com.oheers.fish.api.fishing.FishingType;
import com.oheers.fish.api.requirement.RequirementContext;
import com.oheers.fish.baits.BaitHandler;
import com.oheers.fish.baits.manager.BaitNBTManager;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.buff.CompetitionBuffManager;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.rods.CustomRod;
import com.oheers.fish.fishing.rods.RodManager;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.baits.model.WeightModifier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

/**
 * Side-effect-free targeting for preview AND gated Simulator catches. Reuses EMF
 * requirements and weight modifiers; does not re-roll FishKey or consume bait.
 */
public final class FishAttractionPolicy {
    public record Snapshot(CustomRod rod, BaitHandler bait) {}

    public Snapshot capture(ItemStack heldRod) {
        CustomRod customRod=RodManager.getInstance().getRod(heldRod);
        BaitHandler bait=null;
        if ((customRod==null || customRod.isBaitAllowed()) &&
            !(MainConfig.getInstance().getBaitCompetitionDisable() && Competition.isActive()) &&
            BaitNBTManager.isBaitedRod(heldRod)){
            // Sample ONCE per cast, just as an EMF catch chooses one applied bait.
            bait=BaitNBTManager.randomBaitApplication(heldRod);
        }
        return new Snapshot(customRod,bait);
    }

    public double attraction(Player player,Location hook,Snapshot snapshot,FishAgent agent){
        Objects.requireNonNull(snapshot);
        FishKey key=agent.fishKey();
        FishManager catalog=FishManager.getInstance();
        Rarity rarity=catalog.getRarity(key.rarityId());
        if(rarity==null || rarity.isDisabled() || rarity.getWeight()<=0)return 0;
        Fish fish=rarity.getEditableFish(key.fishId());
        if(fish==null || fish.getCatchType()==CatchType.HUNT)return 0;
        RequirementContext context=new RequirementContext(hook.getWorld(),hook,player,null,null,FishingType.VANILLA);
        if(rarity.getPermission()!=null && !player.hasPermission(rarity.getPermission()))return 0;
        if(!rarity.getRequirement().meetsRequirements(context) ||
           !fish.getRequirement().meetsRequirements(context))return 0;
        // EMF's region boost of zero disables this rarity in the current region.
        String region=com.oheers.fish.FishUtils.getRegionName(hook);
        double regionModifier=MainConfig.getInstance().getRegionBoost(region,rarity.getId());
        if(!Double.isFinite(regionModifier) || regionModifier<=0)return 0;
        CustomRod rod=snapshot.rod();
        if(rod!=null){
            if(rod.getAllowedRarities().stream().noneMatch(r->r.getId().equalsIgnoreCase(rarity.getId())))return 0;
            if(!rod.getAllowedFish().isEmpty() && rod.getAllowedFish().stream().noneMatch(f->
                f.getName().equalsIgnoreCase(fish.getName()) &&
                    f.getRarity().getId().equalsIgnoreCase(rarity.getId())))return 0;
        }
        if(!Competition.isActive() && MainConfig.getInstance().isFishCatchOnlyInCompetition() &&
           !fish.isCompExemptFish())return 0;
        // Weighted targeting must reflect the same fractional region boost and
        // competition/rod luck modifiers used by legacy rarity selection.
        double attraction=regionModifier;
        double luck=CompetitionBuffManager.getInstance().getLuckBonus(player.getUniqueId())+
            (rod==null?0.0:rod.getLuckModifier());
        if(luck>0 && rarity.getIndex()>0)attraction*=1.0+luck*(rarity.getIndex()*0.4);
        if(rod!=null)attraction*=Math.max(0,rod.getRarityMultipliers().getOrDefault(rarity.getId(),1.0));
        if(snapshot.bait()!=null){
            var bait=snapshot.bait().getBaitData();
            double rarityBase=rarity.getWeight();
            double fishBase=FishManager.getBaseFishWeight(fish);
            attraction*=applyRarityModifier(bait.rarityModifiers(),rarity,rarityBase)/rarityBase;
            attraction*=applyFishModifier(bait.fishModifiers(),fish,fishBase)/fishBase;
        }
        return Double.isFinite(attraction) ? Math.min(16.0,Math.max(0.0,attraction)) : 0.0;
    }

    private static double applyRarityModifier(Map<Rarity,WeightModifier> mods,Rarity target,double base){
        for(var entry:mods.entrySet())if(entry.getKey().getId().equalsIgnoreCase(target.getId()))
            return entry.getValue().apply(base);
        return base;
    }
    private static double applyFishModifier(Map<Fish,WeightModifier> mods,Fish target,double base){
        for(var entry:mods.entrySet())if(entry.getKey().getName().equalsIgnoreCase(target.getName()) &&
            entry.getKey().getRarity().getId().equalsIgnoreCase(target.getRarity().getId()))
            return entry.getValue().apply(base);
        return base;
    }
}
