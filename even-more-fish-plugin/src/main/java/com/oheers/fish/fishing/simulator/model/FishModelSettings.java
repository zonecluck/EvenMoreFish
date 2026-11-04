package com.oheers.fish.fishing.simulator.model;

import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Validated atomic config. Custom visuals can never change FishKey, rarity or catch weight. */
public record FishModelSettings(boolean enabled, UUID requiredPackId,
                                Map<String,Profile> fish, Map<String,Profile> rarities) {
    public FishModelSettings {
        fish = Map.copyOf(fish);
        rarities = Map.copyOf(rarities);
        if (enabled && requiredPackId == null) throw new IllegalArgumentException("pack UUID required if enabled");
    }
    public static FishModelSettings disabled() {return new FishModelSettings(false,null,Map.of(),Map.of());}
    public record Profile(String material, String itemModel, float scale, int interpolationTicks) {
        public Profile {
            // Reuse exactly the same safety rules for config and runtime.
            new FishRenderSpec(FishVisualModel.COD,material,itemModel,scale,interpolationTicks);
            if (material.isEmpty())throw new IllegalArgumentException("profile must be custom");
        }
        public FishRenderSpec withFallback(FishVisualModel model) {
            return new FishRenderSpec(model,material,itemModel,scale,interpolationTicks);
        }
    }
    public Profile profile(FishKey key) {
        String id=(key.rarityId()+":"+key.fishId()).toLowerCase(Locale.ROOT);
        Profile specific=fish.get(id);
        return specific!=null?specific:rarities.get(key.rarityId().toLowerCase(Locale.ROOT));
    }
}
