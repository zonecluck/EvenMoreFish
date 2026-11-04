package com.oheers.fish.autofish;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.Checks;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.fishing.pool.FishingPoolService;
import com.oheers.fish.selling.SellHelper;
import com.oheers.fish.selling.TurtleStorageFishBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Main-thread human-like Auto-Fishing AI controller.
 * Executes realistic casts with Smart Water Aim Assist, adaptive focus rhythm,
 * variable-reaction reels, miss-chance simulation, auto rod switching,
 * auto fish selling on full inventory, and VIP package buffs.
 */
public final class AutoFishingService implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));

    public static final class ActiveSession {
        private final UUID playerId;
        private long nextCastTick;
        private long scheduledReelTick = -1L;
        private boolean scheduledOutcomeIsMiss = false;
        private long castVerifyWaterTick = -1L;
        private long hookSpawnTick = -1L;
        private long hookMaxWaitTicks = 700L;
        private int consecutiveDryCasts = 0;
        private int noRodSeconds = 0;
        private int sessionCatches = 0;
        private int sessionMisses = 0;
        private int sessionDurabilitySaved = 0;
        private int sessionAutoSells = 0;
        private int sessionRodSwaps = 0;
        private int focusStreak = 0;
        private int catchesUntilMicroBreak = randomRange(12, 18);

        ActiveSession(UUID playerId, long initialCastTick) {
            this.playerId = playerId;
            this.nextCastTick = initialCastTick;
        }

        public int getSessionCatches() {
            return sessionCatches;
        }

        public int getSessionMisses() {
            return sessionMisses;
        }

        public int getSessionDurabilitySaved() {
            return sessionDurabilitySaved;
        }

        public int getSessionAutoSells() {
            return sessionAutoSells;
        }

        public int getSessionRodSwaps() {
            return sessionRodSwaps;
        }

        public int getFocusStreak() {
            return focusStreak;
        }

        /**
         * Combo focus reduces effective miss chance by up to 3.5% at 10+ streak.
         */
        public double getFocusMissReductionBonus() {
            return Math.min(3.5D, focusStreak * 0.35D);
        }
    }

    private final EvenMoreFish plugin;
    private final AutoFishDataStore dataStore;
    private final Map<UUID, ActiveSession> activeSessions = new ConcurrentHashMap<>();
    private MyScheduledTask tickerTask;
    private long ticks = 0L;

    public AutoFishingService(@NotNull EvenMoreFish plugin) {
        this.plugin = plugin;
        this.dataStore = new AutoFishDataStore(plugin);
    }

    @NotNull
    public AutoFishDataStore getDataStore() {
        return dataStore;
    }

    @NotNull
    public AutoFishProfile getProfile(@NotNull UUID playerId) {
        return dataStore.getOrCreate(playerId);
    }

    public boolean isActive(@NotNull UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    @Nullable
    public ActiveSession getActiveSession(@NotNull UUID playerId) {
        return activeSessions.get(playerId);
    }

    // ── Static VIP Package Buff Resolvers for Gameplay Integration ──

    public static double getActiveAutoLuckBonus(@Nullable Player player) {
        if (player == null) return 0.0D;
        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) return 0.0D;
        AutoFishProfile profile = service.getProfile(player.getUniqueId());
        return profile.getRemainingSeconds() > 0L ? profile.getPackageLuckBonus() : 0.0D;
    }

    public static double getActiveAutoLureBonus(@Nullable Player player) {
        if (player == null) return 0.0D;
        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) return 0.0D;
        AutoFishProfile profile = service.getProfile(player.getUniqueId());
        return profile.getRemainingSeconds() > 0L ? profile.getPackageLureBonus() : 0.0D;
    }

    public static double getActiveAutoDoubleCatchBonus(@Nullable Player player) {
        if (player == null) return 0.0D;
        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) return 0.0D;
        AutoFishProfile profile = service.getProfile(player.getUniqueId());
        return profile.getRemainingSeconds() > 0L ? profile.getPackageDoubleCatchBonus() : 0.0D;
    }

    public static double getActiveSellBonusPercent(@Nullable Player player) {
        if (player == null) return 0.0D;
        AutoFishingService service = EvenMoreFish.getInstance().getAutoFishing();
        if (service == null) return 0.0D;
        AutoFishProfile profile = service.getProfile(player.getUniqueId());
        return profile.getRemainingSeconds() > 0L ? profile.getPackageSellBonusPercent() : 0.0D;
    }

    public boolean toggle(@NotNull Player player) {
        if (isActive(player.getUniqueId())) {
            stop(player, "<yellow>Đã tắt chế độ Auto Câu Cá.</yellow>");
            return false;
        }
        return start(player);
    }

    public boolean start(@NotNull Player player) {
        UUID playerId = player.getUniqueId();
        AutoFishProfile profile = getProfile(playerId);
        if (profile.getRemainingSeconds() <= 0L) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8f, 0.75f);
            player.sendMessage(mm(
                    "<red><bold>✗ HẾT THỜI GIAN AUTO!</bold></red> <white>Bạn đã hết thời gian Auto Câu Cá. Hãy mua thêm Gói Thời Gian trong <yellow>/autofish</yellow>!</white>"
            ));
            return false;
        }

        ActiveSession session = new ActiveSession(playerId, ticks + randomRange(10, 20));
        activeSessions.put(playerId, session);
        ensureTicker();

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.75f, 1.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.70f, 1.30f);

        EquipmentSlot rodHand = findRodHand(player);
        String vipTag = profile.getActivePackageTier() > 0
                ? " <dark_gray>•</dark_gray> " + profile.getActiveTierDisplayName()
                : "";
        if (rodHand == null) {
            player.sendMessage(mm(
                    "<green><bold>✔ ĐÃ BẬT AI AUTO CÂU CÁ!</bold></green> <white>Hãy cầm <aqua>Cần Câu</aqua> trên tay và nhìn xuống mặt nước để bắt đầu (Còn lại: <yellow>"
                            + AutoFishProfile.formatDuration(profile.getRemainingSeconds()) + "</yellow>" + vipTag + ").</white>"
            ));
        } else {
            player.sendMessage(mm(
                    "<green><bold>✔ ĐÃ BẬT AI AUTO CÂU CÁ!</bold></green> <white>AI thông minh sẽ tự căn mặt nước, tự đổi cần & bán cá khi đầy túi (Còn lại: <yellow>"
                            + AutoFishProfile.formatDuration(profile.getRemainingSeconds()) + "</yellow>" + vipTag + ").</white>"
            ));
        }
        return true;
    }

    public void stop(@NotNull Player player, @Nullable String reasonMiniMessage) {
        ActiveSession removed = activeSessions.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        FishHook activeHook = NativeFishingRightClickBridge.findActiveHook(player);
        if (activeHook != null && activeHook.isValid()) {
            activeHook.remove();
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.65f, 1.2f);
        if (reasonMiniMessage != null && !reasonMiniMessage.isBlank()) {
            player.sendMessage(mm("<gold><bold>⚡ AUTO CÂU CÁ:</bold></gold> " + reasonMiniMessage));
        }
        dataStore.saveIfDirtyAsync();
    }

    public void shutdown() {
        for (UUID playerId : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                FishHook hook = NativeFishingRightClickBridge.findActiveHook(player);
                if (hook != null && hook.isValid()) {
                    hook.remove();
                }
            }
        }
        activeSessions.clear();
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        dataStore.saveAllSync();
    }

    private void ensureTicker() {
        if (tickerTask == null) {
            tickerTask = EvenMoreFish.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void tick() {
        ticks++;
        if (ticks % 600L == 0L) {
            dataStore.saveIfDirtyAsync();
        }

        if (activeSessions.isEmpty()) {
            if (tickerTask != null) {
                tickerTask.cancel();
                tickerTask = null;
            }
            return;
        }

        boolean secondBoundary = (ticks % 20L == 0L);

        for (Map.Entry<UUID, ActiveSession> entry : List.copyOf(activeSessions.entrySet())) {
            UUID playerId = entry.getKey();
            ActiveSession session = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                activeSessions.remove(playerId);
                continue;
            }

            AutoFishProfile profile = getProfile(playerId);
            EquipmentSlot rodHand = findRodHand(player);

            // 1. Check if player is holding a valid Fishing Rod (or AI swap from inventory)
            if (rodHand == null) {
                if (profile.isAutoSwitchRod() && tryEquipBackupRodToMainHand(player, session)) {
                    rodHand = EquipmentSlot.HAND;
                } else {
                    if (secondBoundary) {
                        session.noRodSeconds++;
                        if (session.noRodSeconds % 4 == 0) {
                            player.sendActionBar(mm(
                                    "<dark_gray>○ <yellow>⚠ AI Auto Câu Cá đang tạm chờ: Hãy cầm Cần Câu trên tay!</yellow>"
                            ));
                        }
                    }
                    continue;
                }
            }
            session.noRodSeconds = 0;

            ItemStack rod = player.getInventory().getItem(rodHand);
            if (rod == null || !Checks.canUseRod(rod) || isRodAboutToBreak(rod)) {
                if (profile.isAutoSwitchRod() && tryAutoSwitchSpareRod(player, rodHand, session)) {
                    rod = player.getInventory().getItem(rodHand);
                }
                if (rod == null || !Checks.canUseRod(rod) || isRodAboutToBreak(rod)) {
                    stop(player, "<red>Cần câu trên tay sắp gãy và không còn cần dự phòng trong túi! Đã tạm dừng để bảo vệ cần câu.</red>");
                    continue;
                }
            }

            // 2. Consume 1 second of Auto-Fishing time every 20 ticks while holding rod
            if (secondBoundary) {
                if (!profile.consumeOneSecond()) {
                    stop(player, "<red>Đã hết gói thời gian Auto Câu Cá! Hãy mua thêm thời gian trong <yellow>/autofish</yellow>.</red>");
                    continue;
                }
                long remaining = profile.getRemainingSeconds();
                if (remaining == 60L || remaining == 15L) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 0.8f, 1.6f);
                    player.sendMessage(mm(
                            "<yellow>⏳ Thời gian Auto Câu Cá chỉ còn <gold>" + remaining
                                    + " giây</gold>! Gõ <aqua>/autofish</aqua> để gia hạn thêm.</yellow>"
                    ));
                }
                if (ticks % 80L == 0L && session.scheduledReelTick < 0L) {
                    String streakInfo = session.focusStreak >= 3
                            ? " <dark_gray>•</dark_gray> <gold>🔥 Combo x" + session.focusStreak + "</gold>"
                            : "";
                    player.sendActionBar(mm(
                            "<dark_gray>○ <aqua>🤖 AI Auto</aqua> <dark_gray>•</dark_gray> <white>Còn:</white> <yellow>"
                                    + AutoFishProfile.formatCompactDuration(remaining)
                                    + "</yellow> <dark_gray>•</dark_gray> <white>Đã câu:</white> <green>"
                                    + session.sessionCatches + " 🐟</green>" + streakInfo
                    ));
                }
            }

            FishHook activeHook = NativeFishingRightClickBridge.findActiveHook(player);

            // 3. Execute scheduled Auto-Reel when a fish has bitten
            if (session.scheduledReelTick > 0L && ticks >= session.scheduledReelTick) {
                session.scheduledReelTick = -1L;
                if (activeHook != null && activeHook.isValid()) {
                    if (session.scheduledOutcomeIsMiss) {
                        session.sessionMisses++;
                        session.focusStreak = 0;
                        NativeFishingRightClickBridge.simulateMissedBiteOrReel(
                                player,
                                rodHand,
                                activeHook,
                                !profile.isAutoRecastOnMiss()
                        );
                        player.sendActionBar(mm(
                                "<dark_gray>○ <red>✗ AI Auto: Giật trượt nhịp cắn câu!</red> <gray>(Tỉ lệ hụt: "
                                        + profile.getMissChancePercent() + "% • Nâng cấp trong /autofish)</gray>"
                        ));
                        if (profile.isAutoRecastOnMiss()) {
                            session.nextCastTick = ticks + computeHumanCastDelayTicks(profile, session);
                        }
                    } else {
                        int damageBefore = getRodDamage(rod);
                        boolean reeled = NativeFishingRightClickBridge.reelBitingHook(player, rodHand, activeHook);
                        if (reeled) {
                            maybeRestoreRodDurability(player, rodHand, damageBefore, profile, session);
                        }
                        session.nextCastTick = ticks + computeHumanCastDelayTicks(profile, session);
                    }
                }
                continue;
            }

            // 4. Verify that a newly thrown bobber actually landed in water + AI Stuck Watchdog
            if (activeHook != null && activeHook.isValid()) {
                // Watchdog: if hook accidentally hooked an entity/mob or timed out (>35s), reel in & recast
                if (activeHook.getHookedEntity() != null
                        || (session.hookSpawnTick > 0L && ticks - session.hookSpawnTick > session.hookMaxWaitTicks)) {
                    activeHook.remove();
                    NativeFishingRightClickBridge.swingHand(player, rodHand);
                    session.hookSpawnTick = -1L;
                    session.nextCastTick = ticks + randomRange(24, 38);
                    continue;
                }

                if (session.castVerifyWaterTick > 0L && ticks >= session.castVerifyWaterTick) {
                    session.castVerifyWaterTick = -1L;
                    boolean inWater = activeHook.getState() == FishHook.HookState.BOBBING
                            || FishingPoolService.isWater(activeHook.getLocation());
                    if (!inWater) {
                        activeHook.remove();
                        NativeFishingRightClickBridge.swingHand(player, rodHand);
                        session.consecutiveDryCasts++;
                        if (session.consecutiveDryCasts >= 3) {
                            stop(player, "<red>Phao câu rơi trên cạn 3 lần liên tiếp! Hãy đứng gần hồ nước hơn rồi bật lại <yellow>/autofish</yellow>.</red>");
                            continue;
                        }
                        player.sendActionBar(mm(
                                "<dark_gray>○ <yellow>⚠ AI đang tự căn chỉnh lại góc ném phao xuống mặt nước...</yellow>"
                        ));
                        session.nextCastTick = ticks + randomRange(32, 48);
                        continue;
                    } else {
                        session.consecutiveDryCasts = 0;
                    }
                }
            }

            // 5. Execute Auto-Cast when no hook is out and the human recast delay has elapsed
            if (activeHook == null && ticks < session.nextCastTick && ticks % 4L == 0L) {
                var cinematics = plugin.getCatchCinematics();
                if (cinematics == null || !cinematics.isRollingOrShowcasing(playerId)) {
                    double remainSeconds = Math.max(0.1D, (session.nextCastTick - ticks) / 20.0D);
                    int streak = com.oheers.fish.fishing.FishingProcessor.getComboStreak(playerId);
                    String comboTag = streak >= 2
                            ? " <dark_gray>•</dark_gray> <gradient:#FF512F:#F09819>🔥 Combo x" + streak + "</gradient>"
                            : "";
                    player.sendActionBar(mm(
                            "<dark_gray>○ <aqua>🤖 AI Auto</aqua> <dark_gray>•</dark_gray> <yellow>⏳ Quăng cần tiếp: <white>"
                                    + String.format(java.util.Locale.US, "%.1fs", remainSeconds)
                                    + "</white></yellow>" + comboTag
                                    + " <dark_gray>•</dark_gray> <green>⏱ "
                                    + AutoFishProfile.formatCompactDuration(profile.getRemainingSeconds()) + "</green>"
                    ));
                }
            }
            if (activeHook == null && ticks >= session.nextCastTick) {
                if (!FishUtils.inventoryHasSpace(player.getInventory()) && !TurtleStorageFishBridge.canAcceptCaughtFish(player)) {
                    if (profile.isAutoSellOnFullInventory() && tryAutoSellInventory(player, session)) {
                        // Inventory / storage space freed by AI Auto-Sell!
                    } else if (profile.isStopOnFullInventory()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 0.85f, 1.0f);
                        stop(player, "<red>🎒 Túi đồ & Kho Chứa đã đầy! AI Auto Câu Cá tự động dừng để tránh rơi mất cá.</red>");
                        continue;
                    }
                }
                FishHook spawned = NativeFishingRightClickBridge.castRod(player, rodHand);
                if (spawned != null) {
                    session.hookSpawnTick = ticks;
                    session.hookMaxWaitTicks = randomRange(660, 760);
                    session.castVerifyWaterTick = ticks + randomRange(34, 44);
                    session.nextCastTick = ticks + computeHumanCastDelayTicks(profile, session) + 40L;
                } else {
                    session.nextCastTick = ticks + randomRange(40, 65);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(@NotNull PlayerFishEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        ActiveSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        AutoFishProfile profile = getProfile(player.getUniqueId());

        switch (event.getState()) {
            case FISHING -> {
                session.scheduledReelTick = -1L;
                session.hookSpawnTick = ticks;
                session.castVerifyWaterTick = ticks + randomRange(34, 44);
            }
            case BITE -> {
                double effectiveMissPercent = Math.max(
                        1.0D,
                        profile.getMissChancePercent() - session.getFocusMissReductionBonus()
                );
                double missRoll = ThreadLocalRandom.current().nextDouble(100.0D);
                boolean willMiss = missRoll < effectiveMissPercent;
                session.scheduledOutcomeIsMiss = willMiss;
                if (willMiss) {
                    // Simulate a human reacting slightly too late/off-beat
                    session.scheduledReelTick = ticks + randomRange(15, 23);
                } else {
                    // Human reaction time with natural Gaussian jitter + focus streak sharpness
                    int baseTicks = profile.getBaseReactionTicks();
                    int focusBonusTicks = session.focusStreak >= 5 ? 1 : 0;
                    int jitter = (int) Math.round(ThreadLocalRandom.current().nextGaussian() * 1.6D);
                    int reactionTicks = Math.max(8, Math.min(16, baseTicks - focusBonusTicks + jitter));
                    session.scheduledReelTick = ticks + reactionTicks;
                }
            }
            case CAUGHT_FISH -> {
                // A successful right-click can still be cancelled by another listener.
                // Only the accepted native catch event advances Auto statistics and focus.
                session.sessionCatches++;
                session.focusStreak++;
                profile.incrementTotalAutoCatches();
                session.scheduledReelTick = -1L;
                session.castVerifyWaterTick = -1L;
                session.hookSpawnTick = -1L;
                session.nextCastTick = Math.max(session.nextCastTick, ticks + computeHumanCastDelayTicks(profile, session));
            }
            case CAUGHT_ENTITY, REEL_IN -> {
                session.scheduledReelTick = -1L;
                session.castVerifyWaterTick = -1L;
                session.hookSpawnTick = -1L;
                session.nextCastTick = Math.max(session.nextCastTick, ticks + computeHumanCastDelayTicks(profile, session));
            }
            case IN_GROUND -> {
                session.scheduledReelTick = -1L;
                session.castVerifyWaterTick = ticks + 4L;
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
        dataStore.saveIfDirtyAsync();
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        stop(event.getPlayer(), "<yellow>Đã tự động tạm dừng Auto Câu Cá do bạn chuyển thế giới.</yellow>");
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        stop(event.getEntity(), "<red>Đã tự động tắt Auto Câu Cá do nhân vật tử trận.</red>");
    }

    private static long computeHumanCastDelayTicks(@NotNull AutoFishProfile profile, @NotNull ActiveSession session) {
        int baseTicks = profile.getBaseCastDelayTicks();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int variance = Math.max(4, (int) Math.round(baseTicks * 0.18D));
        int jitter = rng.nextInt(-variance / 2, variance + 8);
        long delay = Math.max(20L, baseTicks + jitter);

        // AI Human Rhythm: occasional micro-break every 12..18 catches
        if (session.sessionCatches > 0 && session.sessionCatches >= session.catchesUntilMicroBreak) {
            session.catchesUntilMicroBreak = session.sessionCatches + randomRange(12, 18);
            delay += randomRange(24, 44);
        }
        return delay;
    }

    private boolean tryAutoSellInventory(@NotNull Player player, @NotNull ActiveSession session) {
        try {
            long totalSold = 0L;
            SellHelper seller = new SellHelper(player.getInventory(), player);
            if (seller.getCount() > 0) {
                totalSold += seller.getCount();
                seller.sell();
            }
            TurtleStorageFishBridge.SellSummary storageSale = TurtleStorageFishBridge.sellStoredFish(
                player, null, null, Long.MAX_VALUE, totalSold == 0L
            );
            totalSold += storageSale.soldCount();
            if (totalSold <= 0L) {
                return false;
            }
            session.sessionAutoSells++;
            player.sendMessage(mm(
                    "<dark_gray>[<aqua>🤖 AI</aqua>]</dark_gray> <green>Túi đồ/Kho đầy! AI đã tự động bán <yellow>"
                            + totalSold + " con cá</yellow> để tiếp tục câu.</green>"
            ));
            return FishUtils.inventoryHasSpace(player.getInventory()) || TurtleStorageFishBridge.canAcceptCaughtFish(player);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean tryAutoSwitchSpareRod(
            @NotNull Player player,
            @NotNull EquipmentSlot hand,
            @NotNull ActiveSession session
    ) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (candidate == null || candidate.getType() != Material.FISHING_ROD) {
                continue;
            }
            if (hand == EquipmentSlot.HAND && slot == inv.getHeldItemSlot()) {
                continue;
            }
            if (!Checks.canUseRod(candidate) || isRodAboutToBreak(candidate)) {
                continue;
            }
            ItemStack oldRod = inv.getItem(hand);
            inv.setItem(hand, candidate);
            inv.setItem(slot, oldRod);
            session.sessionRodSwaps++;
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.PLAYERS, 0.9f, 1.25f);
            player.sendMessage(mm(
                    "<dark_gray>[<aqua>🤖 AI</aqua>]</dark_gray> <green>Cần câu cũ sắp hỏng! AI đã tự động đổi sang cần câu dự phòng trong túi đồ.</green>"
            ));
            return true;
        }
        return false;
    }

    private static boolean tryEquipBackupRodToMainHand(@NotNull Player player, @NotNull ActiveSession session) {
        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType() != Material.AIR) {
            return false;
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (candidate == null || candidate.getType() != Material.FISHING_ROD) {
                continue;
            }
            if (!Checks.canUseRod(candidate) || isRodAboutToBreak(candidate)) {
                continue;
            }
            inv.setItemInMainHand(candidate);
            inv.setItem(slot, null);
            session.sessionRodSwaps++;
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.PLAYERS, 0.9f, 1.25f);
            player.sendMessage(mm(
                    "<dark_gray>[<aqua>🤖 AI</aqua>]</dark_gray> <green>AI đã tự động trang bị cần câu dự phòng từ túi đồ lên tay chính!</green>"
            ));
            return true;
        }
        return false;
    }

    @Nullable
    public static EquipmentSlot findRodHand(@NotNull Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.FISHING_ROD) {
            return EquipmentSlot.HAND;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() == Material.FISHING_ROD) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    private static boolean isRodAboutToBreak(@NotNull ItemStack rod) {
        int maxDurability = rod.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return false;
        }
        if (rod.getItemMeta() instanceof Damageable damageable) {
            if (rod.getItemMeta().isUnbreakable()) {
                return false;
            }
            return damageable.getDamage() >= maxDurability - 2;
        }
        return false;
    }

    private static int getRodDamage(@NotNull ItemStack rod) {
        if (rod.getItemMeta() instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return 0;
    }

    private static void maybeRestoreRodDurability(
            @NotNull Player player,
            @NotNull EquipmentSlot hand,
            int damageBefore,
            @NotNull AutoFishProfile profile,
            @NotNull ActiveSession session
    ) {
        double saveChance = profile.getDurabilitySavePercent();
        if (saveChance <= 0.0D || ThreadLocalRandom.current().nextDouble(100.0D) >= saveChance) {
            return;
        }
        ItemStack current = player.getInventory().getItem(hand);
        if (current == null || current.getType() != Material.FISHING_ROD) {
            return;
        }
        if (current.getItemMeta() instanceof Damageable damageable && damageable.getDamage() > damageBefore) {
            damageable.setDamage(damageBefore);
            current.setItemMeta(damageable);
            session.sessionDurabilitySaved++;
        }
    }

    // ── Economy Helpers (Vault + EvenMoreFish Economy fallback) ──

    public double getBalance(@NotNull Player player) {
        if (com.oheers.fish.selling.FishExchangeService.isEnabled()) return 0.0D;
        Economy vault = resolveVaultEconomy();
        if (vault != null) {
            try {
                return vault.getBalance(player);
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        var emfEcon = com.oheers.fish.api.economy.Economy.getInstance();
        if (emfEcon != null && emfEcon.isEnabled()) {
            for (var type : emfEcon.getEconomyTypes()) {
                if (type.isAvailable()) {
                    return type.get(player);
                }
            }
        }
        return 0.0D;
    }

    public boolean withdrawMoney(@NotNull Player player, double cost) {
        if (com.oheers.fish.selling.FishExchangeService.isEnabled()) return false;
        if (cost <= 0.0D) {
            return true;
        }
        Economy vault = resolveVaultEconomy();
        if (vault != null) {
            try {
                if (!vault.has(player, cost)) {
                    return false;
                }
                return vault.withdrawPlayer(player, cost).transactionSuccess();
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        var emfEcon = com.oheers.fish.api.economy.Economy.getInstance();
        if (emfEcon != null && emfEcon.isEnabled() && emfEcon.has(player, cost)) {
            emfEcon.withdraw(player, cost, false);
            return true;
        }
        return false;
    }

    @NotNull
    public static String formatMoney(double amount) {
        return MONEY_FORMAT.format(Math.max(0.0D, amount)) + "$";
    }

    @Nullable
    private static Economy resolveVaultEconomy() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                return null;
            }
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            return rsp != null ? rsp.getProvider() : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static int randomRange(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    @NotNull
    private static Component mm(@NotNull String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
