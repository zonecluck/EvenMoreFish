package org.bukkit.event.inventory; import org.bukkit.inventory.*;
public class InventoryCloseEvent {private Inventory inv; public InventoryCloseEvent(Inventory i){inv=i;} public Inventory getInventory(){return inv;} }
