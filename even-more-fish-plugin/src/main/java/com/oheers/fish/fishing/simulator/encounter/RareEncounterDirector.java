package com.oheers.fish.fishing.simulator.encounter;

import com.oheers.fish.fishing.simulator.environment.EnvironmentSnapshot;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishPopulationService;
import com.oheers.fish.fishing.simulator.population.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Single-owner-thread rare encounter director for existing catalog-backed FishAgents.
 * No spawning, rarity rolling, item generation, rewards, Bukkit calls or extra packet entities.
 * RARE_SCHOOL is a visual label on an existing school, never a population multiplier.
 */
public final class RareEncounterDirector {
    public enum Kind { RARE_PRESENCE, GOLDEN, SHADOW, GHOST, RARE_SCHOOL }
    public enum Phase { SPAWNED, OBSERVED, RESERVED, COMPLETED, EXPIRED }
    public record Viewer(UUID id, UUID worldId, String zoneId, Vec3 position, boolean particlesEnabled) { }
    public record Cue(UUID fishId, Kind kind, Vec3 position, int particles, long tick) { }
    public record Snapshot(UUID id, Kind kind, Phase phase, FishKey fishKey, UUID worldId,
                           String zoneId, UUID privateOwner, List<UUID> fishIds, long expiresAtTick) { }
    @FunctionalInterface public interface EnvironmentLookup { EnvironmentSnapshot snapshot(FishAgent fish,long tick); }
    @FunctionalInterface public interface SchoolLookup { List<UUID> members(FishAgent fish); }
    @FunctionalInterface public interface Emitter { boolean emit(UUID viewer, Cue cue); }
    private record ZoneKey(UUID worldId, String zoneId, UUID privateOwner) { }
    private static final class Encounter {
        final UUID id=UUID.randomUUID();
        final Kind kind;
        final FishKey fishKey;
        final ZoneKey zone;
        final List<UUID> fishIds;
        final Set<UUID> observed = new HashSet<>();
        final long expiresAt;
        Phase phase=Phase.SPAWNED;
        Encounter(Kind kind,FishAgent leader,List<UUID> fishIds,long expiresAt) {
            this.kind=kind;fishKey=leader.fishKey();
            zone=new ZoneKey(leader.worldId(),leader.zoneId(),leader.privateOwner());
            this.fishIds=List.copyOf(fishIds);this.expiresAt=expiresAt;
        }
        Snapshot snapshot(){return new Snapshot(id,kind,phase,fishKey,zone.worldId(),
            zone.zoneId(),zone.privateOwner(),fishIds,expiresAt);}
    }
    private final RareEncounterSettings settings;
    private final RandomGenerator rng;
    private final Map<UUID,Encounter> active = new LinkedHashMap<>();
    private final Set<UUID> inspected = new HashSet<>();
    private final Map<ZoneKey,Long> nextZoneTick = new HashMap<>();
    private final Map<UUID,Long> nextViewerTick = new HashMap<>();
    private int cursor;
    private long spawned, expired, completed, observations, particles, drops, failures;

