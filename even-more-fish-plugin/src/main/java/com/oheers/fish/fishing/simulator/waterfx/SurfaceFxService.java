package com.oheers.fish.fishing.simulator.waterfx;

import com.oheers.fish.fishing.simulator.behavior.FishPersonality;
import com.oheers.fish.fishing.simulator.behavior.FishPersonalityService;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.Vec3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Pure owner-thread FX intent scheduler. Each emission is per-viewer only, separate
 * from Fish AI and reward code. Packet/particle emission is delegated to an adapter.
 */
public final class SurfaceFxService {
    public enum Kind { BUBBLE_TRAIL, RIPPLE, LURE_PULSE, BITE_DISTURBANCE }
    public record Limits(int intervalTicks,int maxParticlesPerTick,int maxParticlesPerViewer,
                         int candidatesPerViewer,double radius) {
        public Limits {
            if(intervalTicks<2||intervalTicks>80||maxParticlesPerTick<1||maxParticlesPerTick>128||
                maxParticlesPerViewer<1||maxParticlesPerViewer>32||maxParticlesPerViewer>maxParticlesPerTick||
                candidatesPerViewer<1||candidatesPerViewer>160||!Double.isFinite(radius)||radius<3||radius>32)
                throw new IllegalArgumentException("Invalid water FX limits");
        }
    }
    public record Viewer(UUID id,UUID worldId,String zoneId,Vec3 position,boolean allowsParticles) { }
    public record Cue(Kind kind,Vec3 position,int particles) { }
    @FunctionalInterface public interface Emitter { boolean send(UUID viewer,Cue cue); }
    private record Signal(UUID worldId,String zone,Vec3 position,Kind kind,long untilTick) { }

    private final Limits limits;
    private final Map<UUID,Signal> pending = new HashMap<>();
    private long particles, emissions, failedEmissions, budgetDrops;
    public SurfaceFxService(Limits limits){this.limits=Objects.requireNonNull(limits);}
    public void signal(UUID owner,UUID world,String zone,Vec3 position,Kind kind,long tick) {
        if(owner==null||world==null||zone==null||position==null||kind==null)return;
        if(pending.size()>=64 && !pending.containsKey(owner)) {budgetDrops++;return;}
        pending.put(owner,new Signal(world,zone,position,kind,tick+40));
    }
    public void forgetViewer(UUID viewer){pending.remove(viewer);}
    public void clear(){pending.clear();}
    public long particles(){return particles;}
    public long emissions(){return emissions;}
    public long failures(){return failedEmissions;}
    public long dropped(){return budgetDrops;}

    public void tick(long tick,List<Viewer> viewers,List<FishAgent> agents,
                     FishPersonalityService personalities,Emitter emitter) {
        tick(tick,viewers,agents,personalities,(viewer,fish)->true,emitter);
    }
    public void tick(long tick,List<Viewer> viewers,List<FishAgent> agents,
                     FishPersonalityService personalities,BiPredicate<UUID,UUID> clientCanSee,
                     Emitter emitter) {
        Objects.requireNonNull(viewers);Objects.requireNonNull(agents);
        Objects.requireNonNull(clientCanSee);Objects.requireNonNull(emitter);
        pending.entrySet().removeIf(e->e.getValue().untilTick()<tick);
        if(tick%limits.intervalTicks()!=0)return;
        int total=0;
        // Rotate the starting viewer so a full global cap cannot starve the same players.
        int viewerStart=viewers.isEmpty()?0:(int)((tick/limits.intervalTicks())%viewers.size());
        for(int v=0;v<viewers.size();v++) {
            Viewer viewer=viewers.get((viewerStart+v)%viewers.size());
            if(!viewer.allowsParticles())continue;
            int used=0;
            Signal signal=pending.remove(viewer.id());
            if(signal!=null && signal.worldId().equals(viewer.worldId()) && signal.zone().equals(viewer.zoneId()) &&
                signal.position().distanceSquared(viewer.position())<=limits.radius()*limits.radius()) {
                int count=Math.min(signal.kind()==Kind.BITE_DISTURBANCE?7:5,
                    Math.min(limits.maxParticlesPerViewer()-used,limits.maxParticlesPerTick()-total));
                if(count>0) {
                    dispatch(emitter,viewer.id(),new Cue(signal.kind(),signal.position(),count));
                    total+=count;used+=count;
                } else budgetDrops++;
            }
            // Start at a rotating position so the same first agents do not monopolize FX.
            if(agents.isEmpty())continue;
            int count=Math.min(agents.size(),limits.candidatesPerViewer());
            int start=(int)((tick/limits.intervalTicks())%agents.size());
            for(int i=0;i<count;i++) {
                if(used>=limits.maxParticlesPerViewer()||total>=limits.maxParticlesPerTick()) {
                    budgetDrops++;break;
                }
                FishAgent fish=agents.get((start+i)%agents.size());
                if(fish.state()==FishAgent.State.DESPAWNED||fish.state()==FishAgent.State.CAUGHT||
                    !fish.worldId().equals(viewer.worldId()) || !fish.zoneId().equals(viewer.zoneId()) ||
                    !fish.visibleTo(viewer.id()) || !clientCanSee.test(viewer.id(),fish.id()) ||
                    fish.cell()==null ||
                    fish.position().distanceSquared(viewer.position())>limits.radius()*limits.radius()) continue;
                Kind kind;
                int desired;
                if(fish.state()==FishAgent.State.APPROACHING) {kind=Kind.BUBBLE_TRAIL;desired=2;}
                else if(fish.state()==FishAgent.State.WANDERING && personalities!=null &&
                        personalities.of(fish)==FishPersonality.MYTHIC) {kind=Kind.RIPPLE;desired=4;}
                else if(fish.state()==FishAgent.State.WANDERING && (tick/limits.intervalTicks())%3==0) {
                    kind=Kind.BUBBLE_TRAIL;desired=1;
                } else continue;
                int allowed=Math.min(desired,Math.min(limits.maxParticlesPerViewer()-used,
                    limits.maxParticlesPerTick()-total));
                if(allowed<=0) {budgetDrops++;break;}
                dispatch(emitter,viewer.id(),new Cue(kind,fish.position(),allowed));
                total+=allowed;used+=allowed;
            }
        }
    }
    private void dispatch(Emitter emitter,UUID viewer,Cue cue) {
        try {if(emitter.send(viewer,cue)) {particles+=cue.particles();emissions++;}
             else failedEmissions++;}
        catch(RuntimeException exception) {failedEmissions++;}
    }
}
