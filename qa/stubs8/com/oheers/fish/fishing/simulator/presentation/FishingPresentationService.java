package com.oheers.fish.fishing.simulator.presentation;
import java.util.UUID;
/** Test-only stub; actual presentation owner has independent JDK smoke tests. */
public final class FishingPresentationService {
 public boolean sound(UUID player){return true;}
 public boolean particles(UUID player){return true;}
 public boolean cinematic(UUID player){return true;}
 public UiProfile profile(UUID player){return UiProfile.BALANCED;}
 public void countdown(UUID player,UUID hook,long now,int remain,int total,int segments,String filled,String empty) {}
}