    public RareEncounterDirector(RareEncounterSettings settings,RandomGenerator rng) {
        this.settings=Objects.requireNonNull(settings);this.rng=Objects.requireNonNull(rng);
    }
    public boolean enabled(){return settings.enabled();}
    public int active(){return active.size();}
    public long spawned(){return spawned;}
    public long expired(){return expired;}
    public long completed(){return completed;}
    public long observations(){return observations;}
    public long particles(){return particles;}
    public long drops(){return drops;}
    public long failures(){return failures;}
    public List<Snapshot> snapshots(){return active.values().stream().map(Encounter::snapshot).toList();}
    public void clear(){active.clear();inspected.clear();nextZoneTick.clear();nextViewerTick.clear();cursor=0;}
    public void forgetViewer(UUID player) {
        nextViewerTick.remove(player);
        for(Encounter encounter:active.values())encounter.observed.remove(player);
    }
    public void committed(UUID fishId) {
        // Called only after an actual successful CatchCommit, not on visual/reel preview.
        for(Encounter encounter:new ArrayList<>(active.values())) {
            if(encounter.fishIds.contains(fishId)){
                encounter.phase=Phase.COMPLETED;completed++;active.remove(encounter.id);
            }
        }
    }
    public void tick(long tick,FishPopulationService population,EnvironmentLookup env,SchoolLookup school) {
        if(!enabled())return;
        Objects.requireNonNull(population);Objects.requireNonNull(env);Objects.requireNonNull(school);
        reconcile(tick,population);
        if(tick%settings.limits().scanIntervalTicks()!=0 || active.size()>=settings.limits().maxActive())return;
        List<FishAgent> candidates=new ArrayList<>(population.agents());
        if(candidates.isEmpty())return;
        int startingIndex=Math.floorMod(cursor,candidates.size());
        cursor=(cursor+settings.limits().candidatesPerScan())%candidates.size();
        int scanned=0;
        for(int step=0;step<candidates.size() && scanned<settings.limits().candidatesPerScan();step++) {
            FishAgent fish=candidates.get((startingIndex+step)%candidates.size());scanned++;
            if(inspected.contains(fish.id()) || !eligible(fish) || claimed(fish.id()))continue;
            ZoneKey zone=zone(fish);
            if(zoneActiveCount(zone)>=settings.limits().maxPerZone())continue;
            if(tick<nextZoneTick.getOrDefault(zone,Long.MIN_VALUE))continue;
            EnvironmentSnapshot snapshot=env.snapshot(fish,tick);
            List<Kind> choices=available(fish,snapshot,school,population);
            // Environmental data may be unavailable during a sampling-budget shortfall.
            // Eligible GOLDEN / RARE_PRESENCE events still work without a sample.
            if(choices.isEmpty())continue;
            Kind forced=settings.override(fish.fishKey());
            // Do not burn the single roll before a night/deep-only override becomes eligible.
            if(forced!=null && !choices.contains(forced))continue;
            inspected.add(fish.id()); // ONE chance per fish; cannot roll repeatedly by ticking/AFK.
            if(rng.nextDouble()>=settings.limits().activationChance())continue;
            Kind chosen=forced==null?weighted(choices):forced;
            if(chosen==null)continue;
            List<UUID> members=members(chosen,fish,school,population);
            if(members.isEmpty())continue;
            create(chosen,fish,members,tick,false);
            if(active.size()>=settings.limits().maxActive())break;
        }
    }
    /** Staff preview uses a REAL currently spawned, eligible FishAgent; skips chance/cooldown only. */
    public boolean preview(Kind kind,FishAgent fish,long tick,FishPopulationService population,
                           EnvironmentLookup env,SchoolLookup school) {
        if(!enabled() || kind==null || fish==null || population.get(fish.id())!=fish ||
            !eligible(fish) || claimed(fish.id()) || active.size()>=settings.limits().maxActive() ||
            zoneActiveCount(zone(fish))>=settings.limits().maxPerZone())return false;
        if(!available(fish,env.snapshot(fish,tick),school,population).contains(kind))return false;
        List<UUID> group=members(kind,fish,school,population);
        if(group.isEmpty())return false;
        inspected.add(fish.id());
        create(kind,fish,group,tick,true);
        return true;
    }
    private void create(Kind kind,FishAgent leader,List<UUID> fishIds,long tick,boolean staff) {
        Encounter encounter=new Encounter(kind,leader,fishIds,tick+settings.limits().lifeTicks());
        active.put(encounter.id,encounter);spawned++;
        // Staff preview bypasses zone cooldown but must not erase an existing cooldown.
        if(!staff)nextZoneTick.put(encounter.zone,tick+settings.limits().zoneCooldownTicks());
    }
    private List<Kind> available(FishAgent fish,EnvironmentSnapshot snapshot,SchoolLookup school,
                                  FishPopulationService population) {
        List<Kind> result=new ArrayList<>();
        for(Kind kind:Kind.values()) {
            var policy=settings.profiles().get(kind);
            if(policy==null || !policy.enabled() ||
                (policy.weight()<=0 && settings.override(fish.fishKey())!=kind))continue;
            if(policy.nightOnly() && (snapshot==null || snapshot.time()!=EnvironmentSnapshot.TimeOfDay.NIGHT))continue;
            if(policy.deepOnly() && (snapshot==null || snapshot.depth()!=EnvironmentSnapshot.Depth.DEEP))continue;
            if(policy.stormOnly() && (snapshot==null || snapshot.weather()==EnvironmentSnapshot.Weather.CLEAR))continue;
            if(kind==Kind.RARE_SCHOOL && members(kind,fish,school,population).size()<policy.minSchoolSize())continue;
            result.add(kind);
        }
        return result;
    }
    private Kind weighted(List<Kind> kinds) {
        int sum=0;
        for(Kind kind:kinds)sum+=settings.profiles().get(kind).weight();
        if(sum<=0)return null;
        int roll=rng.nextInt(sum);
        for(Kind kind:kinds) {
            roll-=settings.profiles().get(kind).weight();
            if(roll<0)return kind;
        }
        return kinds.getLast();
    }
    private List<UUID> members(Kind kind,FishAgent leader,SchoolLookup school,FishPopulationService population) {
        if(kind!=Kind.RARE_SCHOOL)return List.of(leader.id());
        List<UUID> found=school.members(leader);
        if(found==null || !found.contains(leader.id()))return List.of();
        List<UUID> group=new ArrayList<>();
        group.add(leader.id());
        for(UUID id:found) {
            if(id.equals(leader.id()))continue;
            FishAgent member=population.get(id);
            if(member==null || !eligible(member) || claimed(id) ||
                !member.worldId().equals(leader.worldId()) || !member.zoneId().equals(leader.zoneId()) ||
                !Objects.equals(member.privateOwner(),leader.privateOwner()))continue;
            group.add(id);
            if(group.size()==4)break;
        }
        return group;
    }
    private void reconcile(long tick,FishPopulationService population) {
        // O(population cap + active cap). No world/chunk access and no dangling overlays.
        Set<UUID> live=new HashSet<>();
        for(FishAgent fish:population.agents())live.add(fish.id());
        inspected.retainAll(live);
        for(Encounter encounter:new ArrayList<>(active.values())) {
            boolean missing=encounter.fishIds.stream().anyMatch(id->!live.contains(id));
            if(tick>=encounter.expiresAt || missing){
                encounter.phase=Phase.EXPIRED;expired++;active.remove(encounter.id);continue;
            }
            if(encounter.fishIds.stream().anyMatch(id->population.get(id).reserved()))
                encounter.phase=Phase.RESERVED;
            else if(encounter.phase==Phase.RESERVED)encounter.phase=encounter.observed.isEmpty()
                ?Phase.SPAWNED:Phase.OBSERVED;
        }
        nextZoneTick.entrySet().removeIf(e->e.getValue()<=tick);
        nextViewerTick.entrySet().removeIf(e->e.getValue()<=tick);
    }
    private boolean eligible(FishAgent fish){
        return fish.cell()!=null && fish.state()==FishAgent.State.WANDERING &&
            settings.allows(fish.fishKey().rarityId());
    }
    private boolean claimed(UUID id){return active.values().stream().anyMatch(e->e.fishIds.contains(id));}
    private int zoneActiveCount(ZoneKey key){
        // Per-zone limit applies across shared AND private encounters.
        int count=0;
        for(Encounter e:active.values())
            if(e.zone.worldId().equals(key.worldId()) && e.zone.zoneId().equals(key.zoneId()))count++;
        return count;
    }
    private static ZoneKey zone(FishAgent fish){return new ZoneKey(fish.worldId(),fish.zoneId(),fish.privateOwner());}

