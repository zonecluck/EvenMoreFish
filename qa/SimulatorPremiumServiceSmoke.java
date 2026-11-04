import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.SimulatorMessages;
import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import com.oheers.fish.fishing.simulator.presentation.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.nio.file.Files;import java.nio.file.Path;import java.util.UUID;

public final class SimulatorPremiumServiceSmoke {
 private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception{
  Path folder=Files.createTempDirectory("emf-visual-service-");
  EvenMoreFish plugin=new EvenMoreFish(folder.toFile());
  FishingPresentationService hud=new FishingPresentationService(plugin);
  hud.configure(new SimulatorPresentationSettings(true,5,8,35,false,UiProfile.BALANCED));
  UUID player=UUID.randomUUID(),cast=UUID.randomUUID();
  Bukkit.ONLINE.put(player,new Player(player));
  hud.begin(player,cast);
  hud.phase(player,cast,FishAiDirector.Phase.INTERESTED,0,26);
  hud.tick(0);
  check(SimulatorMessages.count==0,"interest should be world-space, not HUD");
  hud.phase(player,cast,FishAiDirector.Phase.BITE_READY,1,26);
  hud.countdown(player,cast,1,26,26,12,"▰","▱");
  hud.tick(1);
  check(SimulatorMessages.count==1&&"presentation-bite".equals(SimulatorMessages.lastKey),"one bite HUD despite dual intents");
  hud.countdown(player,cast,2,25,26,12,"▰","▱");hud.tick(2);
  check(SimulatorMessages.count==1,"countdown throttled");
  hud.result(player,cast,FishAiDirector.Outcome.SUCCESS_PREVIEW_ONLY,3);hud.closeSession(player,cast);hud.tick(3);
  check(SimulatorMessages.count==2&&"ai-success-preview".equals(SimulatorMessages.lastKey),"result preempts and outlives session");
  hud.changeProfile(player,UiProfile.OFF);
  check(!hud.canShow(player),"OFF suppresses all HUD");
  hud.changeProfile(player,UiProfile.REDUCED_MOTION);
  check(hud.canShow(player)&&!hud.cinematic(player),"reduced motion still has bite feedback");
  hud.quit(player);hud.shutdown();
  check(Files.readString(folder.resolve("simulator/player-prefs/"+player+".yml")).contains("REDUCED_MOTION"),"save flushed at shutdown");
  System.out.println("PASS: real FishingPresentationService compiled against Bukkit/EMF stubs; arbitration, lifecycle and async preference persistence");
 }
}
