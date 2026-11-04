package com.oheers.fish.fishing.simulator.commit;

import org.bukkit.inventory.ItemStack;

/**
 * A conservative, single-slot inventory insertion plan for simulator catches.
 *
 * The old addItem() + drop-leftovers path could partially mutate inventory
 * and then report a completely failed delivery when dropping leftovers failed.
 * Simulator fish stacks are small: require one entire destination slot, and
 * choose a whole-stack ground drop when no single slot can hold the reward.
 * Neither this plan nor Inventory.setItem provides an ACID transaction against
 * external plugins that mutate inventories illegally/off-thread.
 */
public final class SingleSlotDeliveryPlanner {
    public record Plan(int slot, ItemStack contents) {}
    private SingleSlotDeliveryPlanner() {}

    public static Plan plan(ItemStack[] storage, ItemStack reward, int inventoryMaxStack) {
        if (storage == null || reward == null || reward.isEmpty() || reward.getAmount() <= 0 ||
            inventoryMaxStack <= 0) return null;
        int requested = reward.getAmount();
        int limit = Math.min(inventoryMaxStack, reward.getMaxStackSize());
        if (requested > limit) return null;

        // Prefer merging with an existing stack so spare slots remain free.
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack existing = storage[slot];
            if (existing == null || existing.isEmpty() || !existing.isSimilar(reward)) continue;
            int capacity = Math.min(limit, existing.getMaxStackSize());
            if (existing.getAmount() < 0 || requested > capacity - existing.getAmount()) continue;
            ItemStack replacement = existing.clone();
            replacement.setAmount(existing.getAmount() + requested);
            return new Plan(slot, replacement);
        }
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack existing = storage[slot];
            if (existing != null && !existing.isEmpty()) continue;
            return new Plan(slot, reward.clone());
        }
        return null;
    }
}
