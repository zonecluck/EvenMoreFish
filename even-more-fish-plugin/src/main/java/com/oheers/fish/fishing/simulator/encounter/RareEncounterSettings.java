package com.oheers.fish.fishing.simulator.encounter;

import java.util.Locale;
import com.oheers.fish.fishing.simulator.population.FishKey;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable, strictly bounded cosmetics policy. Does not change fish identity, weights or rewards. */
public record RareEncounterSettings(boolean enabled, Limits limits, Set<String> eligibleRarities,
                                    Map<RareEncounterDirector.Kind, Profile> profiles,
                                    Map<String, RareEncounterDirector.Kind> fishOverrides) {
    public record Limits(int scanIntervalTicks, int candidatesPerScan, int maxActive, int maxPerZone,
                         int lifeTicks, int zoneCooldownTicks, int viewerCooldownTicks,
                         int fxIntervalTicks, int maxParticlesPerTick, int maxParticlesPerViewer,
                         double revealRadius, double activationChance) {
        public Limits {
            if (scanIntervalTicks < 5 || scanIntervalTicks > 200 || candidatesPerScan < 1 || candidatesPerScan > 160 ||
                maxActive < 1 || maxActive > 64 || maxPerZone < 1 || maxPerZone > maxActive ||
                lifeTicks < 40 || lifeTicks > 7200 || zoneCooldownTicks < 0 || zoneCooldownTicks > 72000 ||
                viewerCooldownTicks < 0 || viewerCooldownTicks > 72000 ||
                fxIntervalTicks < 4 || fxIntervalTicks > 100 ||
                maxParticlesPerTick < 1 || maxParticlesPerTick > 128 ||
                maxParticlesPerViewer < 1 || maxParticlesPerViewer > maxParticlesPerTick ||
                !Double.isFinite(revealRadius) || revealRadius < 3 || revealRadius > 32 ||
                !Double.isFinite(activationChance) || activationChance < 0 || activationChance > 1)
                throw new IllegalArgumentException("Invalid encounter limits");
        }
    }
    public record Profile(boolean enabled, int weight, boolean nightOnly, boolean deepOnly,
                          boolean stormOnly, int minSchoolSize) {
        public Profile {
            if (weight < 0 || weight > 1000 || minSchoolSize < 1 || minSchoolSize > 4)
                throw new IllegalArgumentException("Invalid encounter profile");
        }
    }
    public RareEncounterSettings {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(eligibleRarities, "eligibleRarities");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(fishOverrides, "fishOverrides");
        if (eligibleRarities.isEmpty() || eligibleRarities.size() > 32)
            throw new IllegalArgumentException("eligibleRarities must have 1..32 entries");
        eligibleRarities = Set.copyOf(eligibleRarities.stream()
            .map(s -> Objects.requireNonNull(s).trim().toLowerCase(Locale.ROOT))
            .peek(s -> {if (s.isEmpty() || s.length() > 64) throw new IllegalArgumentException("Invalid rarity id");})
            .collect(Collectors.toSet()));
        profiles = Map.copyOf(profiles);
        if(fishOverrides.size()>128)throw new IllegalArgumentException("Too many FishKey overrides");
        var normalized=new java.util.HashMap<String,RareEncounterDirector.Kind>();
        for(var entry:fishOverrides.entrySet()){
            String key=entry.getKey().trim().toLowerCase(Locale.ROOT);
            int colon=key.indexOf(':');
            if(key.length()>128 || colon<=0 || colon==key.length()-1 || key.indexOf(':',colon+1)>=0)
                throw new IllegalArgumentException("Override key must be Rarity:FishId");
            normalized.put(key,Objects.requireNonNull(entry.getValue()));
        }
        fishOverrides=Map.copyOf(normalized);
        if (!profiles.keySet().containsAll(Set.of(RareEncounterDirector.Kind.values())))
            throw new IllegalArgumentException("Missing encounter profile");
        if (enabled && profiles.values().stream().noneMatch(p -> p.enabled() && p.weight() > 0))
            throw new IllegalArgumentException("At least one enabled profile needs positive weight");
    }
    public boolean allows(String rarityId) {
        return rarityId != null && eligibleRarities.contains(rarityId.toLowerCase(Locale.ROOT));
    }
    public RareEncounterDirector.Kind override(FishKey key){
        return fishOverrides.get((key.rarityId()+":"+key.fishId()).toLowerCase(Locale.ROOT));
    }
    public static RareEncounterSettings disabled() {
        Map<RareEncounterDirector.Kind,Profile> defaults = Map.of(
            RareEncounterDirector.Kind.RARE_PRESENCE, new Profile(true,45,false,false,false,1),
            RareEncounterDirector.Kind.GOLDEN, new Profile(true,20,false,false,false,1),
            RareEncounterDirector.Kind.SHADOW, new Profile(true,15,false,true,false,1),
            RareEncounterDirector.Kind.GHOST, new Profile(true,10,true,false,false,1),
            RareEncounterDirector.Kind.RARE_SCHOOL, new Profile(true,10,false,false,false,2));
        return new RareEncounterSettings(false,
            new Limits(20,20,8,2,600,400,160,12,24,6,16,.25),
            Set.of("rare","epic","legendary","mythical"),defaults,Map.of());
    }
}
