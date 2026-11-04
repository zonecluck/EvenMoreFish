package org.bukkit.inventory;
public class Inventory { private InventoryHolder holder; private ItemStack[] slots;
public Inventory(InventoryHolder h,int n){holder=h;slots=new ItemStack[n];} public void clear(){java.util.Arrays.fill(slots,null);} public void setItem(int n,ItemStack i){slots[n]=i;} public int getSize(){return slots.length;} public InventoryHolder getHolder(){return holder;} }
