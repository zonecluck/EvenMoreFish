package com.oheers.fish.fishing.simulator;
import java.util.*;import org.bukkit.entity.Player;import com.oheers.fish.fishing.simulator.presentation.UiProfile;
public class SimulatorModule { public UiProfile profile=UiProfile.BALANCED; public int changes; public void prefetchHud(UUID id){} public UiProfile ownHudProfile(UUID id){return profile;} public void setOwnHudProfile(UUID id,UiProfile p){profile=p;changes++;} }
