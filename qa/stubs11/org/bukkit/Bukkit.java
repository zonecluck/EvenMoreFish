package org.bukkit;
import java.util.*; import org.bukkit.entity.Player; import org.bukkit.inventory.*; import net.kyori.adventure.text.Component;
public class Bukkit { public static Map<UUID,Player> users=new HashMap<>(); public static Player getPlayer(UUID id){return users.get(id);} public static Inventory createInventory(InventoryHolder h,int size,Component title){return new Inventory(h,size);} }
