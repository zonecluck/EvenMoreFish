package com.oheers.fish.selling;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Predicate;

/** Plans a complete storage-inventory replacement without consuming inputs or dropping outputs. */
public final class FishBarterPlan {
    private FishBarterPlan() {}

    public static Optional<ItemStack[]> create(ItemStack[] current, Predicate<ItemStack> accepted,
                                               int required, ItemStack reward, int rewardAmount) {
        if (required < 1 || rewardAmount < 1 || reward == null || reward.getType().isAir()) {
            throw new IllegalArgumentException("Invalid barter amount or reward");
        }
        ItemStack[] planned = copy(current);
        int remaining = required;
        for (int slot = 0; slot < planned.length && remaining > 0; slot++) {
            ItemStack item = planned[slot];
            if (item == null || item.getType().isAir() || !accepted.test(item)) continue;
            int taken = Math.min(remaining, item.getAmount());
            remaining -= taken;
            if (taken == item.getAmount()) planned[slot] = null;
            else item.setAmount(item.getAmount() - taken);
        }
        if (remaining != 0) return Optional.empty();
        int maximum = Math.max(1, Math.min(64, reward.getMaxStackSize()));
        remaining = rewardAmount;
        for (int slot = 0; slot < planned.length && remaining > 0; slot++) {
            ItemStack item = planned[slot];
            if (item == null || !item.isSimilar(reward)) continue;
            int moved = Math.min(remaining, Math.max(0, maximum - item.getAmount()));
            item.setAmount(item.getAmount() + moved);
            remaining -= moved;
        }
        for (int slot = 0; slot < planned.length && remaining > 0; slot++) {
            if (planned[slot] != null && !planned[slot].getType().isAir()) continue;
            int moved = Math.min(remaining, maximum);
            planned[slot] = reward.clone();
            planned[slot].setAmount(moved);
            remaining -= moved;
        }
        return remaining == 0 ? Optional.of(planned) : Optional.empty();
    }

    public static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) result[i] = items[i] == null ? null : items[i].clone();
        return result;
    }
}
