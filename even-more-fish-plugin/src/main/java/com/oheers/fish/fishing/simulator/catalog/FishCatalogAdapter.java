package com.oheers.fish.fishing.simulator.catalog;

import com.oheers.fish.api.fishing.CatchType;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.simulator.population.FishKey;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Catalog-only population weights. No Fish.init(), no side effects, no fish cloning/reward.
 * Player-specific requirements/buffs must be checked by Phase 6 targeting, NOT by this visual preview.
 */
public final class FishCatalogAdapter {
    public record Entry(FishKey key, double weight) {}
    private final List<Entry> entries;
    private final double totalWeight;
    public FishCatalogAdapter() {
        List<Entry> result=new ArrayList<>();double weight=0;
        for(Rarity rarity:FishManager.getInstance().getRarityMap().values()) {
            if(rarity.isDisabled() || !Double.isFinite(rarity.getWeight()) || rarity.getWeight()<=0)continue;
            for(Fish fish:rarity.getOriginalFishList()) {
                if(fish.getCatchType()==CatchType.HUNT)continue;
                if(!Double.isFinite(fish.getWeight()) || fish.getWeight()<=0)continue;
                double composite=rarity.getWeight()*fish.getWeight();
                if(composite>0 && Double.isFinite(composite)){
                    weight+=composite;result.add(new Entry(new FishKey(rarity.getId(),fish.getName()),composite));
                }
            }
        }
        entries=List.copyOf(result);totalWeight=weight;
    }
    public int count(){return entries.size();}
    public FishKey sample(RandomGenerator rng) {
        if(entries.isEmpty() || totalWeight<=0)return null;
        double roll=rng.nextDouble(totalWeight);
        for(Entry item:entries){roll-=item.weight();if(roll<0)return item.key();}
        return entries.getLast().key();
    }
}
