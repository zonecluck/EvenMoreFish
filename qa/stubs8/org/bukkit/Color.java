package org.bukkit;
/** API shape for isolated smoke only; actual Paper signatures are checked by Gradle. */
public record Color(int red, int green, int blue) {
    public static Color fromRGB(int red, int green, int blue) { return new Color(red, green, blue); }
}
