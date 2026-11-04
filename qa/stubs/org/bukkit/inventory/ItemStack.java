package org.bukkit.inventory;
public class ItemStack implements Cloneable {
    public int marker;
    public ItemStack(int marker) { this.marker = marker; }
    public ItemStack clone() { return new ItemStack(marker); }
}
