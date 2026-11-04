package com.oheers.fish.fishing.rods;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Resolves MMOItems Custom Stats from Fishing Rods for EvenMoreFish integration.
 *
 * Supported MMOItems Custom Stats (percentage values in MMOItems, e.g. 25.0 = +25%):
 * - CUSTOM_FISHING_LUCK       -> luckModifier (0.25 = +25% rarity weight boost)
 * - CUSTOM_FISHING_SPEED      -> lureModifier (0.25 = -25% hook wait time)
 * - CUSTOM_DOUBLE_CATCH       -> doubleCatchChance (0.25 = 25% chance for 2x fish)
 * - CUSTOM_RARE_FISH_CHANCE   -> rareFishModifier (0.25 = +25% extra weight for Epic/Legendary/Mythic/Anomaly)
 * - CUSTOM_FISHING_EXP        -> expBonus (0.25 = +25% fishing XP multiplier)
 */
public record MMOItemsRodStats(
    double luckModifier,
    double lureModifier,
    double doubleCatchChance,
    double rareFishModifier,
    double expBonus
) {
    public static final MMOItemsRodStats ZERO = new MMOItemsRodStats(0.0, 0.0, 0.0, 0.0, 0.0);

    private static final String MMO_NAMESPACE = "mmoitems";
    private static final String KEY_ITEM_ID = "MMOITEMS_ITEM_ID";
    private static final String KEY_FISHING_LUCK = "MMOITEMS_CUSTOM_FISHING_LUCK";
    private static final String KEY_FISHING_SPEED = "MMOITEMS_CUSTOM_FISHING_SPEED";
    private static final String KEY_DOUBLE_CATCH = "MMOITEMS_CUSTOM_DOUBLE_CATCH";
    private static final String KEY_RARE_FISH = "MMOITEMS_CUSTOM_RARE_FISH_CHANCE";
    private static final String KEY_FISHING_EXP = "MMOITEMS_CUSTOM_FISHING_EXP";
    private static final String KEY_ADD_EXP_FISHING = "MMOITEMS_ADDITIONAL_EXPERIENCE_FISHING";

    public boolean isEmpty() {
        return luckModifier <= 0.0
            && lureModifier <= 0.0
            && doubleCatchChance <= 0.0
            && rareFishModifier <= 0.0
            && expBonus <= 0.0;
    }

    public static @NotNull MMOItemsRodStats resolve(@Nullable Player player) {
        if (player == null) {
            return ZERO;
        }
        try {
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main.getType() == Material.FISHING_ROD) {
                return resolve(main);
            }
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off.getType() == Material.FISHING_ROD) {
                return resolve(off);
            }
        } catch (Throwable ignored) {
        }
        return ZERO;
    }

    public static @NotNull MMOItemsRodStats resolve(@Nullable ItemStack item) {
        if (item == null) {
            return ZERO;
        }
        try {
            if (item.getType() != Material.FISHING_ROD || item.isEmpty()) {
                return ZERO;
            }
        } catch (Throwable ignored) {
            return ZERO;
        }

        double luckPct = readStatDouble(item, KEY_FISHING_LUCK);
        double speedPct = readStatDouble(item, KEY_FISHING_SPEED);
        double doublePct = readStatDouble(item, KEY_DOUBLE_CATCH);
        double rarePct = readStatDouble(item, KEY_RARE_FISH);
        double expPct = readStatDouble(item, KEY_FISHING_EXP) + readStatDouble(item, KEY_ADD_EXP_FISHING);

        if (luckPct <= 0.0 && speedPct <= 0.0 && doublePct <= 0.0 && rarePct <= 0.0 && expPct <= 0.0) {
            String mmoId = readStringTag(item, KEY_ITEM_ID);
            if (mmoId != null && !mmoId.isBlank()) {
                MMOItemsRodStats fallback = fallbackByItemId(mmoId.trim().toUpperCase(Locale.ROOT));
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            }
            return ZERO;
        }

        return new MMOItemsRodStats(
            clamp(luckPct / 100.0, 0.0, 5.0),
            clamp(speedPct / 100.0, 0.0, 0.85),
            clamp(doublePct / 100.0, 0.0, 1.0),
            clamp(rarePct / 100.0, 0.0, 5.0),
            clamp(expPct / 100.0, 0.0, 10.0)
        );
    }

    public static @Nullable String getMMOItemId(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            if (item.isEmpty()) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
        return readStringTag(item, KEY_ITEM_ID);
    }

    private static double readStatDouble(@NotNull ItemStack item, @NotNull String upperKey) {
        // 1. Check PersistentDataContainer (MythicLib 1.20.5+ / 1.21+ stores tags under mmoitems:<lower_key>)
        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    for (String keyCandidate : new String[]{
                        upperKey.toLowerCase(Locale.ROOT),
                        upperKey.substring("MMOITEMS_".length()).toLowerCase(Locale.ROOT)
                    }) {
                        NamespacedKey nsKey = new NamespacedKey(MMO_NAMESPACE, keyCandidate);
                        Double d = pdc.get(nsKey, PersistentDataType.DOUBLE);
                        if (d != null && Double.isFinite(d) && d != 0.0) {
                            return d;
                        }
                        Float f = pdc.get(nsKey, PersistentDataType.FLOAT);
                        if (f != null && Float.isFinite(f) && f != 0.0f) {
                            return f.doubleValue();
                        }
                        Integer i = pdc.get(nsKey, PersistentDataType.INTEGER);
                        if (i != null && i != 0) {
                            return i.doubleValue();
                        }
                        String s = pdc.get(nsKey, PersistentDataType.STRING);
                        if (s != null && !s.isBlank()) {
                            try {
                                return Double.parseDouble(s.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Check raw NBT & PublicBukkitValues via NBT-API
        try {
            Double nbtVal = NBT.get(item, nbt -> {
                if (nbt.hasTag(upperKey)) {
                    double v = nbt.getDouble(upperKey);
                    if (v != 0.0) {
                        return v;
                    }
                }
                ReadableNBT pbv = nbt.getCompound("PublicBukkitValues");
                if (pbv != null) {
                    String pbvKey = MMO_NAMESPACE + ":" + upperKey.toLowerCase(Locale.ROOT);
                    if (pbv.hasTag(pbvKey)) {
                        double v = pbv.getDouble(pbvKey);
                        if (v != 0.0) {
                            return v;
                        }
                    }
                }
                return 0.0;
            });
            if (nbtVal != null && Double.isFinite(nbtVal)) {
                return nbtVal;
            }
        } catch (Throwable ignored) {
        }

        return 0.0;
    }

    private static @Nullable String readStringTag(@NotNull ItemStack item, @NotNull String upperKey) {
        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    for (String keyCandidate : new String[]{
                        upperKey.toLowerCase(Locale.ROOT),
                        upperKey.substring("MMOITEMS_".length()).toLowerCase(Locale.ROOT)
                    }) {
                        NamespacedKey nsKey = new NamespacedKey(MMO_NAMESPACE, keyCandidate);
                        String val = pdc.get(nsKey, PersistentDataType.STRING);
                        if (val != null && !val.isBlank()) {
                            return val;
                        }
                    }
                    for (NamespacedKey key : pdc.getKeys()) {
                        String k = key.getKey().toLowerCase(Locale.ROOT);
                        if (k.equals(upperKey.toLowerCase(Locale.ROOT))
                                || k.equals(upperKey.substring("MMOITEMS_".length()).toLowerCase(Locale.ROOT))) {
                            String val = pdc.get(key, PersistentDataType.STRING);
                            if (val != null && !val.isBlank()) {
                                return val;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> nbtItemClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            Object nbtItem = nbtItemClass.getMethod("get", ItemStack.class).invoke(null, item);
            if (nbtItem != null) {
                Object val = nbtItemClass.getMethod("getString", String.class).invoke(nbtItem, upperKey);
                if (val instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            return NBT.get(item, nbt -> {
                if (nbt.hasTag(upperKey)) {
                    String s = nbt.getString(upperKey);
                    if (s != null && !s.isBlank()) {
                        return s;
                    }
                }
                ReadableNBT pbv = nbt.getCompound("PublicBukkitValues");
                if (pbv != null) {
                    String pbvKey = MMO_NAMESPACE + ":" + upperKey.toLowerCase(Locale.ROOT);
                    if (pbv.hasTag(pbvKey)) {
                        String s = pbv.getString(pbvKey);
                        if (s != null && !s.isBlank()) {
                            return s;
                        }
                    }
                }
                return null;
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Fallback stats for pre-existing MMOItems rods in player inventories before NBT refresh.
     */
    static @NotNull MMOItemsRodStats fallbackByItemId(@NotNull String id) {
        return switch (id) {
            case "FISHING_ROD_1" -> new MMOItemsRodStats(0.12, 0.10, 0.06, 0.05, 0.15);
            case "FISHING_ROD_2" -> new MMOItemsRodStats(0.25, 0.20, 0.12, 0.12, 0.30);
            case "FISHING_ROD_3" -> new MMOItemsRodStats(0.42, 0.32, 0.20, 0.22, 0.50);
            case "FISHING_ROD_4" -> new MMOItemsRodStats(0.65, 0.45, 0.30, 0.35, 0.80);
            case "FISHING_ROD_5" -> new MMOItemsRodStats(1.00, 0.60, 0.45, 0.55, 1.20);
            default -> {
                if (id.startsWith("ROD_")) {
                    try {
                        int lvl = Integer.parseInt(id.substring(4));
                        if (lvl >= 1 && lvl <= 50) {
                            double progress = (lvl - 1) / 49.0;
                            double luck = round2(0.05 + progress * 0.80);
                            double speed = round2(0.08 + progress * 0.47);
                            double dbl = round2(0.03 + progress * 0.37);
                            double rare = round2(0.02 + progress * 0.48);
                            double exp = round2(0.10 + progress * 0.90);
                            yield new MMOItemsRodStats(luck, speed, dbl, rare, exp);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                yield ZERO;
            }
        };
    }

    private static double clamp(double val, double min, double max) {
        if (!Double.isFinite(val)) {
            return min;
        }
        return Math.max(min, Math.min(max, val));
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
