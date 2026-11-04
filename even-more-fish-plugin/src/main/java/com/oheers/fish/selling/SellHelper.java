package com.oheers.fish.selling;

import com.devskiller.friendly_id.FriendlyId;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.EMFFishSellEvent;
import com.oheers.fish.api.economy.Economy;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.DatabaseUtil;
import com.oheers.fish.database.data.manager.DataManager;
import com.oheers.fish.database.model.user.UserReport;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.messages.abstracted.EMFMessage;
import de.themoep.inventorygui.GuiStorageElement;
import de.themoep.inventorygui.InventoryGui;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SellHelper {

    private final @NotNull Player player;
    private final @NotNull List<SoldFish> fish;
    private final boolean includesPlayerStorage;

    public int getCount() {
        return count;
    }

    public double getSaleValue() {
        return saleValue;
    }

    private final double saleValue;
    private final int count;

    public SellHelper(@NotNull Inventory inventory, @NotNull Player player, boolean removeFromInventory) {
        this.player = player;
        this.includesPlayerStorage = inventory instanceof PlayerInventory;
        this.fish = fetchFish(inventory, removeFromInventory);
        this.saleValue = fish.stream().mapToDouble(SoldFish::getTotalValue).sum();
        this.count = fish.stream().mapToInt(SoldFish::getAmount).sum();
    }

    public SellHelper(@Nullable ItemStack @NotNull[] itemStacks, @NotNull Player player, boolean removeStacks) {
        this.player = player;
        this.includesPlayerStorage = false;
        this.fish = fetchFish(itemStacks, removeStacks);
        this.saleValue = fish.stream().mapToDouble(SoldFish::getTotalValue).sum();
        this.count = fish.stream().mapToInt(SoldFish::getAmount).sum();
    }

    public SellHelper(@NotNull Inventory inventory, @NotNull Player player) {
        this(inventory, player, true);
    }

    public SellHelper(@Nullable ItemStack @NotNull[] itemStacks, @NotNull Player player) {
        this(itemStacks, player, true);
    }

    /**
     * @deprecated use {@link #sell()} instead
     */
    @Deprecated(since = "2.1.4", forRemoval = true)
    public void sellFish() {
        sell();
    }

    public void sell() {
        if (!Economy.getInstance().isEnabled()) {
            ConfigMessage.ECONOMY_DISABLED.getMessage().send(player);
            return;
        }

        TurtleStorageFishBridge.SellSummary storageSummary = TurtleStorageFishBridge.SellSummary.EMPTY;
        if (includesPlayerStorage && TurtleStorageFishBridge.isAvailable()) {
            storageSummary = TurtleStorageFishBridge.sellStoredFish(
                player,
                null,
                null,
                Long.MAX_VALUE,
                fish.isEmpty()
            );
        }

        if (fish.isEmpty()) {
            if (storageSummary.soldCount() <= 0L) {
                ConfigMessage.NO_SELLABLE_FISH.getMessage().send(player);
            }
            return;
        }

        // Fire the sell event and check for cancellation.
        EMFFishSellEvent event = new EMFFishSellEvent(fish, player, saleValue, LocalDateTime.now());
        if (!event.callEvent()) {
            if (storageSummary.soldCount() <= 0L) {
                ConfigMessage.NO_SELLABLE_FISH.getMessage().send(player);
            }
            return;
        }

        // Calculate bonus & deposit money first so player is always paid
        double vipBonusPct = com.oheers.fish.autofish.AutoFishingService.getActiveSellBonusPercent(player);
        int comboStreak = com.oheers.fish.fishing.FishingProcessor.getComboStreak(player.getUniqueId());
        int comboBonusPct = com.oheers.fish.fishing.FishingProcessor.getComboBonusPercent(comboStreak);
        double totalBonusPct = vipBonusPct + comboBonusPct;
        double finalSaleValue = totalBonusPct > 0.0D ? saleValue * (1.0D + totalBonusPct / 100.0D) : saleValue;
        Economy.getInstance().deposit(player, finalSaleValue, true);

        double combinedFinalWorth = finalSaleValue + storageSummary.finalWorth();
        double combinedBaseWorth = saleValue + storageSummary.baseWorth();
        long combinedCount = count + storageSummary.soldCount();

        if (totalBonusPct > 0.0D) {
            double bonusMoney = Math.max(0.0D, combinedFinalWorth - combinedBaseWorth);
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<dark_gray>[<gold>★ Thưởng Câu Cá</gold>]</dark_gray> <green>Cộng thêm <gold>+" + com.oheers.fish.autofish.AutoFishingService.formatMoney(bonusMoney)
                    + "</gold> <yellow>(+" + (int) totalBonusPct + "% Ưu đãi VIP & Combo)</yellow> khi bán cá!</green>"
            ));
        }

        // Send message
        EMFMessage message = ConfigMessage.FISH_SALE.getMessage();
        message.setSellPrice(Economy.getInstance().getWorthFormat(combinedFinalWorth, true));
        message.setAmount((int) Math.min(Integer.MAX_VALUE, combinedCount));
        message.setPlayer(player);
        message.send(player);

        // Play sound
        player.playSound(this.player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.06f);

        // Give sell rewards safely
        try {
            fish.forEach(sold ->
                sold.getFish().getSellRewards().forEach(
                    reward -> reward.rewardPlayer(player, player.getLocation())
                )
            );
        } catch (Throwable exception) {
            EvenMoreFish.getInstance().debug("Error executing sell rewards: " + exception.getMessage(), exception);
        }

        // Log sold fish to database safely
        try {
            logSoldFish(player, fish, finalSaleValue, count);
        } catch (Throwable exception) {
            EvenMoreFish.getInstance().debug("Error logging sold fish: " + exception.getMessage(), exception);
        }
    }

    private static List<SoldFish> fetchFish(@NotNull Inventory inventory, boolean removeFromInventory) {
        List<SoldFish> list = new ArrayList<>();
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || hasEquipped(inventory, item)) {
                continue;
            }
            try {
                SoldFish fish = new SoldFish(item);
                list.add(fish);
                if (removeFromInventory && Economy.getInstance().isEnabled()) {
                    inventory.setItem(slot, null);
                }
            } catch (IllegalArgumentException exception) {
                EvenMoreFish.getInstance().debug(exception.getMessage(), exception);
            }
        }
        return list;
    }

    private static List<SoldFish> fetchFish(@Nullable ItemStack @NotNull[] itemStacks, boolean removeStack) {
        List<SoldFish> list = new ArrayList<>();

        for (ItemStack item : itemStacks) {
            if (item == null) {
                continue;
            }
            try {
                SoldFish fish = new SoldFish(item);
                list.add(fish);
                if (removeStack && Economy.getInstance().isEnabled()) {
                    item.setAmount(0);
                }
            } catch (IllegalArgumentException exception) {
                EvenMoreFish.getInstance().debug(exception.getMessage(), exception);
            }
        }
        return list;
    }

    private static boolean hasEquipped(@NotNull Inventory inventory, @NotNull ItemStack item) {
        if (!(inventory instanceof PlayerInventory playerInventory)) {
            return false;
        }
        return Arrays.stream(playerInventory.getArmorContents())
            .filter(Objects::nonNull)
            .anyMatch(armorItem -> armorItem.isSimilar(item));
    }

    public static void logSoldFish(@NotNull Player player, @NotNull List<SoldFish> soldFish, double totalWorth, int fishSold) {
        if (!DatabaseUtil.isDatabaseOnline()) {
            return;
        }
        final UUID uuid = player.getUniqueId();

        final int userId = EvenMoreFish.getInstance().getPluginDataManager().getUserManager().getUserId(uuid);
        final String transactionId = FriendlyId.createFriendlyId();
        final Timestamp timestamp = Timestamp.from(Instant.now());

        Database database = EvenMoreFish.getInstance().getPluginDataManager().getDatabase();

        database.createTransaction(transactionId, userId, timestamp);
        soldFish.forEach(fish -> database.createSale(
            transactionId,
            fish.getName(),
            fish.getRarity(),
            fish.getAmount(),
            fish.getLength(),
            fish.getTotalValue()
        ));

        final DataManager<UserReport> userReportDataManager = EvenMoreFish.getInstance().getPluginDataManager().getUserReportDataManager();
        if (userReportDataManager != null) {
            final UserReport report = userReportDataManager.get(uuid.toString());
            if (report != null) {
                report.incrementFishSold(fishSold);
                report.incrementMoneyEarned(totalWorth);
                userReportDataManager.update(uuid.toString(), report);
            }
        }
    }

    public static void sellInventoryGui(@NotNull InventoryGui gui, @NotNull HumanEntity humanEntity) {
        if (!(humanEntity instanceof Player player)) {
            return;
        }

        if (!Economy.getInstance().isEnabled()) {
            ConfigMessage.ECONOMY_DISABLED.getMessage().send(player);
            return;
        }

        gui.getElements().forEach(element -> {
            if (!(element instanceof GuiStorageElement storageElement)) {
                return;
            }
            new SellHelper(storageElement.getStorage(), player).sell();
        });
    }

    public static double calculateInventoryWorth(@NotNull Inventory inventory) {
        return fetchFish(inventory, false).stream().mapToDouble(SoldFish::getTotalValue).sum();
    }

    public static int calculateInventoryCount(@NotNull Inventory inventory) {
        return fetchFish(inventory, false).stream().mapToInt(SoldFish::getAmount).sum();
    }

}
