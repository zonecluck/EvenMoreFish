package org.bukkit.inventory;
/** Test-only model; never included in the plugin artifact. */
public final class ItemStack implements Cloneable {
    private final String type;
    private final int max;
    private int count;
    public ItemStack(String type,int count,int max){this.type=type;this.count=count;this.max=max;}
    public boolean isEmpty(){return count<=0 || "AIR".equals(type);}
    public int getAmount(){return count;}
    public void setAmount(int count){this.count=count;}
    public int getMaxStackSize(){return max;}
    public boolean isSimilar(ItemStack other){return other!=null && type.equals(other.type);}
    @Override public ItemStack clone(){return new ItemStack(type,count,max);}
}
