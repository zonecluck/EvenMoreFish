package com.oheers.fish.fishing;

import com.oheers.fish.Checks;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.api.events.EMFFishCaughtEvent;
import com.oheers.fish.api.fishing.CatchType;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.buff.CompetitionBuffManager;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.effects.AnimationManager;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.rods.CustomRod;
import com.oheers.fish.fishing.rods.RodManager;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.permissions.UserPerms;
import com.oheers.fish.selling.TurtleStorageFishBridge;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class FishingProcessor extends Processor<PlayerFishEvent> implements Listener {
    private static final Map<UUID, Integer> COMBO_STREAKS = new ConcurrentHashMap<>();
    private final EvenMoreFish plugin = EvenMoreFish.getInstance();
    private final FishingHookHolograms hookHolograms = new FishingHookHolograms(plugin);
    private final Map<UUID, CastSnapshot> castSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCatchVisual> pendingVisuals = new java.util.HashMap<>();
    private final java.util.Set<UUID> acceptedBites = new java.util.HashSet<>();
    private static final long CAST_TTL_NANOS = TimeUnit.MINUTES.toNanos(5);

    private record CastSnapshot(UUID hookId, EquipmentSlot hand, ItemStack signature, long castTime) {}
    private record PendingCatchVisual(
            org.bukkit.Location water,
            Fish fish,
            ItemStack item,
            int comboStreak,
            int comboBonusPercent,
            boolean doubleCatch
    ) {}

    public static int getComboStreak(@Nullable UUID playerId) {
        if (playerId == null) return 0;
        return COMBO_STREAKS.getOrDefault(playerId, 0);
    }

    public static int getComboBonusPercent(int streak) {
        if (streak <= 1) return 0;
        return Math.min(50, (streak - 1) * 5);
    }

    public static int incrementComboStreak(@NotNull UUID playerId) {
        return COMBO_STREAKS.merge(playerId, 1, Integer::sum);
    }

    public static void resetComboStreak(@NotNull UUID playerId) {
        COMBO_STREAKS.remove(playerId);
    }

    private static boolean isTerminal(PlayerFishEvent.State state) {
        return switch (state) {
            case CAUGHT_FISH, CAUGHT_ENTITY, REEL_IN, IN_GROUND -> true;
            default -> false;
        };
    }


    @Override
    @EventHandler(priority = EventPriority.HIGHEST)
    public void process(@NotNull PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            castSnapshots.remove(event.getPlayer().getUniqueId());
            if (plugin.getFishingPools() != null) plugin.getFishingPools().quit(event.getPlayer().getUniqueId());
            if (plugin.getHookedFishPackets() != null) plugin.getHookedFishPackets().stop(event.getPlayer().getUniqueId());
        }
        try {
            // Single entrypoint: a confirmed Simulator owner excludes legacy EMF processing.
            if (plugin.getSimulatorModule() != null &&
                plugin.getSimulatorModule().onFishEvent(event, this::clearLegacyState, this)) return;
            processCast(event);
        } finally {
            boolean terminal = isTerminal(event.getState());
            if (terminal) {
                castSnapshots.remove(event.getPlayer().getUniqueId());
                if (plugin.getFishingPools() != null) plugin.getFishingPools().untrack(event.getHook().getUniqueId());
            }
            // A missed bite resets the native timer but leaves this cast and hook alive.
            if ((terminal || event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT)
                && plugin.getHookedFishPackets() != null)
                plugin.getHookedFishPackets().stop(event.getPlayer().getUniqueId());
        }
    }

    private void clearLegacyState(@NotNull org.bukkit.entity.FishHook hook) {
        if (hook.getShooter() instanceof Player player) {
            castSnapshots.remove(player.getUniqueId());
        }
        hookHolograms.remove(hook);
        if (plugin.getFishingPools() != null) plugin.getFishingPools().untrack(hook.getUniqueId());
        if (hook.getShooter() instanceof Player player && plugin.getHookedFishPackets() != null)
            plugin.getHookedFishPackets().stop(player.getUniqueId());
    }

    private void processCast(@NotNull PlayerFishEvent event) {
        if (event.isCancelled()) {
            plugin.debug("Fishing event was cancelled. Skipping handling.");
            return;
        }

        if (event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT) {
            resetComboStreak(event.getPlayer().getUniqueId());
        }

        handleHookHologram(event);

        ItemStack rod = getRod(event);

        if (rod == null) {
            plugin.debug("Fishing blocked: could not find rod.");
            return;
        }

        if (!isCustomFishAllowed(event.getPlayer())) {
            plugin.debug("Fishing blocked: custom fish not allowed for player %s.".formatted(event.getPlayer().getName()));
            return;
        }

        if (!Checks.canUseRod(rod)) {
            plugin.debug("Fishing blocked: rod unusable (%s).".formatted(rod));
            return;
        }

        if (MainConfig.getInstance().requireFishingPermission() && !event.getPlayer().hasPermission(UserPerms.USE_ROD)) {
            plugin.debug("Fishing blocked: permission required and player lacks it.");
            if (event.getState() == PlayerFishEvent.State.FISHING) {
                // send msg only when throw the lure
                ConfigMessage.NO_PERMISSION_FISHING.getMessage().send(event.getPlayer());
            }
            return;
        }

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            EquipmentSlot castHand = event.getHand();
            if (castHand == null) {
                return;
            }
            castSnapshots.put(event.getPlayer().getUniqueId(), new CastSnapshot(
                event.getHook().getUniqueId(), castHand, rodFingerprint(rod), System.nanoTime()
            ));
            applyLureAndCastEffects(event, rod);
        }

        if (event.getState() == PlayerFishEvent.State.BITE) {
            acceptedBites.add(event.getHook().getUniqueId());
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            plugin.debug("Ignoring event state: %s".formatted(event.getState()));
            return;
        }

        if (!(event.getCaught() instanceof Item nonCustom)) {
            plugin.debug("Caught entity is not an Item.");
            return;
        }
        nonCustom.setGlowing(false);
        boolean autoFishingActive = plugin.getAutoFishing() != null
            && plugin.getAutoFishing().isActive(event.getPlayer().getUniqueId());
        boolean giveDirect = autoFishingActive || MainConfig.getInstance().isGiveStraightToInventory();

        if (MainConfig.getInstance().isFishCatchOverrideOnlyFish() && !Tag.ITEMS_FISHES.isTagged(nonCustom.getItemStack().getType())) {
            plugin.debug("Caught item is not a vanilla fish, and we have been told to skip non-fish. Skipping.");
            ItemStack vanillaCatch = nonCustom.getItemStack();
            FishUtils.disableGlintOverride(vanillaCatch);
            if (giveDirect && !vanillaCatch.isEmpty()) {
                FishUtils.giveItem(vanillaCatch, event.getPlayer());
                nonCustom.remove();
            } else {
                nonCustom.setItemStack(vanillaCatch);
            }
            return;
        }

        ItemStack fish = getCaughtItem(event.getPlayer(), event.getHook().getLocation(), rod);
        Fish caughtFishRef = takeLastCaughtFish();

        if (fish == null) {
            plugin.debug("Could not obtain fish.");
            ItemStack vanillaCatch = nonCustom.getItemStack();
            FishUtils.disableGlintOverride(vanillaCatch);
            if (giveDirect && !vanillaCatch.isEmpty()) {
                FishUtils.giveItem(vanillaCatch, event.getPlayer());
                nonCustom.remove();
            } else {
                nonCustom.setItemStack(vanillaCatch);
            }
            return;
        }

        int comboStreak = incrementComboStreak(event.getPlayer().getUniqueId());
        int comboBonusPercent = getComboBonusPercent(comboStreak);

        // Apply Custom Rod, Combo Streak & Competition Buff effects (Double Catch, EXP Multiplier, Worth Bonus)
        boolean doubleCatch = applyPostCatchEffects(event, rod, fish, caughtFishRef, comboStreak, comboBonusPercent);
        FishUtils.disableGlintOverride(fish);

        // Grant LeDatSkills FISHING XP scaled by rarity
        TurtleStorageFishBridge.grantFishingSkillXp(event.getPlayer(), caughtFishRef != null ? caughtFishRef : FishUtils.getFish(fish));

        // replaces the fishing item with a custom evenmorefish fish.
        if (fish.isEmpty()) {
            nonCustom.remove();
            return;
        }

        ItemStack visualFishItem = fish;
        int totalAmount = fish.getAmount();
        int depositedToStorage = TurtleStorageFishBridge.depositCaughtFish(
            event.getPlayer(),
            caughtFishRef != null ? caughtFishRef : FishUtils.getFish(fish),
            fish,
            totalAmount
        );
        if (depositedToStorage >= totalAmount) {
            nonCustom.remove();
            playCatchVisual(event, visualFishItem, caughtFishRef, comboStreak, comboBonusPercent, doubleCatch);
            return;
        }
        if (depositedToStorage > 0) {
            try {
                visualFishItem = fish.clone();
            } catch (Throwable ignored) {
            }
            fish.setAmount(totalAmount - depositedToStorage);
        }

        if (giveDirect && (autoFishingActive || FishUtils.inventoryHasSpace(event.getPlayer().getInventory()))) {
            FishUtils.giveItem(fish, event.getPlayer());
            nonCustom.remove();
            playCatchVisual(event, visualFishItem, caughtFishRef, comboStreak, comboBonusPercent, doubleCatch);
            return;
        }

        nonCustom.setItemStack(fish);
        playCatchVisual(event, visualFishItem, caughtFishRef, comboStreak, comboBonusPercent, doubleCatch);
    }

    /** Cosmetics follow normal delivery/replacement and can never prevent a catch. */
    private void playCatchVisual(
            PlayerFishEvent event,
            ItemStack delivered,
            @Nullable Fish directFish,
            int comboStreak,
            int comboBonusPercent,
            boolean doubleCatch
    ) {
        try {
            Fish fish = directFish != null ? directFish : FishUtils.getFish(delivered);
            if (fish == null) return;
            pendingVisuals.put(event.getHook().getUniqueId(), new PendingCatchVisual(
                event.getHook().getLocation().clone(),
                fish,
                delivered.clone(),
                comboStreak,
                comboBonusPercent,
                doubleCatch
            ));
        } catch (RuntimeException | LinkageError error) {
            plugin.debug("Catch cosmetic failed: " + error.getMessage());
        }
    }

    /** Wait for all cancellable handlers before starting ordinary fishing cosmetics or tracking a pool. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterFishEvent(@NotNull PlayerFishEvent event) {
        UUID hookId = event.getHook().getUniqueId();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            CastSnapshot cast = castSnapshots.get(event.getPlayer().getUniqueId());
            if (event.isCancelled()) {
                if (cast != null && cast.hookId().equals(hookId)) castSnapshots.remove(event.getPlayer().getUniqueId());
            } else if (cast != null && cast.hookId().equals(hookId) && plugin.getFishingPools() != null) {
                plugin.getFishingPools().track(event.getHook());
            }
            return;
        }
        if (event.getState() == PlayerFishEvent.State.BITE) {
            boolean accepted = acceptedBites.remove(hookId);
            try {
                ItemStack rod = accepted && !event.isCancelled() ? getRod(event) : null;
                if (rod != null && isCustomFishAllowed(event.getPlayer()) && Checks.canUseRod(rod)
                    && (!MainConfig.getInstance().requireFishingPermission() || event.getPlayer().hasPermission(UserPerms.USE_ROD))
                    ) {
                    AnimationManager.getInstance().playHookBiteParticles(event.getPlayer(), event.getHook().getLocation());
                    if (plugin.getHookedFishPackets() != null)
                        plugin.getHookedFishPackets().bite(event.getPlayer(), event.getHook());
                }
            } catch (RuntimeException | LinkageError failure) {
                plugin.debug("Bite cosmetic failed: " + failure.getMessage());
            }
            return;
        }
        PendingCatchVisual pending = pendingVisuals.remove(hookId);
        if (pending == null || event.isCancelled()) {
            if (plugin.getCatchCinematics() != null) {
                plugin.getCatchCinematics().flushPendingCallbacks(event.getPlayer().getUniqueId());
            }
            return;
        }
        try {
            if (pending.doubleCatch()) AnimationManager.getInstance().playDoubleFishAnimation(event.getPlayer(), pending.fish());
            boolean played = plugin.getCatchCinematics() != null && plugin.getCatchCinematics().play(
                event.getPlayer(),
                pending.water(),
                pending.fish(),
                pending.item(),
                pending.comboStreak(),
                pending.comboBonusPercent(),
                pending.doubleCatch()
            );
            if (!played) {
                if (plugin.getCatchCinematics() != null) {
                    plugin.getCatchCinematics().flushPendingCallbacks(event.getPlayer().getUniqueId());
                }
                AnimationManager.getInstance().playRareFishCatch(event.getPlayer(), pending.fish());
            }
        } catch (RuntimeException | LinkageError error) {
            if (plugin.getCatchCinematics() != null) {
                plugin.getCatchCinematics().flushPendingCallbacks(event.getPlayer().getUniqueId());
            }
            plugin.debug("Catch cosmetic failed: " + error.getMessage());
        }
    }

    private void applyLureAndCastEffects(@NotNull PlayerFishEvent event, @NotNull ItemStack rodItem) {
        CustomRod customRod = RodManager.getInstance().getRod(rodItem);
        com.oheers.fish.fishing.rods.MMOItemsRodStats mmoStats = com.oheers.fish.fishing.rods.MMOItemsRodStats.resolve(rodItem);
        UUID playerId = event.getPlayer().getUniqueId();
        double buffLure = CompetitionBuffManager.getInstance().getLureBonus(playerId);
        double rodLure = (customRod != null ? customRod.getLureModifier() : 0.0) + mmoStats.lureModifier();
        double autoLure = com.oheers.fish.autofish.AutoFishingService.getActiveAutoLureBonus(event.getPlayer());
        double comboLure = Math.min(0.25, getComboStreak(playerId) * 0.025);
        double totalLure = Math.min(0.82, buffLure + rodLure + autoLure + comboLure);

        try {
            // Snappy LeDatRNG/SkyMine countdown window (2.5s - 7.0s base, faster with Lure & Combo)
            int targetMin = Math.max(20, (int) Math.round(50 * (1.0 - totalLure)));
            int targetMax = Math.max(targetMin + 20, (int) Math.round(140 * (1.0 - totalLure)));
            event.getHook().setMinWaitTime(targetMin);
            event.getHook().setMaxWaitTime(targetMax);
            if (event.getHook().getWaitTime() > targetMax) {
                event.getHook().setWaitTime(targetMax);
            }
        } catch (Throwable ignored) {
            // Fallback for servers without getMinWaitTime
        }
    }

    private record CatchModifiers(double doubleChance, double expMultiplier) {}

    /** Keep original EMF double-catch / XP formulas consistent across engines, with base + combo double-catch. */
    private CatchModifiers catchModifiers(Player player, ItemStack rodItem) {
        CustomRod rod = RodManager.getInstance().getRod(rodItem);
        com.oheers.fish.fishing.rods.MMOItemsRodStats mmoStats = com.oheers.fish.fishing.rods.MMOItemsRodStats.resolve(rodItem);
        var buffs = CompetitionBuffManager.getInstance();
        int streak = getComboStreak(player.getUniqueId());
        double baseDouble = 0.08 + Math.min(0.10, Math.max(0, streak - 1) * 0.005);
        double doubleChance = baseDouble
            + buffs.getDoubleCatchChance(player.getUniqueId())
            + (rod == null ? 0.0 : rod.getDoubleCatchChance())
            + mmoStats.doubleCatchChance()
            + com.oheers.fish.autofish.AutoFishingService.getActiveAutoDoubleCatchBonus(player);
        double baseExp = Math.max(buffs.getExpMultiplier(player.getUniqueId()),
            rod == null ? 1.0 : rod.getExpMultiplier());
        double exp = baseExp + mmoStats.expBonus();
        return new CatchModifiers(Math.max(0, Math.min(0.85, doubleChance)), Math.max(0, exp));
    }

    /** Deterministic bonus choice per Simulator commit. Visuals play only after delivery. */
    public record SimulatorBonuses(Fish fish, boolean doubleCatch, int exp) {}

    public SimulatorBonuses prepareSimulatorBonuses(@NotNull Player player, @NotNull ItemStack rod,
                                                     @NotNull ItemStack delivered,
                                                     @NotNull Fish fish, int baseExperience) {
        CatchModifiers modifier=catchModifiers(player,rod);
        boolean doubled=modifier.doubleChance()>0 && Math.random()<modifier.doubleChance();
        if(doubled) {
            // ItemStack amount must not exceed its material stack maximum.
            delivered.setAmount((int)Math.min(delivered.getMaxStackSize(), (long)delivered.getAmount()*2));
        }
        int exp=(int)Math.min(1000,Math.max(0,Math.round(baseExperience*modifier.expMultiplier())));
        return new SimulatorBonuses(fish,doubled,exp);
    }

    public void playSimulatorCatchAnimations(@NotNull Player player,@NotNull SimulatorBonuses bonuses) {
        playSimulatorCatchAnimations(player,bonuses,null);
    }

    /** Simulator catches use the same private rare-catch cinematic as ordinary fishing when eligible. */
    public void playSimulatorCatchAnimations(@NotNull Player player,@NotNull SimulatorBonuses bonuses,
                                             @Nullable Location water) {
        int streak = incrementComboStreak(player.getUniqueId());
        int bonusPct = getComboBonusPercent(streak);
        if(bonuses.doubleCatch())AnimationManager.getInstance().playDoubleFishAnimation(player,bonuses.fish());
        boolean played=false;
        if(water!=null && plugin.getCatchCinematics()!=null){
            try {
                ItemStack preview=bonuses.fish().give();
                preview.setAmount(bonuses.doubleCatch() ? 2 : 1);
                played=plugin.getCatchCinematics().play(player,water,bonuses.fish(),preview,streak,bonusPct,bonuses.doubleCatch());
            }catch(RuntimeException | LinkageError ignored){}
        }
        if(!played)AnimationManager.getInstance().playRareFishCatch(player,bonuses.fish());
    }

    private boolean applyPostCatchEffects(
            @NotNull PlayerFishEvent event,
            @NotNull ItemStack rodItem,
            @NotNull ItemStack fishItem,
            @Nullable Fish directFish,
            int comboStreak,
            int comboBonusPercent
    ) {
        Fish fish = directFish != null ? directFish : FishUtils.getFish(fishItem);
        if (fish == null) return false;
        Player player = event.getPlayer();
        CatchModifiers modifier = catchModifiers(player, rodItem);
        double tripleChance = comboStreak >= 5
                ? 0.02 + Math.min(0.05, (comboStreak - 5) * 0.003)
                : 0.0;
        boolean tripled = tripleChance > 0 && Math.random() < tripleChance;
        boolean doubled = !tripled && modifier.doubleChance() > 0 && Math.random() < modifier.doubleChance();

        if (tripled) {
            fishItem.setAmount(Math.min(fishItem.getMaxStackSize(), fishItem.getAmount() * 3));
            if (fish.hasCatchRewards()) {
                try {
                    for (int i = 0; i < 2; i++) {
                        fish.getCatchRewards().forEach(reward -> reward.rewardPlayer(player, event.getHook().getLocation()));
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        } else if (doubled) {
            fishItem.setAmount(Math.min(fishItem.getMaxStackSize(), fishItem.getAmount() * 2));
            if (fish.hasCatchRewards()) {
                try {
                    fish.getCatchRewards().forEach(reward -> reward.rewardPlayer(player, event.getHook().getLocation()));
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }

        if (comboStreak == 5 || comboStreak == 10 || comboStreak == 15 || comboStreak == 20 || comboStreak == 30 || comboStreak == 50) {
            Runnable milestoneToast = () -> {
                if (!player.isOnline()) return;
                net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
                player.sendMessage(mm.deserialize(
                    "<gradient:#FF512F:#F09819><bold>🔥 CHUỖI COMBO CÂU CÁ x" + comboStreak + "! 🔥</bold></gradient> "
                    + "<white>Thưởng <green>+" + comboBonusPercent + "% Xu & EXP</green>, tăng tốc cá cắn câu & mở khóa tỉ lệ <aqua>Đại Bạo Kích x3</aqua>!</white>"
                ));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.3f);
            };
            var cinematics = plugin.getCatchCinematics();
            if (cinematics != null && cinematics.enabled() && cinematics.eligible(fish)) {
                cinematics.enqueueRevealCallback(player.getUniqueId(), milestoneToast);
            } else {
                milestoneToast.run();
            }
        }

        double expBonus = modifier.expMultiplier() * (1.0 + Math.max(0, comboBonusPercent) / 100.0);
        if (expBonus > 1.0 && event.getExpToDrop() > 0) {
            event.setExpToDrop((int) Math.round(event.getExpToDrop() * expBonus));
        }
        return doubled || tripled;
    }

    @Override
    protected boolean isEnabled() {
        return MainConfig.getInstance().isCatchEnabled();
    }

    @Override
    protected boolean competitionOnlyCheck() {
        Competition active = Competition.getCurrentlyActive();

        if (active != null) {
            return active.getCompetitionFile().isAllowFishing();
        }

        return !MainConfig.getInstance().isFishCatchOnlyInCompetition();
    }

    @Override
    protected boolean fireEvent(@NotNull Fish fish, @NotNull Player player) {
        return new EMFFishCaughtEvent(fish, player, LocalDateTime.now()).callEvent();
    }

    @Override
    protected ConfigMessage getCaughtMessage() {
        return ConfigMessage.FISH_CAUGHT;
    }

    @Override
    protected ConfigMessage getLengthlessCaughtMessage() {
        return ConfigMessage.FISH_LENGTHLESS_CAUGHT;
    }

    @Override
    protected boolean shouldCatchBait() {
        return true;
    }

    @Override
    public boolean canUseFish(@NotNull Fish fish) {
        return fish.getCatchType().equals(CatchType.CATCH)
                || fish.getCatchType().equals(CatchType.BOTH);
    }

    private @Nullable ItemStack getRod(@NotNull PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            EquipmentSlot hand = event.getHand();
            return hand == null ? null : player.getInventory().getItem(hand);
        }

        // Never search "either hand" on reel: that allows swapping in a more
        // valuable rod after casting. Re-evaluate the exact cast hand/item.
        CastSnapshot cast = castSnapshots.get(player.getUniqueId());
        if (cast == null || !cast.hookId().equals(event.getHook().getUniqueId()) ||
            System.nanoTime() - cast.castTime() > CAST_TTL_NANOS) {
            return null;
        }
        ItemStack current = player.getInventory().getItem(cast.hand());
        if (current == null || current.getType() != Material.FISHING_ROD ||
            !rodFingerprint(current).isSimilar(cast.signature())) {
            plugin.debug("Cast rod changed before reel for " + player.getUniqueId());
            return null;
        }
        return current;
    }

    private static ItemStack rodFingerprint(@NotNull ItemStack rod) {
        ItemStack snapshot = rod.clone();
        // Ordinary durability loss during a cast must not invalidate identity.
        if (snapshot.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            snapshot.setItemMeta(damageable);
        }
        return snapshot;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        var simulator=plugin.getSimulatorModule();
        // Lazy async preferences also apply to ordinary fishing cosmetics; no disk I/O on join.
        if(simulator!=null && ((simulator.settings()!=null && simulator.settings().enabled())
            || (plugin.getCatchCinematics()!=null && plugin.getCatchCinematics().enabled())
            || plugin.getHookedFishPackets()!=null))
            simulator.prefetchHud(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        castSnapshots.remove(playerId);
        COMBO_STREAKS.remove(playerId);
        if (plugin.getCatchCinematics() != null) plugin.getCatchCinematics().quit(playerId);
        if (plugin.getFishingPools() != null) plugin.getFishingPools().quit(playerId);
        if (plugin.getHookedFishPackets() != null) plugin.getHookedFishPackets().quit(playerId);
        if (plugin.getSimulatorModule() != null) plugin.getSimulatorModule().quit(playerId);
    }

    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        castSnapshots.remove(event.getPlayer().getUniqueId());
        if (plugin.getCatchCinematics() != null) plugin.getCatchCinematics().stop(event.getPlayer().getUniqueId());
        if (plugin.getFishingPools() != null) plugin.getFishingPools().quit(event.getPlayer().getUniqueId());
        if (plugin.getHookedFishPackets() != null) plugin.getHookedFishPackets().stop(event.getPlayer().getUniqueId());
    }

    private void handleHookHologram(@NotNull PlayerFishEvent event) {
        switch (event.getState()) {
            case FISHING -> hookHolograms.showWaiting(event.getHook());
            case BITE -> hookHolograms.showBite(event.getHook());
            case FAILED_ATTEMPT -> hookHolograms.showMissed(event.getHook());
            case CAUGHT_FISH, CAUGHT_ENTITY, REEL_IN, IN_GROUND -> hookHolograms.remove(event.getHook());
            default -> {
                // Other states do not affect the current hook hologram.
            }
        }
    }

    @EventHandler
    public void onPluginDisable(@NotNull PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            hookHolograms.clear();
            castSnapshots.clear();
            pendingVisuals.clear();
            acceptedBites.clear();
            COMBO_STREAKS.clear();
            if (plugin.getSimulatorModule() != null) plugin.getSimulatorModule().shutdown();
        }
    }

}
