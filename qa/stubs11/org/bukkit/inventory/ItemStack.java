package org.bukkit.inventory;
import org.bukkit.Material; import org.bukkit.inventory.meta.ItemMeta;
public class ItemStack { public final Material material; ItemMeta meta=new ItemMeta(); public ItemStack(Material m){material=m;} public ItemMeta getItemMeta(){return meta;} public void setItemMeta(ItemMeta m){meta=m;} }
