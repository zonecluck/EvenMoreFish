package com.oheers.fish.fishing.simulator.school;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishPopulationService;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.population.WaterPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded, owner-thread steering of already spawned fish. NEVER decides loot, creates fish,
 * changes reservations or touches Bukkit APIs. Cell validation belongs to the Paper caller.
 * Schools are transient; runtime reload rebuilds them instead of persisting stale positions.
 */
public final class FishSchoolService {
    public interface Navigation {
        List<WaterPoint> adjacent(WaterPoint cell);
        boolean traversable(FishAgent fish, WaterPoint cell);
    }
    private record Bucket(UUID world, String zone, UUID owner, int x, int z) {}
    private final SchoolSettings settings;
    private final Map<UUID,FishSchool> schools = new LinkedHashMap<>();
    private final Map<UUID,UUID> memberships = new HashMap<>();
    private final Map<Bucket,List<UUID>> indexedLeaders = new HashMap<>();
    private long decisions, neighborChecks, budgetExhaustions, scatters, detachments, recruited;
    private int cursor;
    private long lastIndexTick=Long.MIN_VALUE;

    public FishSchoolService(SchoolSettings settings){this.settings=Objects.requireNonNull(settings);}
    public boolean enabled(){return settings.enabled();}
    public int schools(){return schools.size();}
    public int members(){return memberships.size();}
    public boolean assigned(UUID fishId){return memberships.containsKey(fishId);}
    public UUID schoolId(UUID fishId){return memberships.get(fishId);}
    /** Read-only snapshot for rare-school visuals; never exposes the mutable group. */
    public List<UUID> membersOf(UUID fishId) {
        UUID groupId=memberships.get(fishId);
        FishSchool group=groupId==null?null:schools.get(groupId);
        return group==null?List.of():List.copyOf(group.orderedMembers());
    }
    public FishSchool school(UUID id){return schools.get(id);}
    public long decisions(){return decisions;}
    public long neighborChecks(){return neighborChecks;}
    public long budgetExhaustions(){return budgetExhaustions;}
    public long scatters(){return scatters;}
    public long detachments(){return detachments;}
    public long recruited(){return recruited;}
    public void clear(){schools.clear();memberships.clear();indexedLeaders.clear();cursor=0;lastIndexTick=Long.MIN_VALUE;}

