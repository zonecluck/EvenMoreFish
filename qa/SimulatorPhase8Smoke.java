import com.oheers.fish.fishing.simulator.effects.*;
import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.visual.*;
import com.oheers.fish.fishing.simulator.ai.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import java.util.*;

public final class SimulatorPhase8Smoke {
 private static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
 private static final class Transport implements PacketTransport {
   Set<Integer> visible=new HashSet<>();int spawns, moves, destroys;
   boolean failDestroy,partialSpawnFailure;
   public boolean available(){return true;}
   public boolean spawn(UUID v,FishAgent f){spawns++;visible.add(f.entityId());return !partialSpawnFailure;}
   public boolean teleport(UUID v,FishAgent f){moves++;return visible.contains(f.entityId());}
   public boolean destroy(UUID v,int id){destroys++;return !failDestroy && visible.remove(id);}
 }
 private static SimulatorEffectsSettings cfg(boolean cine,int max){return cfg(cine,max,12);}
 private static SimulatorEffectsSettings cfg(boolean cine,int max,int budget){
   var cue=new SimulatorEffectsSettings.Cue(null,.6f,1.0f,null,0);
   var preset=new SimulatorEffectsSettings.Preset(cue,cue,cue,cue,12,1.0);
   return new SimulatorEffectsSettings(true,true,5,12,"▰","▱",cine,max,budget,32,
     Map.of("common",preset),Map.of("legendary","common"),Map.of());
 }
 public static void main(String[] args){
   var start=new Vec3(0,64,0);var end=new Vec3(4,65,0);
   var arc=new CatchArc(start,end,1.5);
   check(arc.sample(0).equals(start),"start is exact");
   check(arc.sample(1).equals(end),"end is exact");
   check(arc.sample(.5).y()>65,"arc height has a visible rise");
   check(arc.sample(-5).equals(start)&&arc.sample(500).equals(end),"progress clamped");
   var world=new World();var other=new World();
   var player=new Player(UUID.randomUUID(),new Location(world,3,64,0));Bukkit.USERS.put(player.getUniqueId(),player);
   var second=new Player(UUID.randomUUID(),new Location(world,3,64,0));Bukkit.USERS.put(second.getUniqueId(),second);
   var transport=new Transport();
   var service=new PacketCatchCinematicService(transport);
   service.configure(cfg(true,1));
   var fish=new FishKey("Legendary","GoldenCarp");
   var preset=cfg(true,1).forFish("Legendary","GoldenCarp");
   var hook=new Location(world,0,64,0);
   check(service.start(player,hook,fish,FishVisualModel.COD,"lake",0,preset),"spawn first FX");
   check(!service.start(second,hook,fish,FishVisualModel.COD,"lake",0,preset),"max-concurrent enforced");
   for(int tick=1;tick<=12;tick++)service.tick(tick);
   check(service.active()==0 && transport.visible.isEmpty(),"all visual packet IDs destroyed");
   check(transport.spawns==1 && transport.destroys==1,"one spawn/destroy pair");
   check(service.start(second,hook,fish,FishVisualModel.COD,"lake",20,preset),"slot reclaimed after cinematic");
   second.teleport(new Location(other,0,64,0));service.tick(21);
   check(service.active()==0&&transport.visible.isEmpty(),"world-change cleanup");
   check(service.start(player,hook,fish,FishVisualModel.SALMON,"lake",40,preset),"restart FX");
   service.stop(player.getUniqueId());
   check(transport.visible.isEmpty(),"manual stop for quit/opt-out");
   var limited=new PacketCatchCinematicService(transport);
   limited.configure(cfg(true,2,2));
   second.teleport(new Location(world,3,64,0));
   check(limited.start(player,hook,fish,FishVisualModel.COD,"lake",60,preset),"2-packet spawn fits budget");
   check(!limited.start(second,hook,fish,FishVisualModel.COD,"lake",60,preset),"second spawn rejected by tick budget");
   int before=transport.moves;
   limited.tick(60);
   check(transport.moves==before,"same-tick teleport deferred because spawn spent budget");
   limited.tick(61);
   check(transport.moves>before,"budget resets the next tick");
   limited.clear();
   check(transport.visible.isEmpty(),"rate-limited spawns still have unconditional destroy cleanup");
   var reliable=new PacketCatchCinematicService(transport);
   reliable.configure(cfg(true,2));
   transport.partialSpawnFailure=true;
   transport.failDestroy=true;
   check(!reliable.start(player,hook,fish,FishVisualModel.COD,"lake",70,preset),
       "partial spawn must be rejected");
   check(reliable.pendingDestroys()==1 && !transport.visible.isEmpty(),
       "partial spawn needs compensating cleanup");
   check(!reliable.start(player,hook,fish,FishVisualModel.COD,"lake",71,preset),
       "new cinematic may not overwrite an unresolved ghost");
   reliable.tick(71);
   check(reliable.pendingDestroys()==1,"failed cleanup was prematurely forgotten");
   transport.partialSpawnFailure=false;
   transport.failDestroy=false;
   reliable.tick(72);
   check(reliable.pendingDestroys()==0 && transport.visible.isEmpty(),
       "retry should remove partial cinematic spawn");
   check(reliable.start(player,hook,fish,FishVisualModel.COD,"lake",73,preset),
       "cinematic did not recover after orphan cleanup");
   transport.failDestroy=true;
   reliable.stop(player.getUniqueId());
   check(reliable.pendingDestroys()==1,"failed cinematic destroy must be retried");
   transport.failDestroy=false;
   reliable.tick(74);
   check(reliable.pendingDestroys()==0 && transport.visible.isEmpty(),
       "cinematic stop cleanup retry failed");
   var fx=new SimulatorEffectsService(transport,new com.oheers.fish.fishing.simulator.presentation.FishingPresentationService());
   fx.configure(cfg(true,2));
   fx.phase(player,UUID.randomUUID(),FishAiDirector.Phase.BITE_READY,fish,50,26,hook);
   check(fx.activeHud()==1,"start countdown HUD");
   fx.finished(player,UUID.randomUUID(),FishAiDirector.Outcome.SUCCESS_PREVIEW_ONLY,fish,hook,"lake",FishVisualModel.COD,50);
   check(fx.activeHud()==0 && fx.activeCinematics()==1,"finish removes HUD, visual only");
   fx.setEffectsEnabled(player.getUniqueId(),false);
   check(!fx.effectsEnabledFor(player.getUniqueId()) && fx.activeCinematics()==0 && transport.visible.isEmpty(),
       "opt-out stops cinematic immediately");
   fx.clear();
   check(fx.activeCinematics()==0,"reload/disable cleanup");
   System.out.println("PASS Phase 8 cinematic arc, packet budget, bounded concurrency, cleanup, player opt-out, HUD lifecycle");
 }
}
