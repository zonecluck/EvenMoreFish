package com.oheers.fish.fishing.simulator;
import org.bukkit.entity.Player;import java.util.Map;
public final class SimulatorMessages {
 public static String lastKey;public static int count;
 public static void actionBar(Player player,String key,Map<String,String> variables){count++;lastKey=key;}
}