    /** FX-only viewer observation. Strict privacy + visibility + global/per-viewer emission budgets. */
    public void render(long tick,List<Viewer> viewers,FishPopulationService population,
                       BiPredicate<UUID,UUID> shownTo,Emitter emitter) {
        if(!enabled() || tick%settings.limits().fxIntervalTicks()!=0 || viewers.isEmpty())return;
        int global=0;
        int start=(int)((tick/settings.limits().fxIntervalTicks())%viewers.size());
        double maxDist=settings.limits().revealRadius()*settings.limits().revealRadius();
        for(int i=0;i<viewers.size() && global<settings.limits().maxParticlesPerTick();i++) {
            Viewer viewer=viewers.get((start+i)%viewers.size());
            if(!viewer.particlesEnabled())continue;
            int used=0;
            for(Encounter encounter:active.values()) {
                if(used>=settings.limits().maxParticlesPerViewer() || global>=settings.limits().maxParticlesPerTick()){
                    drops++;break;
                }
                if(!encounter.zone.worldId().equals(viewer.worldId()) ||
                    !encounter.zone.zoneId().equals(viewer.zoneId()) ||
                    (encounter.zone.privateOwner()!=null && !encounter.zone.privateOwner().equals(viewer.id())))continue;
                if(!encounter.observed.contains(viewer.id()) &&
                    tick<nextViewerTick.getOrDefault(viewer.id(),Long.MIN_VALUE))continue;
                // A multi-fish encounter is visible only while its target fish are actually rendered.
                FishAgent fish=null;
                int groupStart=encounter.kind==Kind.RARE_SCHOOL
                    ?(int)((tick/settings.limits().fxIntervalTicks())%encounter.fishIds.size()):0;
                for(int j=0;j<encounter.fishIds.size();j++) {
                    UUID memberId=encounter.fishIds.get((groupStart+j)%encounter.fishIds.size());
                    FishAgent member=population.get(memberId);
                    if(member!=null && member.visibleTo(viewer.id()) &&
                        shownTo.test(viewer.id(),member.id()) &&
                        member.position().distanceSquared(viewer.position())<=maxDist) {fish=member;break;}
                }
                if(fish==null)continue;
                int amount=Math.min(encounter.kind==Kind.GOLDEN?3:2,
                    Math.min(settings.limits().maxParticlesPerTick()-global,
                             settings.limits().maxParticlesPerViewer()-used));
                if(amount<=0)continue;
                boolean accepted;
                try {accepted=emitter.emit(viewer.id(),new Cue(fish.id(),encounter.kind,
                    fish.position(),amount,tick));}
                catch(RuntimeException error){accepted=false;}
                global+=amount;used+=amount; // even a failing emitter consumes budget
                if(accepted){
                    particles+=amount;
                    if(encounter.observed.add(viewer.id())){
                        observations++;
                        nextViewerTick.put(viewer.id(),tick+settings.limits().viewerCooldownTicks());
                    }
                    if(encounter.phase==Phase.SPAWNED)encounter.phase=Phase.OBSERVED;
                } else failures++;
            }
        }
    }
}
