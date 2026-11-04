package org.bukkit.event.inventory;
import org.bukkit.entity.Player;import org.bukkit.inventory.*;
public class InventoryClickEvent {private InventoryView view; private Player player;private int slot;private ClickType click;private boolean cancel;
public InventoryClickEvent(InventoryView v,Player p,int slot,ClickType c){view=v;player=p;this.slot=slot;click=c;}
public InventoryView getView(){return view;} public Player getWhoClicked(){return player;} public int getRawSlot(){return slot;} public ClickType getClick(){return click;} public void setCancelled(boolean b){cancel=b;} public boolean isCancelled(){return cancel;} }
