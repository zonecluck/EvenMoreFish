package org.bukkit;
import org.bukkit.entity.Player;
import java.util.*;
public final class Bukkit {
 public static final Map<UUID,Player> USERS=new HashMap<>();
 public static Player getPlayer(UUID id){return USERS.get(id);}
 public static int currentTick;
 public static int getCurrentTick(){return currentTick;}
}