    private static Bucket bucket(FishAgent fish) {
        return new Bucket(fish.worldId(),fish.zoneId(),fish.privateOwner(),
            Math.floorDiv((int)Math.floor(fish.position().x()),8),
            Math.floorDiv((int)Math.floor(fish.position().z()),8));
    }
    private void reindex(FishPopulationService pop,long tick) {
        if(lastIndexTick==tick)return;
        indexedLeaders.clear();
        for(FishSchool school:schools.values()) {
            FishAgent leader=pop.get(school.leaderId());
            if(leader!=null && leader.state()==FishAgent.State.WANDERING)
                indexedLeaders.computeIfAbsent(bucket(leader),ignored->new ArrayList<>()).add(school.id());
        }
        lastIndexTick=tick;
    }
    /** Called ONLY after the fish passes FishPopulationService.add and its existing agent caps. */
    public boolean onSpawn(FishAgent fish,FishPopulationService pop,long tick) {
        if(!enabled() || fish==null || fish.cell()==null || fish.state()!=FishAgent.State.WANDERING ||
            memberships.containsKey(fish.id()) || pop.get(fish.id())!=fish)return false;
        reindex(pop,tick);
        Bucket b=bucket(fish);
        double best=settings.joinRadius()*settings.joinRadius();
        FishSchool selected=null;
        int radius=(int)Math.ceil(settings.joinRadius()/8.0)+1;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            Bucket key=new Bucket(b.world(),b.zone(),b.owner(),b.x()+dx,b.z()+dz);
            for(UUID id:indexedLeaders.getOrDefault(key,List.of())) {
                FishSchool school=schools.get(id);
                if(school==null || school.size()>=settings.maxMembers())continue;
                FishAgent leader=pop.get(school.leaderId());
                if(leader==null || !leader.model().equals(fish.model()))continue;
                double dist=leader.position().distanceSquared(fish.position());
                if(dist<=best){best=dist;selected=school;}
            }
        }
        if(selected!=null) {
            if(!selected.add(fish.id(),settings.maxMembers()))return false;
            memberships.put(fish.id(),selected.id());recruited++;
            return true;
        }
        if(schools.size()>=settings.maxSchools() || countZone(fish.worldId(),fish.zoneId())>=settings.maxSchoolsPerZone())return false;
        FishSchool created=new FishSchool(UUID.randomUUID(),fish.worldId(),fish.zoneId(),fish.privateOwner(),
            fish.id(),fish.position());
        schools.put(created.id(),created);memberships.put(fish.id(),created.id());
        // Index now, so fish from the same batch can join the new school.
        indexedLeaders.computeIfAbsent(b,ignored->new ArrayList<>()).add(created.id());
        return true;
    }
    private int countZone(UUID world,String zone) {
        int n=0;
        for(FishSchool group:schools.values())
            if(group.worldId().equals(world) && group.zoneId().equals(zone))n++;
        return n;
    }
    /** Make new fish spawn near compatible leaders when cached water permits. Never spawns fish itself. */
    public WaterPoint preferredSpawnCell(UUID world,String zone,UUID owner,
                                         com.oheers.fish.fishing.simulator.population.FishVisualModel model,
                                         FishPopulationService pop,Navigation navigation,long tick) {
        if(!enabled() || schools.isEmpty())return null;
        reindex(pop,tick);
        int offset=(int)Math.floorMod(tick,Math.max(1,schools.size()));
        List<FishSchool> ordered=new ArrayList<>(schools.values());
        int inspected=0;
        for(int i=0;i<ordered.size() && inspected<4;i++) {
            FishSchool school=ordered.get((offset+i)%ordered.size());
            if(!school.worldId().equals(world) || !school.zoneId().equals(zone) ||
                !Objects.equals(school.privateOwner(),owner) || school.size()>=settings.maxMembers())continue;
            inspected++;
            FishAgent leader=pop.get(school.leaderId());
            if(leader==null || leader.cell()==null || leader.state()!=FishAgent.State.WANDERING
                || !leader.model().equals(model))continue;
            // The live Paper caller still validates loaded chunk, region and player view distance.
            for(WaterPoint next:navigation.adjacent(leader.cell())) {
                if(!navigation.traversable(leader,next))continue;
                boolean occupied=false;
                for(UUID memberId:school.orderedMembers()) {
                    FishAgent member=pop.get(memberId);
                    if(member!=null && member.position().distanceSquared(next.swimmingPosition())<.85){
                        occupied=true;break;
                    }
                }
                if(!occupied)return next;
            }
        }
        return null;
    }
    /** Detach reserved/removed fish immediately on the next movement batch; AI owns their motion. */
    public void reconcile(FishPopulationService pop) {
        for(UUID fishId:List.copyOf(memberships.keySet())) {
            FishAgent fish=pop.get(fishId);
            if(fish==null || fish.state()!=FishAgent.State.WANDERING)detach(fishId);
        }
        for(UUID id:List.copyOf(schools.keySet()))if(schools.get(id).size()==0)schools.remove(id);
        lastIndexTick=Long.MIN_VALUE;
    }
    private void detach(UUID fishId){
        UUID schoolId=memberships.remove(fishId);
        if(schoolId==null)return;
        FishSchool school=schools.get(schoolId);
        if(school!=null){school.remove(fishId);if(school.size()==0)schools.remove(schoolId);}
        detachments++;
    }
    public void scatter(Vec3 origin,UUID world,String zone,UUID owner,FishPopulationService pop,long tick) {
        if(!enabled() || origin==null)return;
        double limit=Math.pow(settings.joinRadius()+2,2);
        for(FishSchool school:schools.values()) {
            if(!school.worldId().equals(world) || !school.zoneId().equals(zone) ||
                !Objects.equals(school.privateOwner(),owner))continue;
            FishAgent leader=pop.get(school.leaderId());
            if(leader==null || leader.position().distanceSquared(origin)>limit)continue;
            // Visual-only disturbance, bounded to nearby schools of the same visibility policy.
            school.scatter(origin,tick,settings.scatterTicks());scatters++;
        }
    }

    private static final class Budget {
        int decisions, checks;
        Budget(int decisions,int checks){this.decisions=decisions;this.checks=checks;}
    }
    /** One owner-thread movement batch. No full Boids N^2 / no chunk loads / no extra packets. */
    public void steer(long tick,FishPopulationService pop,Navigation navigation) {
        if(!enabled())return;
        reconcile(pop);
        if(schools.isEmpty())return;
        if(settings.recruitPerMove()>0 && tick%20==0)recruitOrphans(pop,tick);
        Budget budget=new Budget(settings.decisionsPerMove(),settings.neighborChecksPerMove());
        List<FishSchool> ordered=new ArrayList<>(schools.values());
        int start=Math.floorMod(cursor++,ordered.size());
        for(int i=0;i<ordered.size();i++) {
            FishSchool school=ordered.get((start+i)%ordered.size());
            school.update(tick,settings.reformTicks());
            FishAgent leader=pop.get(school.leaderId());
            if(leader==null)continue;
            if(leader.state()==FishAgent.State.WANDERING)chooseStep(leader,school,leader,0,tick,pop,navigation,budget);
            int index=1;
            for(UUID fishId:school.orderedMembers()) {
                if(fishId.equals(school.leaderId()))continue;
                FishAgent member=pop.get(fishId);
                if(member!=null && member.state()==FishAgent.State.WANDERING)
                    chooseStep(member,school,leader,index,tick,pop,navigation,budget);
                index++;
            }
        }
        if(budget.decisions==0 || budget.checks==0)budgetExhaustions++;
    }
    private void recruitOrphans(FishPopulationService pop,long tick) {
        List<FishAgent> all=new ArrayList<>(pop.agents());
        if(all.isEmpty())return;
        int start=(int)Math.floorMod(tick/20,all.size());
        int attempts=Math.min(settings.recruitPerMove(),all.size());
        for(int n=0;n<attempts;n++) {
            FishAgent fish=all.get((start+n)%all.size());
            if(!assigned(fish.id()) && fish.state()==FishAgent.State.WANDERING && fish.cell()!=null)
                onSpawn(fish,pop,tick);
        }
    }
    private void chooseStep(FishAgent fish,FishSchool school,FishAgent leader,int formationIndex,
                            long tick,FishPopulationService pop,Navigation nav,Budget budget) {
        if(fish.cell()==null || fish.nextCell()!=null ||
            fish.position().distanceSquared(fish.waypoint())>.0025 || budget.decisions<=0 || budget.checks<=0)return;
        if(formationIndex==0 && school.lastDecisionTick()!=Long.MIN_VALUE &&
            tick-school.lastDecisionTick()<settings.decisionIntervalTicks())return;
        budget.decisions--;decisions++;
        Vec3 desired=target(school,leader,formationIndex);
        List<WaterPoint> options=nav.adjacent(fish.cell());
        WaterPoint best=null;double bestScore=Double.NEGATIVE_INFINITY;
        for(WaterPoint option:options) {
            if(budget.checks<=0)break;
            budget.checks--;neighborChecks++;
            if(!nav.traversable(fish,option))continue;
            Vec3 proposed=option.swimmingPosition();
            double score;
            if(school.behavior()==FishSchool.Behavior.SCATTERED && school.disturbance()!=null)
                score=proposed.distanceSquared(school.disturbance());
            else if(formationIndex>0) {
                score=-proposed.distanceSquared(desired);
                // Tiny separation term, checking only the members of THIS 2..8-fish school.
                for(UUID otherId:school.orderedMembers()) {
                    if(otherId.equals(fish.id()))continue;
                    FishAgent other=pop.get(otherId);
                    if(other!=null && other.position().distanceSquared(proposed)<.55)score-=3;
                }
            } else {
                double dx=proposed.x()-fish.position().x(),dz=proposed.z()-fish.position().z();
                double align=dx*school.headingX()+dz*school.headingZ();
                score=.85*align - .025*proposed.distanceSquared(school.anchor());
                if(school.anchor().distanceSquared(proposed)>settings.roamRadius()*settings.roamRadius())score-=8;
            }
            score+=jitter(fish.id(),tick,option)*.12;
            if(score>bestScore){bestScore=score;best=option;}
        }
        if(formationIndex==0)school.decide(tick);
        boolean needsMovement=formationIndex==0 || school.behavior()==FishSchool.Behavior.SCATTERED
            || fish.position().distanceSquared(desired)>settings.formationGap()*settings.formationGap()*1.3;
        if(best!=null && needsMovement) {
            if(formationIndex==0)school.heading(best.x()-fish.cell().x(),best.z()-fish.cell().z(),tick);
            fish.travelTo(best);
        }
    }
    private Vec3 target(FishSchool school,FishAgent leader,int index) {
        double x=school.headingX(),z=school.headingZ();
        // Tight staggered V; offsets are never interpreted as teleport destinations.
        int rank=(index+1)/2;
        double side=index%2==0?1:-1;
        double behind=rank*settings.formationGap();
        double lateral=side*rank*settings.formationGap()*.55;
        return new Vec3(leader.position().x()-x*behind-z*lateral,
            leader.position().y(),leader.position().z()-z*behind+x*lateral);
    }
    private static double jitter(UUID fish,long tick,WaterPoint option) {
        long h=fish.getMostSignificantBits() ^ fish.getLeastSignificantBits();
        h ^= tick*0x9e3779b97f4a7c15L;
        h ^= (long)option.x()*0xbf58476d1ce4e5b9L ^ (long)option.z()*0x94d049bb133111ebL;
        h^=h>>>30;h*=0xbf58476d1ce4e5b9L;h^=h>>>27;
        return ((h>>>12)&65535)/65535.0;
    }
    public double speed(FishAgent agent) {
        UUID id=memberships.get(agent.id());FishSchool school=schools.get(id);
        return school!=null && !agent.id().equals(school.leaderId())
            ?settings.followerSpeed():settings.leaderSpeed();
    }
}
