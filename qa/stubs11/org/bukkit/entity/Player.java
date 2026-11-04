package org.bukkit.entity;
import java.util.UUID;
import org.bukkit.*;import org.bukkit.inventory.*;
public class Player { private final UUID id; private InventoryView view;
public Player(UUID id){this.id=id;} public UUID getUniqueId(){return id;} public boolean hasPermission(String s){return true;} public boolean isOnline(){return true;}
public void openInventory(Inventory i){view=new InventoryView(i);} public InventoryView getOpenInventory(){return view;}
public void closeInventory(){view=new InventoryView(new Inventory(null,9));} public Location getLocation(){return new Location();} public void playSound(Location l,Sound s,float v,float p){} }
