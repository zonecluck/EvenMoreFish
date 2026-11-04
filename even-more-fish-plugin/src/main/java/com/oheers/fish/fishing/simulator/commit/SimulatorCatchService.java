package com.oheers.fish.fishing.simulator.commit;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.config.simulator.SimulatorSettings;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.Processor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.selling.TurtleStorageFishBridge;
import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import com.oheers.fish.fishing.simulator.ai.FishAttractionPolicy;
import com.oheers.fish.fishing.simulator.engine.ProbeSession;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Phase 7 adapter that reuses EMF fish metadata, cancellable event, bait,
 * shop/journal identity and competition side effects. No async Bukkit access.
 *
 * Preparation is cancellable and must NOT grant loot or spend bait. The AI
 * owner consumes the matching reserved FishAgent only after prepare and
 * revalidation, then calls deliver() exactly once. Should delivery fail, the
 * fish is gone (fail closed, never duplicate or retry a half-committed catch).
 * This is an admin-only integration gate, NOT production-ready yet.
 */
public final class SimulatorCatchService {
    private final EvenMoreFish plugin;
    private final FishAttractionPolicy attraction;
    private long preparedCount, deliveredCount, rejectedCount, failedDeliveryCount;

    public SimulatorCatchService(EvenMoreFish plugin, FishAttractionPolicy attraction) {
        this.plugin=Objects.requireNonNull(plugin);
        this.attraction=Objects.requireNonNull(attraction);
    }

    /**
     * No Simulator reward takeover while a native EMF loot category would be lost.
     * "only-fish: true" explicitly preserves native treasure, and bait chance
     * requires a BAIT_LOOT visual agent which does not yet exist in Phase 7.
     */
    public static boolean legacyLootCompatibilityRequired() {
        MainConfig config=MainConfig.getInstance();
        if(config.isFishCatchOverrideOnlyFish() || config.getBaitCatchPercentage()>0.0)return true;
        if(!config.disableMcMMOTreasure() && Bukkit.getPluginManager().isPluginEnabled("mcMMO"))return true;
        return !config.disableAuraSkills() && Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
    }

    /** Called synchronously from the original FishingProcessor REEL_IN handler. */
    public @Nullable FishAiDirector.PreparedCatch prepare(
        PlayerFishEvent event, ProbeSession session, FishAiDirector.View view,
        FishAgent agent, FishAttractionPolicy.Snapshot snapshot,
        FishingProcessor fishing, SimulatorSettings.RewardBridge policy,
        BooleanSupplier stillValid) {
        Objects.requireNonNull(event);Objects.requireNonNull(session);
        Objects.requireNonNull(view);Objects.requireNonNull(agent);
        Objects.requireNonNull(snapshot);Objects.requireNonNull(fishing);
        Objects.requireNonNull(policy);Objects.requireNonNull(stillValid);
        Player player=event.getPlayer();
        if(!policy.enabled() || !policy.paperGatesVerified() || event.isCancelled() ||
            !stillValid.getAsBoolean() || legacyLootCompatibilityRequired() ||
            !view.fishKey().equals(agent.fishKey()) || !agent.reservedFor(session.hookId())) {
            rejectedCount++;return null;
        }
        // Recheck requirements/rarity/rod/bait at the actual reel, not only at cast.
        Location hookLocation=event.getHook().getLocation();
        if(attraction.attraction(player,hookLocation,snapshot,agent)<=0) {
            rejectedCount++;return null;
        }
        ItemStack rod=player.getInventory().getItem(session.castHand());
        if(rod==null || rod.isEmpty()){rejectedCount++;return null;}

        // Dispatch existing EMFFishCaughtEvent once. If an addon cancels it,
        // return null: AI releases the fish; no bait, item, stats, or rewards.
        Processor.SimulatorPreparedFish prepared;
        try {
            prepared=fishing.prepareSimulatorFish(player,view.fishKey(),snapshot.bait());
        } catch(RuntimeException error) {
            plugin.getLogger().warning("[Simulator] EMF catch preparation failed: "+error.getMessage());
            rejectedCount++;return null;
        }
        if(prepared==null){rejectedCount++;return null;}
        ItemStack result=prepared.item();
        int baseExp=ThreadLocalRandom.current().nextInt(policy.experienceMin(),policy.experienceMax()+1);
        FishingProcessor.SimulatorBonuses bonuses;
        try {
            bonuses=fishing.prepareSimulatorBonuses(player,rod,result,prepared.fish(),baseExp);
        } catch(RuntimeException error) {
            plugin.getLogger().warning("[Simulator] Fish bonus preparation failed: "+error.getMessage());
            rejectedCount++;return null;
        }
        preparedCount++;
        // Never carry an ItemStack or Player into an async continuation. Both
        // methods below run synchronously on the owner thread in the same event.
        return new FishAiDirector.PreparedCatch() {
            private boolean delivered;
            @Override public boolean ready(){
                try {
                    return !delivered && !event.isCancelled() && stillValid.getAsBoolean() &&
                        !legacyLootCompatibilityRequired() && agent.reservedFor(session.hookId()) &&
                        view.fishKey().equals(agent.fishKey());
                } catch(RuntimeException error){return false;}
            }
            @Override public boolean deliver() {
                if(delivered)return false;
                delivered=true; // terminal even if delivery throws
                if(!deliverItem(player,prepared.fish(),result)){
                    failedDeliveryCount++;
                    plugin.getLogger().warning("[Simulator] Fish delivery failed for "+player.getUniqueId());
                    return false;
                }
                deliveredCount++;
                // No further step is permitted to issue this fish item again.
                // Keep player progress after item delivery; additive post-actions
                // are isolated so a bad optional integration cannot dupe loot.
                try {fishing.completeSimulatorFish(prepared,player,hookLocation,rod,snapshot.bait());}
                catch(RuntimeException error){
                    plugin.getLogger().warning("[Simulator] Post-catch handling failed: "+error.getMessage());
                }
                try {TurtleStorageFishBridge.grantFishingSkillXp(player,prepared.fish());}
                catch(RuntimeException ignored){}
                try {if(bonuses.exp()>0)player.giveExp(bonuses.exp());}
                catch(RuntimeException error){
                    plugin.getLogger().warning("[Simulator] XP delivery failed: "+error.getMessage());
                }
                try {fishing.playSimulatorCatchAnimations(player,bonuses,hookLocation);}
                catch(RuntimeException error){
                    plugin.getLogger().warning("[Simulator] Catch animation failed: "+error.getMessage());
                }
                return true;
            }
        };
    }

