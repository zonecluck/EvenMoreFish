package com.oheers.fish.utils.nbt;

import de.tr7zw.changeme.nbtapi.NBT;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

/**
 * Enum representing the different versions of NBT (Named Binary Tag) data.
 */
public enum NbtVersion {
    /**
     * Represents the legacy version of NBT data.
     */
    LEGACY,

    /**
     * Represents the version of NBT data that uses the nbt-api.
     */
    NBTAPI,

    /**
     * Represents the compatible version of NBT data.
     */
    COMPAT;

    /**
     * Returns the version of NBT data associated with the given ItemStack.
     *
     * @param itemStack The ItemStack to check.
     * @return The NbtVersion representing the version of the NBT data.
     */
    public static NbtVersion getVersion(final ItemStack itemStack) {
        return NBT.get(itemStack, nbt -> {
            if (nbt.hasTag(NbtKeys.EMF_COMPOUND)) {
                return NbtVersion.COMPAT; //def an emf item
            }
            if (nbt.hasTag(NbtKeys.PUBLIC_BUKKIT_VALUES)) {
                de.tr7zw.changeme.nbtapi.iface.ReadableNBT publicBukkitValues = nbt.getCompound(NbtKeys.PUBLIC_BUKKIT_VALUES);
                if (publicBukkitValues != null && hasAnyEmfKey(publicBukkitValues)) {
                    return NbtVersion.LEGACY;
                }
            }
            if (nbt.hasTag(NbtKeys.EMF_COMPOUND + ":" + NbtKeys.EMF_APPLIED_BAIT)) {
                return NbtVersion.NBTAPI;
            }
            return NbtVersion.COMPAT;
        });
    }

    /**
     * Returns the version of NBT data associated with the given BlockState.
     *
     * @param state The BlockState to check.
     * @return The NbtVersion representing the version of the NBT data.
     */
    public static NbtVersion getVersion(final BlockState state) {
        return NBT.get(state, nbt -> {
            if (nbt.hasTag(NbtKeys.EMF_COMPOUND)) {
                return NbtVersion.COMPAT; //def an emf item
            }
            if (nbt.hasTag(NbtKeys.PUBLIC_BUKKIT_VALUES)) {
                de.tr7zw.changeme.nbtapi.iface.ReadableNBT publicBukkitValues = nbt.getCompound(NbtKeys.PUBLIC_BUKKIT_VALUES);
                if (publicBukkitValues != null && hasAnyEmfKey(publicBukkitValues)) {
                    return NbtVersion.LEGACY;
                }
            }
            if (nbt.hasTag(NbtKeys.EMF_COMPOUND + ":" + NbtKeys.EMF_APPLIED_BAIT)) {
                return NbtVersion.NBTAPI;
            }
            return NbtVersion.COMPAT;
        });
    }

    private static boolean hasAnyEmfKey(final de.tr7zw.changeme.nbtapi.iface.ReadableNBT compound) {
        final String prefix = NbtKeys.EMF_COMPOUND + ":";
        for (String key : compound.getKeys()) {
            if (key.startsWith(prefix) || key.startsWith("evenmorefish:")) {
                return true;
            }
        }
        return false;
    }
}
