package com.oheers.fish.fishing.simulator;
import org.bukkit.entity.Player;
import java.util.Map;
public final class SimulatorMessages {
 public static int bars;
 public static int titles;
 public static void title(Player player,String first,String second,Map<String,String> vars){titles++;}
 public static void actionBar(Player player,String key,Map<String,String> vars){bars++;}
}
