package com.oheers.fish.gui.guis;

import com.oheers.fish.Checks;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.baits.BaitHandler;
import com.oheers.fish.baits.manager.BaitManager;
import com.oheers.fish.baits.manager.BaitNBTManager;
import com.oheers.fish.baits.model.ApplicationResult;
import com.oheers.fish.config.GuiConfig;
import com.oheers.fish.exceptions.MaxBaitReachedException;
import com.oheers.fish.exceptions.MaxBaitsReachedException;
import com.oheers.fish.gui.ConfigGui;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.messages.abstracted.EMFMessage;
import de.themoep.inventorygui.GuiStorageElement;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ApplyBaitsGui extends ConfigGui {

    private final Inventory baitInventory;

    public ApplyBaitsGui(@NotNull Player player, @Nullable Inventory baitInventory) {
        super(
            GuiConfig.getInstance().getConfig().getSection("apply-baits-menu"),
            player
        );
        this.baitInventory = Optional.ofNullable(baitInventory).orElse(Bukkit.createInventory(player, 54));

        setCloseAction(close -> {
            processBaits();
            doRescue();
            return false;
        });

        createGui();

        Section config = getGuiConfig();
        if (config != null) {
            getGui().addElement(new GuiStorageElement(FishUtils.getCharFromString(getGuiConfig().getString("bait-character", "b"), 'b'), this.baitInventory));
        }
    }

    private void processBaits() {
        ItemStack handItem = this.player.getInventory().getItemInMainHand();
        if (!Checks.canUseRod(handItem)) {
            return;
        }
        boolean changedRod = false;
        List<String> ignoredBaits = new ArrayList<>();

        for (int slot = 0; slot < this.baitInventory.getSize(); slot++) {
            ItemStack item = this.baitInventory.getItem(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            BaitHandler bait = BaitManager.getInstance().getBait(item);
            if (bait == null) {
                continue;
            }
            if (ignoredBaits.contains(bait.getId())) {
                continue;
            }

            int amount = item.getAmount();
            ApplicationResult result;
            boolean rodMaxed = false;

            try {
                result = BaitNBTManager.applyBaitedRodNBT(handItem, bait, amount);
                int applied = -result.cursorItemModifier();
                EvenMoreFish.getInstance().getMetricsManager().incrementBaitsApplied(applied > 0 ? applied : amount);
            } catch (MaxBaitReachedException exception) {
                EMFMessage message = ConfigMessage.BAITS_MAXED_ON_ROD.getMessage();
                message.setBait(bait);
                message.send(this.player);
                ignoredBaits.add(bait.getId());
                result = exception.getRecoveryResult();
            } catch (MaxBaitsReachedException exception) {
                ConfigMessage.BAITS_MAXED.getMessage().send(this.player);
                result = exception.getRecoveryResult();
                rodMaxed = true;
            }

            if (result != null) {
                handItem = result.fishingRod();
                int cursorModifier = result.cursorItemModifier();
                int remainingAmount = amount + cursorModifier;

                if (remainingAmount <= 0) {
                    this.baitInventory.setItem(slot, null);
                } else {
                    item.setAmount(remainingAmount);
                    this.baitInventory.setItem(slot, item);
                }

                if (cursorModifier < 0) {
                    changedRod = true;
                }
            }

            if (rodMaxed) {
                break;
            }
        }

        if (changedRod) {
            this.player.getInventory().setItemInMainHand(handItem);
            this.player.playSound(this.player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
        }
    }

}
