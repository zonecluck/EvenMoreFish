package org.bukkit;
import org.bukkit.entity.Player;import java.util.Map;import java.util.HashMap;import java.util.UUID;
public final class Bukkit {
 public static final Map<UUID,Player> ONLINE=new HashMap<>();
 public static Player getPlayer(UUID id){return ONLINE.get(id);}
}
