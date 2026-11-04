package org.bukkit.event.inventory; import org.bukkit.inventory.*;
public class InventoryDragEvent {private InventoryView v; private boolean cancelled; public InventoryDragEvent(InventoryView v){this.v=v;} public InventoryView getView(){return v;} public void setCancelled(boolean b){cancelled=b;} public boolean isCancelled(){return cancelled;} }
