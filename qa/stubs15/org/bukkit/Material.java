package org.bukkit;
public enum Material {
    PAPER(true,false), COD(true,false), AIR(false,true), WATER(false,false);
    final boolean item,air;
    Material(boolean item, boolean air){this.item=item;this.air=air;}
    public static Material getMaterial(String key) {try {return valueOf(key);}catch(IllegalArgumentException x){return null;}}
    public boolean isItem(){return item;}
    public boolean isAir(){return air;}
}