    /** One-slot inventory commit OR one whole-stack drop. Never addItem+split/drop leftovers. */
    private boolean deliverItem(Player player, @Nullable Fish fish, ItemStack item) {
        ItemStack copy=item.clone();
        if(copy.isEmpty() || copy.getAmount()<=0 || !player.isOnline())return false;
        try {
            int totalAmount = copy.getAmount();
            int deposited = TurtleStorageFishBridge.depositCaughtFish(player, fish, copy, totalAmount);
            if (deposited >= totalAmount) {
                return true;
            }
            if (deposited > 0) {
                copy.setAmount(totalAmount - deposited);
            }
            PlayerInventory inventory=player.getInventory();
            if(MainConfig.getInstance().isGiveStraightToInventory()) {
                // A one-slot update cannot leave the catch spread across multiple
                // stacks if a Bukkit inventory implementation behaves unexpectedly.
                var plan=SingleSlotDeliveryPlanner.plan(
                    inventory.getStorageContents(),copy,inventory.getMaxStackSize());
                if(plan!=null) {
                    inventory.setItem(plan.slot(),plan.contents());
                    ItemStack committed=inventory.getItem(plan.slot());
                    if(committed!=null && committed.getAmount()==plan.contents().getAmount() &&
                        committed.isSimilar(plan.contents()))return true;
                    // Ambiguous post-write state: do NOT drop or retry the fish.
                    plugin.getLogger().severe("[Simulator] Inventory post-write verification failed for " +
                        player.getUniqueId() + "; no item retry performed");
                    return false;
                }
            }
            return dropAtPlayer(player,copy);
        } catch(RuntimeException error){
            // setItem() or dropItem() may have partly completed before throwing.
            // The already-consumed fish is not retried; admin must inspect logs.
            plugin.getLogger().severe("[Simulator] Ambiguous inventory/drop failure for " +
                player.getUniqueId() + ": " + error.getMessage());
            return false;
        }
    }

    private static boolean dropAtPlayer(Player player,ItemStack item) {
        if(item.isEmpty() || item.getAmount()<=0)return false;
        Item dropped=player.getWorld().dropItem(player.getLocation().add(0,0.3,0),item.clone());
        return dropped!=null && dropped.isValid();
    }

    public long preparedCount(){return preparedCount;}
    public long deliveredCount(){return deliveredCount;}
    public long rejectedCount(){return rejectedCount;}
    public long failedDeliveryCount(){return failedDeliveryCount;}
}
