import com.oheers.fish.fishing.simulator.effects.*;
import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.visual.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import java.util.*;

public class SimulatorPremiumPhase11Smoke {
    static void check(boolean b, String message){ if(!b)throw new AssertionError(message); }
    static class PacketSink implements PacketTransport {
        Map<UUID,Set<Integer>> clients = new HashMap<>();
        int spawns,teleports,destroys;
        boolean partialSpawn,failDestroy,failTeleport;
        public boolean available(){return true;}
        public boolean spawn(UUID viewer, FishAgent fish){
            spawns++;clients.computeIfAbsent(viewer,k->new HashSet<>()).add(fish.entityId());
            return !partialSpawn;
        }
        public boolean teleport(UUID viewer,FishAgent fish){
            teleports++;return !failTeleport && clients.getOrDefault(viewer,Set.of()).contains(fish.entityId());
        }
        public boolean destroy(UUID viewer,int id){
            destroys++;return !failDestroy && clients.getOrDefault(viewer,new HashSet<>()).remove(id);
        }
        int visible(){return clients.values().stream().mapToInt(Set::size).sum();}
    }
    static SimulatorEffectsSettings effects(){
        var cue=new SimulatorEffectsSettings.Cue(null,.8f,1f,null,0);
        var p=new SimulatorEffectsSettings.Preset(cue,cue,cue,cue,16,.8);
        return new SimulatorEffectsSettings(true,true,5,12,"▰","▱",true,4,12,32,
            Map.of("common",p),Map.of("legendary","common"),Map.of());
    }
    static PremiumCinematicSettings scenes(){
        var d=new PremiumCinematicTimeline.Durations(3,4,8,8,8,3);
        var profile=new PremiumCinematicSettings.Profile(d,1.3,.6,3,null,Map.of());
        return new PremiumCinematicSettings(true,8,Map.of("common",profile,"rare",profile,
            "epic",profile,"legendary",profile),Map.of("legendary","legendary"),Map.of());
    }
    public static void main(String[] args){
        var world=new World();var player=new Player(UUID.randomUUID(),new Location(world,4,64,0));
        Bukkit.USERS.put(player.getUniqueId(),player);
        Location water=new Location(world,0,63,0);
        var key=new FishKey("Legendary","GoldFish");
        var p=scenes().profile(key);
        var timeline=new PremiumCinematicTimeline(p.durations(),new Vec3(0,64,0),new Vec3(4,65,0),p.arcHeight(),p.orbit());
        check(timeline.duration()==34,"scene duration");
        check(timeline.sample(0).stage()==PremiumCinematicTimeline.Stage.ANTICIPATION,"stage starts from anticipation");
        check(timeline.sample(3).stage()==PremiumCinematicTimeline.Stage.VORTEX,"vortex starts on time");
        check(timeline.sample(7).stage()==PremiumCinematicTimeline.Stage.SURFACE_BREAK,"surface begins after charge");
        check(timeline.sample(15).stage()==PremiumCinematicTimeline.Stage.HERO_REVEAL,"reveal starts");
        check(timeline.sample(23).stage()==PremiumCinematicTimeline.Stage.DELIVERY,"delivery starts");
        check(timeline.sample(31).stage()==PremiumCinematicTimeline.Stage.FADE,"fade starts");
        check(!timeline.sample(3).visible() && timeline.sample(7).visible(),"no fish before surface breach");
        for(int t=1;t<timeline.duration();t++){
            Vec3 a=timeline.sample(t-1).position(),b=timeline.sample(t).position();
            check(a.distanceSquared(b)<3,"motion snap at tick "+t);
        }
        check(timeline.sample(999).position().equals(new Vec3(4,65,0)),"clamped finish");
        boolean rejected=false;
        try{new PremiumCinematicTimeline.Durations(20,20,20,20,20,20);}catch(IllegalArgumentException ex){rejected=true;}
        check(rejected,"invalid total length rejected");
        PacketSink transport=new PacketSink();
        PacketCatchCinematicService service=new PacketCatchCinematicService(transport);
        var stages=new ArrayList<PremiumCinematicTimeline.Stage>();
        service.configure(effects(),scenes(),new PacketCatchCinematicService.StageFxSink(){
            public void stage(Player viewer,Location origin,PremiumCinematicTimeline.Stage stage,
                    PremiumCinematicSettings.Profile profile,FishKey fish){stages.add(stage);}
            public void trail(Player viewer,Location point,PremiumCinematicSettings.Profile profile){}
        });
        check(service.start(player,water,key,FishVisualModel.COD,"lake",0,effects().forFish("legendary","GoldFish")),"premium start");
        check(transport.spawns==0 && transport.visible()==0,"no premature fish before breach");
        for(int t=0;t<=34;t++){
            service.tick(t);
            check(transport.teleports + 2*transport.spawns <= 12*(t+1),"packet budget violated");
        }
        check(stages.containsAll(List.of(PremiumCinematicTimeline.Stage.ANTICIPATION,
            PremiumCinematicTimeline.Stage.VORTEX,PremiumCinematicTimeline.Stage.SURFACE_BREAK,
            PremiumCinematicTimeline.Stage.HERO_REVEAL,PremiumCinematicTimeline.Stage.DELIVERY,
            PremiumCinematicTimeline.Stage.FADE)),"missing premium stage cues: "+stages);
        check(transport.spawns==1 && transport.destroys==1 && transport.visible()==0,"spawn/destroy exactly once");
        // Partial spawn must enter the orphan retry queue; never display a duplicate.
        transport.partialSpawn=true;transport.failDestroy=true;
        check(service.preview(player,water,key,FishVisualModel.COD,100,"legendary"),"admin preview started");
        for(int t=100;t<=107;t++)service.tick(t);
        check(service.pendingDestroys()==1 && transport.visible()==1,"partial spawn must remain tracked");
        check(!service.preview(player,water,key,FishVisualModel.COD,108,"legendary"),"orphan should block replay");
        transport.partialSpawn=false;transport.failDestroy=false;
        service.tick(109);
        check(service.pendingDestroys()==0 && transport.visible()==0,"cleanup retry recovered orphan");
        check(service.preview(player,water,key,FishVisualModel.COD,120,"epic"),"preview after recovery");
        service.stop(player.getUniqueId());
        check(service.active()==0 && transport.visible()==0,"manual skip cleans entity before/after spawn");
        System.out.println("PASS Phase 11: 6-stage timeline, continuity, packet budget, admin preview, partial spawn retry, cleanup");
    }
}
