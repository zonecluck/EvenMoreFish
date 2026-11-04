package com.oheers.fish.fishing;

import com.oheers.fish.Checks;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.api.Logging;
import com.oheers.fish.api.fishing.FishingType;
import com.oheers.fish.api.requirement.RequirementContext;
import com.oheers.fish.baits.BaitHandler;
import com.oheers.fish.baits.manager.BaitNBTManager;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.database.DatabaseUtil;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.broadcast.FishBroadcast;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.rods.CustomRod;
import com.oheers.fish.fishing.rods.RodManager;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.commit.SimulatorCatchEventGate;
import com.oheers.fish.fishing.EMFFishListener;
import java.time.LocalDateTime;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.messages.abstracted.EMFMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public abstract class Processor<E extends Event> {

    // Used for formatting fish length.
    public static final DecimalFormat LENGTH_FORMAT = new DecimalFormat("#.0");
    private final Random random = new Random();
    private final FishingType fishingType;

    public Processor() {
        this.fishingType = FishingType.VANILLA;
    }

    public Processor(@NotNull FishingType fishingType) {
        this.fishingType = fishingType;
    }

    protected abstract void process(@NotNull E event);

    protected abstract boolean isEnabled();

    private @Nullable Fish lastCaughtFish;

    public @Nullable Fish takeLastCaughtFish() {
        Fish f = this.lastCaughtFish;
        this.lastCaughtFish = null;
        return f;
    }

    /**
     * Compatibility adapter for the original EMF API. Simulator will reuse the
     * selection/prepare/commit boundary rather than roll a second fish on reel.
     */
    public @Nullable ItemStack getCaughtItem(@NotNull Player player,
                                             @NotNull Location location,
                                             @Nullable ItemStack fishingRod) {
        this.lastCaughtFish = null;
        CatchSelection selection = selectCatch(player, location, fishingRod);
        if (selection == null) {
            return null;
        }
        prepareCatch(selection);
        ItemStack committed = commitCatch(selection, player, location, fishingRod);
        if (committed != null) {
            this.lastCaughtFish = selection.fish();
        }
        return committed;
    }

    /** No rewards, bait consumption, messages, effects, or DB writes here. */
    private @Nullable CatchSelection selectCatch(@NotNull Player player,
                                                  @NotNull Location location,
                                                  @Nullable ItemStack fishingRod) {
        if (!FishUtils.checkWorld(location) ||
                !FishUtils.checkRegion(location, MainConfig.getInstance().getAllowedRegions()) ||
                Checks.isMcMMOOverfishing(player)) {
            return null;
        }

        double baitCatchPercentage = MainConfig.getInstance().getBaitCatchPercentage();
        if (shouldCatchBait() && baitCatchPercentage > 0 && random.nextDouble() * 100 < baitCatchPercentage) {
            Optional<BaitHandler> baitLoot = BaitNBTManager.randomBaitCatch();
            return baitLoot.map(CatchSelection::forBait).orElse(null);
        }

        CustomRod customRod = null;
        BaitHandler bait = null;
        if (fishingRod != null && !fishingRod.isEmpty()) {
            customRod = RodManager.getInstance().getRod(fishingRod);
            bait = getBaitFromRod(fishingRod, customRod);
        }

        Fish fish = chooseFish(player, location, bait, customRod);
        return fish == null ? null : CatchSelection.forFish(fish, bait);
    }

    private void prepareCatch(@NotNull CatchSelection selection) {
        // Selection and size generation do not produce observable side effects.
        if (selection.fish() != null) {
            selection.fish().prepareCatch();
        }
    }

    /**
     * The only path allowed to produce catch side effects. A cancelled EMF
     * event cannot consume bait, modify leaderboard, trigger fish effects,
     * record metrics or grant catch rewards.
     */
    private @Nullable ItemStack commitCatch(@NotNull CatchSelection selection,
                                            @NotNull Player player,
                                            @NotNull Location location,
                                            @Nullable ItemStack fishingRod) {
        if (selection.caughtBait() != null) {
            ItemStack baitItem = selection.caughtBait().create(player);
            handleCaughtBait(player, location, selection.caughtBait(), baitItem);
            return baitItem;
        }

        Fish fish = selection.fish();
        if (fish == null) {
            return null;
        }
        if (DatabaseUtil.isDatabaseOnline() &&
            !EvenMoreFish.getInstance().getPluginDataManager().hasCatchWriteCapacity()) {
            Logging.warn("EMF catch cancelled: DB report queue is saturated.");
            return null;
        }
        if (!fireEvent(fish, player)) {
            Logging.debug("Catch event cancelled; no side effects committed.");
            return null;
        }
        // Construct the item AFTER event handlers: existing addons may customize
        // fish metadata during the cancellable event.
        ItemStack preparedItem = fish.give();

        if (selection.appliedBait() != null && fishingRod != null) {
            selection.appliedBait().handleFish(player, fish, fishingRod);
        }
        fish.applyCatchEffects();
        handleCaughtFish(player, location, fish);
        leaderboardCheck(fish, player, location);
        return preparedItem;
    }


    /**
     * A single Simulator fish chosen by the *visual* agent, not re-rolled at reel time.
     * The delivery coordinator owns this object. Complete at most once, after
     * inventory/drop delivery has succeeded.
     */
    public static final class SimulatorPreparedFish {
        private final Fish fish;
        private final ItemStack item;
        private boolean completed;
        private SimulatorPreparedFish(Fish fish, ItemStack item) {
            this.fish=fish;
            this.item=item.clone();
        }
        public @NotNull Fish fish(){return fish;}
        public @NotNull ItemStack item(){return item.clone();}
        private boolean takeCompletion(){
            if(completed)return false;
            completed=true;
            return true;
        }
    }

    /**
     * Preparation only; EMFFishCaughtEvent is fired exactly once for this agent.
     * No bait usage, effects, competition, messages, or DB writes are performed
     * here, so cancellation can reject the catch without observable EMF rewards.
     */
    public final @Nullable SimulatorPreparedFish prepareSimulatorFish(@NotNull Player player,
                                                                       @NotNull FishKey key,
                                                                       @Nullable BaitHandler bait) {
        if (DatabaseUtil.isDatabaseOnline() &&
            !EvenMoreFish.getInstance().getPluginDataManager().hasCatchWriteCapacity()) {
            Logging.warn("Simulator fish cancelled: DB report queue is saturated.");
            return null;
        }
        Fish fish=FishManager.getInstance().getFish(key.rarityId(),key.fishId());
        if(fish==null || !canUseFish(fish))return null;
        fish.setFisherman(player);
        fish.setWasBaited(bait!=null);
        fish.prepareCatch();
        if(!SimulatorCatchEventGate.invokeDeferred(player,fish,()->fireEvent(fish,player)))return null;
        ItemStack prepared=fish.give();
        if(prepared.isEmpty() || prepared.getAmount()<=0)return null;
        return new SimulatorPreparedFish(fish,prepared);
    }

    /**
     * Post-delivery EMF adapter: same fish rewards/metrics/broadcast/competition
     * path as the legacy fishing processor. Cannot run twice for the same prepared
     * fish. Exceptions are isolated so one addon cannot skip all later post-steps.
     */
    public final boolean completeSimulatorFish(@NotNull SimulatorPreparedFish prepared,
                                               @NotNull Player player,
                                               @NotNull Location location,
                                               @Nullable ItemStack rod,
                                               @Nullable BaitHandler bait) {
        if(!prepared.takeCompletion())return false;
        Fish fish=prepared.fish();
        // The cancellable event was already fired, but stats were deferred.
        // DB/journal history is emitted ONLY for an actually delivered item.
        try {EMFFishListener.recordDeliveredSimulatorCatch(player,fish,LocalDateTime.now());}
        catch(RuntimeException error) {
            EvenMoreFish.getInstance().getLogger().warning("[Simulator] catch history enqueue failed: "+error.getMessage());
        }
        if(bait!=null && rod!=null) {
            try {bait.handleFish(player,fish,rod);}
            catch(RuntimeException error) {
                EvenMoreFish.getInstance().getLogger().warning("[Simulator] bait usage failed: "+error.getMessage());
            }
        }
        try {fish.applyCatchEffects();}
        catch(RuntimeException error) {
            EvenMoreFish.getInstance().getLogger().warning("[Simulator] fish catch effects failed: "+error.getMessage());
        }
        try {handleCaughtFish(player,location,fish);}
        catch(RuntimeException error) {
            EvenMoreFish.getInstance().getLogger().warning("[Simulator] fish rewards/messages failed: "+error.getMessage());
        }
        try {leaderboardCheck(fish,player,location);}
        catch(RuntimeException error) {
            EvenMoreFish.getInstance().getLogger().warning("[Simulator] competition update failed: "+error.getMessage());
        }
        return true;
    }

    private record CatchSelection(@Nullable Fish fish,
                                  @Nullable BaitHandler appliedBait,
                                  @Nullable BaitHandler caughtBait) {
        private static CatchSelection forFish(Fish fish, BaitHandler appliedBait) {
            return new CatchSelection(fish, appliedBait, null);
        }
        private static CatchSelection forBait(BaitHandler bait) {
            return new CatchSelection(null, null, bait);
        }
    }

    private void handleCaughtFish(@NotNull Player player, @NotNull Location location, @NotNull Fish fish) {
        if (fish.hasCatchRewards()) {
            fish.getCatchRewards().forEach(fishReward -> fishReward.rewardPlayer(player, location));
        }

        EvenMoreFish.getInstance().getMetricsManager().incrementFishCaught(1);
        if (fish.isSilent()) {
            return;
        }

        EMFMessage message = fish.getLength() == -1 ?
            getLengthlessCaughtMessage().getMessage() :
            getCaughtMessage().getMessage();

        message.setPlayer(player);

        // Sets rarity, length, and fish display name variables
        message.setFishCatchVariables(fish);

        if (fish.getRarity().getBroadcastEnabled()) {
            Runnable sendBroadcast = () -> {
                if (player.isOnline()) {
                    new FishBroadcast(message, player, fish).broadcast();
                }
            };
            var cinematics = EvenMoreFish.getInstance().getCatchCinematics();
            if (cinematics != null && cinematics.enabled() && cinematics.eligible(fish)) {
                cinematics.enqueueRevealCallback(player.getUniqueId(), sendBroadcast);
            } else {
                sendBroadcast.run();
            }
        } else {
            Runnable sendDirect = () -> {
                if (player.isOnline()) {
                    message.send(player);
                }
            };
            var cinematics = EvenMoreFish.getInstance().getCatchCinematics();
            if (cinematics != null && cinematics.enabled() && cinematics.eligible(fish)) {
                cinematics.enqueueRevealCallback(player.getUniqueId(), sendDirect);
            } else {
                sendDirect.run();
            }
        }
    }

    private @Nullable BaitHandler getBaitFromRod(@NotNull ItemStack rod, @Nullable CustomRod customRod) {
        if (customRod != null && !customRod.isBaitAllowed()) {
            return null;
        }
        if (MainConfig.getInstance().getBaitCompetitionDisable() && Competition.isActive()) {
            return null;
        }
        return BaitNBTManager.isBaitedRod(rod) ? BaitNBTManager.randomBaitApplication(rod) : null;
    }

    private void handleCaughtBait(@NotNull Player player, @NotNull Location location,
                                  @NotNull BaitHandler bait, @NotNull ItemStack baitItem) {
        if (bait.hasCatchRewards()) {
            bait.getCatchRewards().forEach(reward -> reward.rewardPlayer(player, location));
        }
        if (!bait.isSilent()) {
            EMFMessage message = ConfigMessage.BAIT_CAUGHT.getMessage();
            message.setBait(bait, baitItem);
            message.setPlayer(player);
            message.send(player);
        }
    }

    /**
     * Chooses a fish for the player. randomWeightedRarity & getFish methods are used to
     * choose the random fish.
     *
     * @param player   The fisher catching the fish.
     * @param location The location of the fisher.
     * @param bait The bait being used, null if no bait.
     * @param customRod The custom rod being used, null if no custom rod.
     * @return A random fish.
     */
    private @Nullable Fish chooseFish(@NotNull Player player, @NotNull Location location, @Nullable BaitHandler bait, @Nullable CustomRod customRod) {
        RequirementContext context = this instanceof FishingProcessor ? new NativeFishingContext(location, player, this.fishingType) : new RequirementContext(
            player.getWorld(),
            location,
            player,
            null,
            null,
            this.fishingType
        );

        // Bait is compatible with custom rods when allow-baits is enabled.
        // The bait resolver respects rarity/fish restrictions of that rod.
        if (bait != null) {
            return bait.chooseFish(player, location, context, customRod, this);
        }

        Rarity rarity = FishManager.getInstance().getRandomWeightedRarity(
            player,
            1,
            Set.of(),
            Set.copyOf(FishManager.getInstance().getRarityMap().values()),
            customRod,
            context
        );
        if (rarity == null) {
            Logging.error("Could not determine a fish rarity for " + player.getName());
            return null;
        }

        Fish fish = FishManager.getInstance().getFish(
            rarity,
            location,
            player,
            1,
            null,
            true,
            this,
            customRod,
            context
        );
        if (fish == null) {
            EvenMoreFish.getInstance().getLogger().severe("Could not determine a fish for " + player.getName());
            return null;
        }
        fish.setFisherman(player);
        return fish;
    }

    protected abstract boolean fireEvent(@NotNull Fish fish, @NotNull Player player);

    protected abstract ConfigMessage getCaughtMessage();

    protected abstract ConfigMessage getLengthlessCaughtMessage();

    // Checks

    protected boolean isCustomFishAllowed(Player player) {
        return isEnabled() && MainConfig.getInstance().getEnabled() && (competitionOnlyCheck() || EvenMoreFish.getInstance().isRaritiesCompCheckExempt())
            && !EvenMoreFish.getInstance().getToggle().isCustomFishingDisabled(player);
    }

    /**
     * Checks if the player should get a fish, respecting the only-in-competition option in config.yml
     */
    protected abstract boolean competitionOnlyCheck();

    protected abstract boolean shouldCatchBait();

    public abstract boolean canUseFish(@NotNull Fish fish);

    /**
     * Checks if we need to update the competition leaderboard.
     */
    protected void leaderboardCheck(@NotNull Fish fish, @NotNull Player fisherman, @NotNull Location location) {
        final Competition active = Competition.getCurrentlyActive();
        if (active == null) {
            return;
        }

        List<World> competitionWorlds = active.getCompetitionFile().getRequiredWorlds();
        if (!competitionWorlds.isEmpty()) {
            final World world = location.getWorld();
            if (world == null || !competitionWorlds.contains(world)) {
                return;
            }
        }
        active.applyToLeaderboard(fish, fisherman);
    }

}
