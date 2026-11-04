package org.bukkit;
public enum Particle {
    SPLASH, BUBBLE_POP, HAPPY_VILLAGER, CLOUD, ENCHANT, REDSTONE;
    public record DustOptions(Color color, float size) {}
}
