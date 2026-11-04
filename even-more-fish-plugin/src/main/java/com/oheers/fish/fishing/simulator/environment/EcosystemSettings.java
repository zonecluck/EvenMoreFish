package com.oheers.fish.fishing.simulator.environment;

import com.oheers.fish.fishing.simulator.behavior.FishPersonality;
import com.oheers.fish.fishing.simulator.waterfx.SurfaceFxService;
import java.util.Map;
import java.util.Objects;

/** Versioned, immutable and strictly validated optional feature settings. */
public record EcosystemSettings(boolean enabled, boolean samplingEnabled, boolean personalityEnabled,
                                boolean waterFxEnabled, EnvironmentSampler.Limits samples,
                                int depthScanBlocks, double shyRadius,
                                Map<String,FishPersonality> rarityProfiles,
                                Map<String,FishPersonality> fishOverrides, SurfaceFxService.Limits fx) {
    public EcosystemSettings {
        Objects.requireNonNull(samples);Objects.requireNonNull(fx);
        rarityProfiles=Map.copyOf(Objects.requireNonNull(rarityProfiles));
        fishOverrides=Map.copyOf(Objects.requireNonNull(fishOverrides));
        if(depthScanBlocks<2||depthScanBlocks>16||!Double.isFinite(shyRadius)||shyRadius<1||shyRadius>16)
            throw new IllegalArgumentException("Invalid ecosystem sampling / personality limits");
    }
    public static EcosystemSettings disabled(){
        return new EcosystemSettings(false,false,false,false,
            new EnvironmentSampler.Limits(4,256,200),8,5.0,
            Map.of("Common",FishPersonality.CALM,"Uncommon",FishPersonality.CURIOUS,
                "Rare",FishPersonality.SHY,"Epic",FishPersonality.AGGRESSIVE,
                "Legendary",FishPersonality.MYTHIC,"Mythical",FishPersonality.MYTHIC),
            Map.of(),new SurfaceFxService.Limits(10,32,8,24,16));
    }
}
