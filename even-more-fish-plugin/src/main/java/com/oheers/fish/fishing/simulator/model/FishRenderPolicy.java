package com.oheers.fish.fishing.simulator.model;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure visual authorization; different viewers may see different entity types for the SAME fish. */
public final class FishRenderPolicy {
    private final FishPackAudience audience;
    private final Predicate<UUID> customSupported;
    private FishModelSettings settings=FishModelSettings.disabled();
    public FishRenderPolicy(FishPackAudience audience){this(audience,id->false);}
    public FishRenderPolicy(FishPackAudience audience,Predicate<UUID> customSupported){
        this.audience=Objects.requireNonNull(audience);
        this.customSupported=Objects.requireNonNull(customSupported);
    }
    public void configure(FishModelSettings next) {
        settings=Objects.requireNonNull(next);
        audience.configure(next.enabled()?next.requiredPackId():null);
    }
    public FishModelSettings settings(){return settings;}
    public FishRenderSpec choose(UUID viewer,FishAgent fish) {
        FishRenderSpec vanilla=FishRenderSpec.vanilla(fish.model());
        if (!settings.enabled() || !audience.ready(viewer)) return vanilla;
        FishModelSettings.Profile profile = settings.profile(fish.fishKey());
        // Avoid the PacketEvents client-version lookup for unconfigured fish.
        return profile == null || !customSupported.test(viewer) ? vanilla : profile.withFallback(fish.model());
    }
}
