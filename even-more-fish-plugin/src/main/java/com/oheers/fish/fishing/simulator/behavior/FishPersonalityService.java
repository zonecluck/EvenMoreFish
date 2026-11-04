package com.oheers.fish.fishing.simulator.behavior;

import com.oheers.fish.fishing.simulator.environment.EnvironmentSnapshot;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishPopulationService;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.population.WaterPoint;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure ambient behavior; only WANDERING agents can be steered/flee. */
public final class FishPersonalityService {
    public interface Navigation {
        List<WaterPoint> adjacent(WaterPoint at);
        boolean traversable(FishAgent fish,WaterPoint next);
    }
    private final Map<String,FishPersonality> rarity;
    private final Map<String,FishPersonality> overrides;
    private final double disturbanceRadiusSquared;
    private long shyScatters;
    public FishPersonalityService(Map<String,FishPersonality> rarity,Map<String,FishPersonality> overrides,
                                  double disturbanceRadius) {
        this.rarity=Map.copyOf(Objects.requireNonNull(rarity));
        this.overrides=Map.copyOf(Objects.requireNonNull(overrides));
        if(!Double.isFinite(disturbanceRadius)||disturbanceRadius<1||disturbanceRadius>16)
            throw new IllegalArgumentException("disturbanceRadius");
        disturbanceRadiusSquared=disturbanceRadius*disturbanceRadius;
    }
    public FishPersonality of(FishAgent agent) {
        return overrides.getOrDefault(agent.fishKey().rarityId()+":"+agent.fishKey().fishId(),
            rarity.getOrDefault(agent.fishKey().rarityId(),FishPersonality.CALM));
    }
    public double movementFactor(FishAgent fish, EnvironmentSnapshot snapshot) {
        double factor=switch(of(fish)) {
            case CALM -> .88;
            case CURIOUS -> 1.03;
            case SHY -> 1.12;
            case AGGRESSIVE -> 1.28;
            case NOCTURNAL -> snapshot!=null && snapshot.time()==EnvironmentSnapshot.TimeOfDay.NIGHT?1.22:.75;
            case DEEP_DWELLER -> snapshot!=null && snapshot.depth()==EnvironmentSnapshot.Depth.DEEP?1.05:.82;
            case MYTHIC -> .94;
        };
        if(snapshot!=null && snapshot.weather()==EnvironmentSnapshot.Weather.THUNDER)factor*=.96;
        if(snapshot!=null && snapshot.biome().contains("RIVER") && of(fish)==FishPersonality.CURIOUS)factor*=1.04;
        return Math.min(1.35,Math.max(.6,factor));
    }
    /** Prefer deeper indexed neighboring cells for DEEP_DWELLER, no dry-block teleport. */
    public WaterPoint preferNext(FishAgent fish,List<WaterPoint> cells,Navigation nav, int offset) {
        if(cells.isEmpty())return null;
        WaterPoint chosen=null;
        int best=Integer.MIN_VALUE;
        FishPersonality personality=of(fish);
        for(int i=0;i<cells.size();i++) {
            WaterPoint next=cells.get((offset+i)%cells.size());
            if(!nav.traversable(fish,next))continue;
            if(personality!=FishPersonality.DEEP_DWELLER)return next;
            int score=fish.cell().y()-next.y();
            if(chosen==null || score>best) {chosen=next;best=score;}
        }
        return chosen;
    }
    /** A private SHY fish only reacts to its owner; shared fish may react to anyone. */
    public int disturb(Vec3 center,UUID world,String zone,UUID triggeringViewer,
                       FishPopulationService population,Navigation nav) {
        int changed=0;
        for(FishAgent fish:population.agents()) {
            if(fish.state()!=FishAgent.State.WANDERING || fish.cell()==null ||
                !fish.worldId().equals(world) || !fish.zoneId().equals(zone) ||
                (fish.privateOwner()!=null && !fish.privateOwner().equals(triggeringViewer)) ||
                of(fish)!=FishPersonality.SHY || fish.position().distanceSquared(center)>disturbanceRadiusSquared)
                continue;
            WaterPoint next=null;double far=-1;
            for(WaterPoint candidate:nav.adjacent(fish.cell())) {
                if(!nav.traversable(fish,candidate))continue;
                double distance=candidate.swimmingPosition().distanceSquared(center);
                if(distance>far) {far=distance;next=candidate;}
            }
            if(next!=null && fish.fleeTo(next)) {changed++;shyScatters++;}
        }
        return changed;
    }
    public long shyScatters(){return shyScatters;}
}
