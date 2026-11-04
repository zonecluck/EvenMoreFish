package com.oheers.fish.effects;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.simulator.effects.CinematicParticleEffects;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import com.oheers.fish.messages.EMFSingleMessage;
import com.oheers.fish.selling.WorthNBT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Paper owner thread only. Private, nonpersistent ItemDisplay + TextDisplay scene
 * playing a high-quality LeDatRNG-style Roll Animation (including Stage-1 7-slot
 * Legendary Material Wheel for Legendary+ catches) above the bobber, followed by
 * a skyward "Byebye!" wave and fade-out.
 */
public final class VanillaCatchCinematicService {
    private static final int FRAME_TICKS = 1;
    private static final int PARTICLES_PER_FRAME = 10;
    private static final int MAX_SCENES_PER_PLAYER = 3;
    private static final float ITEM_BASE_SCALE = 1.18f;
    private static final float TEXT_BASE_SCALE = 1.04f;
    private static final float ITEM_VERTICAL_OFFSET = 0.0f;
    private static final float TEXT_VERTICAL_OFFSET = 0.78f;
    private static final double HALO_TILT_RADIANS = Math.toRadians(18.0);
    private static final DecimalFormat LENGTH_FORMAT = new DecimalFormat("#,##0.#");
    private static final DecimalFormat WORTH_FORMAT = new DecimalFormat("#,##0.#");

    private static final String[] ROLL_PROGRESS_BARS = {
            "<gradient:#00F5FF:#55FFFF>▰</gradient><dark_gray>▱▱▱▱</dark_gray> <gray>Đang quay.</gray>",
            "<gradient:#00F5FF:#55FFFF>▰▰</gradient><dark_gray>▱▱▱</dark_gray> <gray>Đang quay..</gray>",
            "<gradient:#00F5FF:#55FFFF>▰▰▰</gradient><dark_gray>▱▱</dark_gray> <gray>Đang quay...</gray>",
            "<gradient:#FFE259:#FFA751>▰▰▰▰</gradient><dark_gray>▱</dark_gray> <yellow>Sắp lộ diện..</yellow>",
            "<gradient:#FFD700:#FF4FD8>▰▰▰▰▰</gradient> <gold>✦ Mở khóa! ✦</gold>"
    };

    private static final Material[] WHEEL_MATERIALS = {
            Material.COAL,
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD,
            Material.NETHERITE_INGOT,
            Material.ELYTRA
    };

    private static final String[] WHEEL_MATERIAL_NAMES = {
            "<gray>Than Đá Thủy Triều</gray>",
            "<white>Phôi Sắt Đại Dương</white>",
            "<gold>Phôi Vàng Hải Vương</gold>",
            "<aqua>Kim Cương Thẳm Sâu</aqua>",
            "<green>Ngọc Lục Bảo Cổ Đại</green>",
            "<gold><bold>Phôi Netherite Huyền Thoại</bold></gold>",
            "<gradient:#FF4FD8:#8C52FF><bold>Cánh Tinh Tú Thần Thoại</bold></gradient>"
    };

    // Cached particle lookups resolved once at class load
    private static final Particle PARTICLE_END_ROD = findParticle("END_ROD", "WAX_ON");
    private static final Particle PARTICLE_SPLASH = findParticle("SPLASH", "WATER_SPLASH");
    private static final Particle PARTICLE_CLOUD = findParticle("CLOUD", "FIREWORKS_SPARK");
    private static final Particle PARTICLE_TOTEM = findParticle("TOTEM_OF_UNDYING", "TOTEM");
    private static final Particle PARTICLE_PORTAL = findParticle("PORTAL", "ENCHANTMENT_TABLE");
    private static final Particle PARTICLE_SOUL_FIRE = findParticle("SOUL_FIRE_FLAME", "FLAME");
    private static final Particle PARTICLE_ENCHANT = findParticle("ENCHANT", "ENCHANTMENT_TABLE");

    private record DecoyEntry(ItemStack item, Component[] progressTexts) {
        Component rollingText(double progress) {
            if (progressTexts == null || progressTexts.length == 0) {
                return Component.empty();
            }
            int idx = Math.max(0, Math.min(progressTexts.length - 1, (int) Math.floor(progress * progressTexts.length)));
            return progressTexts[idx];
        }
    }

    private static final class Scene {
        final UUID playerId;
        final ItemDisplay itemDisplay;
        final TextDisplay textDisplay;
        final List<ItemDisplay> wheelDisplays;
        final int wheelTicks;
        final Location anchor;
        final Location viewerStart;
        final Vector rightAxis;
        final CatchRollTimeline.TierStyle tierStyle;
        final CatchRollTimeline.Durations durations;
        final ItemStack caughtPreview;
        final Component resultText;
        final Component byebyeText;
        final Component resultActionBar;
        final Component farewellActionBar;
        final Title showcaseTitle;
        final int resultExtraLines;
        final boolean doubleCatch;
        final int caughtAmount;
        final int comboStreak;
        final List<DecoyEntry> decoys;
        final List<Runnable> revealCallbacks;
        long startTick;
        final EnumSet<CatchRollTimeline.Phase> enteredPhases = EnumSet.noneOf(CatchRollTimeline.Phase.class);
        int lastWheelHighlight = -1;
        boolean wheelBreakthroughPlayed = false;
        boolean wheelConvergeSoundPlayed = false;
        boolean wheelCleanedUp = false;
        int lastRouletteStep = -1;
        boolean firstCatch = false;
        boolean revealed = false;
        boolean byebyeTextApplied = false;
        boolean textClearedForFade = false;
        boolean holdPoseApplied = false;

        Scene(
                UUID playerId,
                ItemDisplay itemDisplay,
                TextDisplay textDisplay,
                List<ItemDisplay> wheelDisplays,
                int wheelTicks,
                Location anchor,
                Location viewerStart,
                Vector rightAxis,
                CatchRollTimeline.TierStyle tierStyle,
                CatchRollTimeline.Durations durations,
                ItemStack caughtPreview,
                Component resultText,
                Component byebyeText,
                Component resultActionBar,
                Component farewellActionBar,
                Title showcaseTitle,
                int resultExtraLines,
                boolean doubleCatch,
                int caughtAmount,
                int comboStreak,
                List<DecoyEntry> decoys,
                List<Runnable> revealCallbacks,
                long startTick
        ) {
            this.playerId = playerId;
            this.itemDisplay = itemDisplay;
            this.textDisplay = textDisplay;
            this.wheelDisplays = wheelDisplays;
            this.wheelTicks = wheelTicks;
            this.anchor = anchor;
            this.viewerStart = viewerStart;
            this.rightAxis = rightAxis;
            this.tierStyle = tierStyle;
            this.durations = durations;
            this.caughtPreview = caughtPreview;
            this.resultText = resultText;
            this.byebyeText = byebyeText;
            this.resultActionBar = resultActionBar;
            this.farewellActionBar = farewellActionBar;
            this.showcaseTitle = showcaseTitle;
            this.resultExtraLines = resultExtraLines;
            this.doubleCatch = doubleCatch;
            this.caughtAmount = caughtAmount;
            this.comboStreak = comboStreak;
            this.decoys = decoys;
            this.revealCallbacks = revealCallbacks;
            this.startTick = startTick;
        }

        int totalTicks() {
            return wheelTicks + durations.totalTicks();
        }

        int ascendOffsetTicks() {
            return wheelTicks
                    + durations.slideInTicks()
                    + durations.rollingTicks()
                    + durations.revealTicks()
                    + durations.showcaseTicks()
                    + durations.holdTicks();
        }

        void fireRevealCallbacks() {
            if (revealCallbacks == null || revealCallbacks.isEmpty()) return;
            for (Runnable cb : List.copyOf(revealCallbacks)) {
                try {
                    cb.run();
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
            revealCallbacks.clear();
        }
    }

    private final EvenMoreFish plugin;
    private final List<Scene> activeScenes = new ArrayList<>();
    private final Map<UUID, List<Runnable>> pendingRevealCallbacks = new HashMap<>();
    private final java.util.Set<UUID> pendingFirstTimeFlags = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> sessionDiscoveredFish = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastPlayed = new HashMap<>();
    private final List<DecoyEntry> cachedDecoys = new ArrayList<>();
    private long lastDecoyCacheTimeNanos = 0L;
    private MyScheduledTask task;
    private long ticks;
    private long lastWarning;

    public VanillaCatchCinematicService(EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    public void markFirstTimeCatch(UUID playerId) {
        if (playerId == null) return;
        pendingFirstTimeFlags.add(playerId);
        var scheduler = EvenMoreFish.getScheduler();
        if (scheduler != null) {
            scheduler.runTask(() -> {
                for (int i = activeScenes.size() - 1; i >= 0; i--) {
                    Scene s = activeScenes.get(i);
                    if (s.playerId.equals(playerId) && !s.revealed) {
                        s.firstCatch = true;
                        pendingFirstTimeFlags.remove(playerId);
                        try {
                            if (s.itemDisplay.isValid()) {
                                s.itemDisplay.setItemStack(buildFirstCatchChestItem(s.tierStyle));
                            }
                        } catch (RuntimeException | LinkageError ignored) {
                        }
                        break;
                    }
                }
            });
        }
    }

    private boolean isFirstTimeCatch(Player player, Fish fish) {
        if (player == null || fish == null || fish.getRarity() == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        String sessionKey = playerId + ":" + fish.getRarity().getId().toLowerCase(java.util.Locale.ROOT)
            + ":" + fish.getName().toLowerCase(java.util.Locale.ROOT);
        boolean firstInSession = sessionDiscoveredFish.add(sessionKey);

        if (pendingFirstTimeFlags.remove(playerId)) {
            return true;
        }
        try {
            var dataManager = plugin.getPluginDataManager();
            if (dataManager != null && dataManager.getUserManager() != null && dataManager.getUserFishStatsDataManager() != null) {
                int userId = dataManager.getUserManager().getUserId(playerId);
                if (userId > 0) {
                    String key = com.oheers.fish.database.data.UserFishRarityKey.of(userId, fish).toString();
                    var stats = dataManager.getUserFishStatsDataManager().peek(key);
                    if (stats != null) {
                        return stats.getQuantity() <= 1;
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return !com.oheers.fish.config.MainConfig.getInstance().databaseEnabled() && firstInSession;
    }

    private static ItemStack buildFirstCatchChestItem(CatchRollTimeline.TierStyle tierStyle) {
        Material mat = tierStyle.isEpicOrAbove() ? Material.ENDER_CHEST : Material.CHEST;
        return sanitizeDisplayItem(new ItemStack(mat));
    }

    private static Component buildFirstCatchChestText(CatchRollTimeline.TierStyle tierStyle, double progress) {
        int barIdx = Math.max(0, Math.min(ROLL_PROGRESS_BARS.length - 1, (int) Math.floor(progress * ROLL_PROGRESS_BARS.length)));
        String header = tierStyle.isEpicOrAbove()
            ? "<gradient:#FFD700:#FF4FD8><bold>✦ RƯƠNG CỔ VẬT SỔ TAY ✦</bold></gradient>"
            : "<gradient:#55FFFF:#00AAFF><bold>✦ RƯƠNG MỞ KHÓA SỔ TAY ✦</bold></gradient>";
        return EMFSingleMessage.fromString(
            header + "\n<yellow>Đang mở khóa loài cá mới...</yellow>\n" + ROLL_PROGRESS_BARS[barIdx]
        ).getComponentMessage();
    }

    public void enqueueRevealCallback(UUID playerId, Runnable callback) {
        if (playerId == null || callback == null) return;
        for (int i = activeScenes.size() - 1; i >= 0; i--) {
            Scene s = activeScenes.get(i);
            if (s.playerId.equals(playerId) && !s.revealed) {
                s.revealCallbacks.add(callback);
                return;
            }
        }
        pendingRevealCallbacks.computeIfAbsent(playerId, k -> new ArrayList<>()).add(callback);
    }

    public void flushPendingCallbacks(UUID playerId) {
        if (playerId == null) return;
        List<Runnable> callbacks = pendingRevealCallbacks.remove(playerId);
        if (callbacks != null) {
            for (Runnable cb : callbacks) {
                try {
                    cb.run();
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
    }

    public boolean isRollingOrShowcasing(UUID playerId) {
        if (playerId == null) return false;
        for (Scene s : activeScenes) {
            if (s.playerId.equals(playerId) && (ticks - s.startTick) < s.ascendOffsetTicks()) {
                return true;
            }
        }
        return false;
    }

    public boolean enabled() {
        var config = MainConfig.getInstance().getConfig();
        return config.getBoolean("animations.rare-catch.enabled", true)
                && config.getBoolean("animations.rare-catch.cinematic.enabled", true);
    }

    public boolean eligible(Fish fish) {
        if (fish == null || fish.getRarity() == null) return false;
        var config = MainConfig.getInstance().getConfig();
        var rarity = fish.getRarity();
        if (config.getBoolean("animations.rare-catch.cinematic.all-rarities", true)) {
            return true;
        }
        List<String> excluded = config.getStringList("animations.rare-catch.cinematic.excluded-rarities");
        for (String ex : excluded) {
            if (ex != null && !ex.equalsIgnoreCase("Common") && ex.equalsIgnoreCase(rarity.getId())) {
                return false;
            }
        }
        List<String> included = config.getStringList("animations.rare-catch.cinematic.rarities");
        if (included.isEmpty()) {
            included = List.of("Common", "Rare", "Epic", "Legendary", "Mythic", "Mythical", "Anomaly");
        }
        if ("Common".equalsIgnoreCase(rarity.getId())) {
            return true;
        }
        return RareCatchPolicy.eligible(rarity.getId(), rarity.getIndex(), rarity.getWeight(), included, excluded);
    }

    public boolean play(Player player, Location water, Fish fish, ItemStack caughtItem) {
        boolean doubled = caughtItem != null && caughtItem.getAmount() > 1;
        return play(player, water, fish, caughtItem, 1, 0, doubled);
    }

    public boolean play(
            Player player,
            Location water,
            Fish fish,
            ItemStack caughtItem,
            int comboStreak,
            int comboBonusPercent,
            boolean doubleCatch
    ) {
        if (!enabled() || !eligible(fish) || caughtItem == null || caughtItem.isEmpty()
                || water == null || water.getWorld() == null || !player.isOnline()
                || !player.getWorld().equals(water.getWorld())) {
            return false;
        }
        var module = plugin.getSimulatorModule();
        if (module != null) {
            if (!module.ownEffectsEnabled(player.getUniqueId())) return false;
            UiProfile profile = module.ownHudProfile(player.getUniqueId());
            if (profile == UiProfile.OFF || profile == UiProfile.REDUCED_MOTION) return false;
        }
        var config = MainConfig.getInstance().getConfig();
        int limit = Math.max(8, Math.min(48, config.getInt("animations.rare-catch.cinematic.max-concurrent", 24)));
        int cooldown = Math.min(2, Math.max(0, config.getInt("animations.rare-catch.cinematic.cooldown-ticks", 2)));
        double anchorYOffset = config.getDouble("animations.rare-catch.cinematic.anchor-y-offset", 1.35);
        boolean wheelEnabled = config.getBoolean("animations.rare-catch.cinematic.material-wheel", true);

        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        Long previous = lastPlayed.get(id);
        if (activeScenes.size() >= limit
                || (previous != null && now - previous < cooldown * 50_000_000L)
                || water.distanceSquared(player.getLocation()) > 48 * 48
                || !water.getWorld().isChunkLoaded(water.getBlockX() >> 4, water.getBlockZ() >> 4)) {
            return false;
        }

        // Fast-forward any older rolling scene of this player into its Byebye skyward ascend so scenes never collide
        fastForwardPlayerScenesToAscend(id);

        Rarity rarity = fish.getRarity();
        CatchRollTimeline.TierStyle tierStyle = CatchRollTimeline.TierStyle.forRarity(
                rarity.getId(), rarity.getIndex(), rarity.getWeight()
        );
        CatchRollTimeline.Durations durations = CatchRollTimeline.Durations.forTier(tierStyle);
        boolean useMaterialWheel = wheelEnabled && tierStyle.isLegendaryOrAbove();
        int wheelTicks = useMaterialWheel ? CatchRollTimeline.WHEEL_DURATION_TICKS : 0;

        Location anchor = resolveAboveWaterAnchor(water, anchorYOffset);
        Location viewerStart = player.getLocation();
        Vector rightAxis = computeRightAxis(anchor, viewerStart);

        ItemStack caughtPreview = sanitizeDisplayItem(caughtItem);
        int caughtAmount = Math.max(1, caughtItem.getAmount());
        boolean firstCatch = isFirstTimeCatch(player, fish);

        // The reward is already selected and delivered: show that same fish throughout the scene.
        // Candidate previews are an explicit cosmetic opt-in, never another catch selection.
        List<DecoyEntry> decoys = config.getBoolean("animations.rare-catch.cinematic.candidate-preview", true)
                ? selectDecoysForRoll(fish, durations.rouletteSteps(), now)
                : List.of(createDecoyEntry(fish, caughtPreview));
        DecoyEntry initialDecoy = decoys.isEmpty()
                ? new DecoyEntry(sanitizeDisplayItem(new ItemStack(Material.COD)),
                        new Component[]{EMFSingleMessage.fromString("<gray><bold>[COMMON]</bold></gray>\n<white>???</white>\n" + ROLL_PROGRESS_BARS[0]).getComponentMessage()})
                : decoys.get(0);
        ItemStack initialItem = firstCatch ? buildFirstCatchChestItem(tierStyle) : initialDecoy.item();

        String oddsStr = computeOneInOdds(fish);
        String coinsStr = resolveCoinsDisplay(fish, tierStyle, comboBonusPercent, caughtAmount);
        Component resultText = buildResultText(fish, tierStyle, oddsStr, coinsStr, comboStreak, comboBonusPercent, doubleCatch, caughtAmount, false);
        Component byebyeText = buildResultText(fish, tierStyle, oddsStr, coinsStr, comboStreak, comboBonusPercent, doubleCatch, caughtAmount, true);
        Component resultActionBar = buildResultActionBar(fish, tierStyle, oddsStr, coinsStr, comboStreak, comboBonusPercent, doubleCatch, caughtAmount);
        Component farewellActionBar = EMFSingleMessage.fromString(
                "<green>✔ Câu cá hoàn tất!</green>"
                        + (comboStreak >= 2 ? " <dark_gray>•</dark_gray> <gold>🔥 Combo x" + comboStreak + " (+" + comboBonusPercent + "%)</gold>" : "")
                        + " <dark_gray>•</dark_gray> <aqua>Byebye! 👋</aqua>"
        ).getComponentMessage();
        Title showcaseTitle = buildShowcaseTitle(fish, tierStyle, oddsStr, doubleCatch, caughtAmount, comboStreak);
        int resultExtraLines = doubleCatch ? 4 : 3;

        ItemDisplay itemDisplay = null;
        TextDisplay textDisplay = null;
        List<ItemDisplay> wheelDisplays = new ArrayList<>(useMaterialWheel ? CatchRollTimeline.WHEEL_SLOT_COUNT : 0);
        try {
            World world = anchor.getWorld();
            CatchRollTimeline.Frame initialFrame = CatchRollTimeline.sample(durations, 0);

            itemDisplay = world.spawn(anchor, ItemDisplay.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setGlowing(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                entity.setItemStack(initialItem);
                entity.setCustomNameVisible(false);
                entity.setShadowRadius(0.0f);
                entity.setShadowStrength(0.0f);
                entity.setViewRange(32.0f);
                int light = useMaterialWheel ? 0 : 15;
                entity.setBrightness(new Display.Brightness(light, light));
                entity.setInterpolationDuration(FRAME_TICKS);
                entity.setInterpolationDelay(0);
                float scale = useMaterialWheel ? 0.01f : (float) (ITEM_BASE_SCALE * Math.max(0.65, initialFrame.scaleMultiplier()));
                entity.setTransformation(new Transformation(
                        new Vector3f((float) (rightAxis.getX() * initialFrame.offsetX()), ITEM_VERTICAL_OFFSET, (float) (rightAxis.getZ() * initialFrame.offsetX())),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                ));
            });

            Component initialText = useMaterialWheel
                    ? buildWheelHeaderText(0, false)
                    : (firstCatch ? buildFirstCatchChestText(tierStyle, 0.0) : initialDecoy.rollingText(0.0));

            textDisplay = world.spawn(anchor, TextDisplay.class, entity -> {
                entity.setVisibleByDefault(false);
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setGlowing(false);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                entity.setLineWidth(240);
                entity.setShadowed(true);
                entity.setSeeThrough(false);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setTextOpacity((byte) -1);
                entity.text(initialText);
                entity.setShadowRadius(0.0f);
                entity.setShadowStrength(0.0f);
                entity.setViewRange(32.0f);
                entity.setBrightness(new Display.Brightness(15, 15));
                entity.setInterpolationDuration(FRAME_TICKS);
                entity.setInterpolationDelay(0);
                float scale = TEXT_BASE_SCALE;
                float textY = TEXT_VERTICAL_OFFSET + (useMaterialWheel ? 0.36f : 0.0f);
                entity.setTransformation(new Transformation(
                        new Vector3f(0.0f, textY, 0.0f),
                        new Quaternionf(),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                ));
            });

            if (useMaterialWheel) {
                CatchRollTimeline.WheelFrame wheelFrame0 = CatchRollTimeline.sampleWheel(tierStyle, 0, wheelTicks, ITEM_BASE_SCALE * 0.72f);
                for (int i = 0; i < CatchRollTimeline.WHEEL_SLOT_COUNT; i++) {
                    final int slotIdx = i;
                    final CatchRollTimeline.WheelSlotPose pose = wheelFrame0.slots()[i];
                    ItemDisplay wheelItem = world.spawn(anchor, ItemDisplay.class, entity -> {
                        entity.setVisibleByDefault(false);
                        entity.setPersistent(false);
                        entity.setGravity(false);
                        entity.setGlowing(false);
                        entity.setInvulnerable(true);
                        entity.setSilent(true);
                        entity.setBillboard(Display.Billboard.CENTER);
                        entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        entity.setItemStack(sanitizeDisplayItem(new ItemStack(WHEEL_MATERIALS[slotIdx])));
                        entity.setCustomNameVisible(false);
                        entity.setShadowRadius(0.0f);
                        entity.setShadowStrength(0.0f);
                        entity.setViewRange(32.0f);
                        entity.setBrightness(new Display.Brightness(pose.brightness(), pose.brightness()));
                        entity.setInterpolationDuration(FRAME_TICKS);
                        entity.setInterpolationDelay(0);
                        float wx = (float) (rightAxis.getX() * pose.offsetRight());
                        float wz = (float) (rightAxis.getZ() * pose.offsetRight());
                        float wy = (float) pose.offsetY();
                        entity.setTransformation(new Transformation(
                                new Vector3f(wx, wy, wz),
                                new Quaternionf(),
                                new Vector3f(pose.scale(), pose.scale(), pose.scale()),
                                new Quaternionf()
                        ));
                    });
                    player.showEntity(plugin, wheelItem);
                    wheelDisplays.add(wheelItem);
                }
            }

            player.showEntity(plugin, itemDisplay);
            player.showEntity(plugin, textDisplay);

            List<Runnable> callbacks = pendingRevealCallbacks.remove(id);
            List<Runnable> sceneCallbacks = callbacks != null ? new ArrayList<>(callbacks) : new ArrayList<>();

            Scene scene = new Scene(
                    id,
                    itemDisplay,
                    textDisplay,
                    wheelDisplays,
                    wheelTicks,
                    anchor,
                    viewerStart,
                    rightAxis,
                    tierStyle,
                    durations,
                    caughtPreview,
                    resultText,
                    byebyeText,
                    resultActionBar,
                    farewellActionBar,
                    showcaseTitle,
                    resultExtraLines,
                    doubleCatch,
                    caughtAmount,
                    comboStreak,
                    decoys,
                    sceneCallbacks,
                    ticks
            );
            scene.firstCatch = firstCatch;
            activeScenes.add(scene);
            ensureTicker();
            lastPlayed.put(id, now);
            return true;
        } catch (RuntimeException | LinkageError error) {
            flushPendingCallbacks(id);
            if (itemDisplay != null) {
                try { itemDisplay.remove(); } catch (RuntimeException | LinkageError ignored) { }
            }
            if (textDisplay != null) {
                try { textDisplay.remove(); } catch (RuntimeException | LinkageError ignored) { }
            }
            for (ItemDisplay wd : wheelDisplays) {
                try { wd.remove(); } catch (RuntimeException | LinkageError ignored) { }
            }
            warn(error);
            return false;
        }
    }

    private void fastForwardPlayerScenesToAscend(UUID playerId) {
        List<Scene> playerScenes = new ArrayList<>();
        for (Scene s : activeScenes) {
            if (s.playerId.equals(playerId)) {
                playerScenes.add(s);
            }
        }
        while (playerScenes.size() >= MAX_SCENES_PER_PLAYER) {
            Scene oldest = playerScenes.remove(0);
            activeScenes.remove(oldest);
            destroyScene(oldest);
        }
        for (Scene s : playerScenes) {
            int ascendOffset = s.ascendOffsetTicks();
            long elapsed = ticks - s.startTick;
            if (elapsed < ascendOffset) {
                if (!s.wheelCleanedUp) {
                    s.wheelCleanedUp = true;
                    cleanupWheelDisplays(s);
                }
                s.revealed = true;
                s.byebyeTextApplied = true;
                s.fireRevealCallbacks();
                try {
                    s.itemDisplay.setItemStack(s.caughtPreview);
                    s.textDisplay.text(s.byebyeText);
                } catch (RuntimeException | LinkageError ignored) {
                }
                s.startTick = ticks - ascendOffset;
            }
        }
    }

    private static Location resolveAboveWaterAnchor(Location water, double configuredOffset) {
        Location clone = water.clone();
        World world = clone.getWorld();
        double surfaceY = clone.getY();
        if (world != null) {
            int bx = clone.getBlockX();
            int by = clone.getBlockY();
            int bz = clone.getBlockZ();
            for (int dy = 2; dy >= -1; dy--) {
                Material type = world.getBlockAt(bx, by + dy, bz).getType();
                if (type == Material.WATER || type == Material.KELP || type == Material.KELP_PLANT
                        || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
                        || type == Material.BUBBLE_COLUMN) {
                    surfaceY = Math.max(surfaceY, (by + dy) + 1.0D);
                    break;
                }
            }
        }
        double minHeightAboveSurface = Math.max(1.25D, configuredOffset);
        clone.setY(Math.max(clone.getY() + minHeightAboveSurface, surfaceY + 1.35D));
        return clone;
    }

    private void ensureTicker() {
        if (task == null) {
            task = EvenMoreFish.getScheduler().runTaskTimer(plugin, this::tick, FRAME_TICKS, FRAME_TICKS);
        }
    }

    private void tick() {
        ticks += FRAME_TICKS;
        int frameParticles = 0;
        for (Scene scene : List.copyOf(activeScenes)) {
            UUID playerId = scene.playerId;
            Player viewer = Bukkit.getPlayer(playerId);
            var module = plugin.getSimulatorModule();
            long elapsed = ticks - scene.startTick;

            if (viewer == null || !viewer.isOnline()
                    || !scene.itemDisplay.isValid() || !scene.textDisplay.isValid()
                    || !viewer.getWorld().equals(scene.anchor.getWorld())
                    || viewer.getLocation().distanceSquared(scene.anchor) > 48 * 48
                    || !enabled()
                    || (module != null && (!module.ownEffectsEnabled(playerId)
                            || module.ownHudProfile(playerId) == UiProfile.OFF
                            || module.ownHudProfile(playerId) == UiProfile.REDUCED_MOTION))
                    || elapsed >= scene.totalTicks()) {
                activeScenes.remove(scene);
                destroyScene(scene);
                continue;
            }

            try {
                boolean soundAllowed = module == null || module.ownHudProfile(playerId).sound();
                boolean hudAllowed = module == null || module.ownHudProfile(playerId).hud();
                boolean particleAllowed = module == null || module.ownHudProfile(playerId).particle();

                // Stage 1: Legendary Material Wheel (if active for Legendary/Mythical)
                if (elapsed < scene.wheelTicks) {
                    frameParticles += tickWheelStage(
                            viewer,
                            scene,
                            (int) elapsed,
                            soundAllowed,
                            hudAllowed,
                            particleAllowed ? (PARTICLES_PER_FRAME - frameParticles) : 0
                    );
                    continue;
                }

                // Clean up Stage 1 wheel displays right as Stage 2 begins
                if (!scene.wheelCleanedUp) {
                    scene.wheelCleanedUp = true;
                    cleanupWheelDisplays(scene);
                }

                long rollElapsed = elapsed - scene.wheelTicks;
                CatchRollTimeline.Frame frame = CatchRollTimeline.sample(scene.durations, rollElapsed);
                boolean phaseEntered = scene.enteredPhases.add(frame.phase());

                // 1. Update displayed item & text during ROLLING vs REVEAL+ vs BYEBYE_ASCEND
                if (!frame.revealResult()) {
                    if (scene.firstCatch) {
                        if (frame.rouletteStep() != scene.lastRouletteStep || phaseEntered) {
                            scene.lastRouletteStep = frame.rouletteStep();
                            scene.itemDisplay.setItemStack(buildFirstCatchChestItem(scene.tierStyle));
                            scene.textDisplay.text(buildFirstCatchChestText(scene.tierStyle, frame.progress()));
                            if (soundAllowed && frame.phase() == CatchRollTimeline.Phase.ROLLING
                                    && !CatchRollTimeline.isInRevealSilenceWindow(scene.durations, scene.tierStyle, rollElapsed)) {
                                float rattlePitch = (float) (0.85 + 0.65 * frame.progress());
                                viewer.playSound(scene.anchor, Sound.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 0.55f, rattlePitch);
                                viewer.playSound(scene.anchor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.45f, rattlePitch);
                            }
                        }
                    } else if (!scene.decoys.isEmpty() && (frame.rouletteStep() != scene.lastRouletteStep || phaseEntered)) {
                        scene.lastRouletteStep = frame.rouletteStep();
                        DecoyEntry decoy = scene.decoys.get(Math.floorMod(frame.rouletteStep(), scene.decoys.size()));
                        scene.itemDisplay.setItemStack(decoy.item());
                        scene.textDisplay.text(decoy.rollingText(frame.progress()));
                        if (soundAllowed && frame.phase() == CatchRollTimeline.Phase.ROLLING
                                && !CatchRollTimeline.isInRevealSilenceWindow(scene.durations, scene.tierStyle, rollElapsed)) {
                            playRollingStepSound(viewer, scene, frame);
                        }
                    }
                    // Warden Heartbeat in the final window of ROLLING for Epic+
                    if (soundAllowed && frame.phase() == CatchRollTimeline.Phase.ROLLING
                            && CatchRollTimeline.isHeartbeatTick(scene.durations, scene.tierStyle, rollElapsed)) {
                        float hbPitch = (float) (0.72 + 0.36 * frame.progress());
                        viewer.playSound(scene.anchor, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.85f, hbPitch);
                    }
                } else if (!scene.revealed) {
                    scene.revealed = true;
                    scene.itemDisplay.setItemStack(scene.caughtPreview);
                    scene.textDisplay.text(scene.resultText);
                    scene.fireRevealCallbacks();
                    if (scene.firstCatch && soundAllowed) {
                        viewer.playSound(scene.anchor, scene.tierStyle.isEpicOrAbove() ? Sound.BLOCK_ENDER_CHEST_OPEN : Sound.BLOCK_CHEST_OPEN, SoundCategory.PLAYERS, 0.90f, 1.25f);
                        viewer.playSound(scene.anchor, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.75f, 1.35f);
                    }
                    if (scene.doubleCatch && soundAllowed) {
                        viewer.playSound(scene.anchor, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.90f, scene.caughtAmount >= 3 ? 1.75f : 1.55f);
                        viewer.playSound(scene.anchor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.95f, 1.65f);
                        if (scene.caughtAmount >= 3) {
                            viewer.playSound(scene.anchor, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.85f, 1.35f);
                        }
                    }
                } else if (frame.phase() == CatchRollTimeline.Phase.BYEBYE_ASCEND) {
                    if (frame.opacity() < 0.08) {
                        if (!scene.textClearedForFade) {
                            scene.textClearedForFade = true;
                            scene.textDisplay.text(Component.empty());
                        }
                    } else if (!scene.byebyeTextApplied && frame.progress() >= 0.12) {
                        scene.byebyeTextApplied = true;
                        scene.textDisplay.text(scene.byebyeText);
                    }
                }

                // 2. Apply interpolated Transformations & Opacity/Brightness (skip redundant updates during HOLD)
                boolean shouldUpdatePose = frame.phase() != CatchRollTimeline.Phase.HOLD || !scene.holdPoseApplied;
                float tx = (float) (scene.rightAxis.getX() * frame.offsetX());
                float tz = (float) (scene.rightAxis.getZ() * frame.offsetX());
                float ty = (float) frame.offsetY();

                if (shouldUpdatePose) {
                    if (frame.phase() == CatchRollTimeline.Phase.HOLD) {
                        scene.holdPoseApplied = true;
                    }
                    float scaleFactor = frame.phase() == CatchRollTimeline.Phase.SLIDE_IN
                            ? (float) Math.max(0.65, frame.scaleMultiplier())
                            : (float) frame.scaleMultiplier();
                    float itemScale = ITEM_BASE_SCALE * scaleFactor;
                    float chestExtraY = 0.0f;
                    Quaternionf itemRot;
                    if (scene.firstCatch && !frame.revealResult()) {
                        double shakeIntensity = 0.14 + 0.32 * frame.progress();
                        float shakeZ = (float) (Math.sin(rollElapsed * 1.65) * shakeIntensity);
                        float shakeY = (float) (Math.cos(rollElapsed * 1.15) * shakeIntensity * 0.85);
                        chestExtraY = (float) (-0.28 * (1.0 - frame.progress()) + Math.abs(Math.sin(rollElapsed * 1.35)) * 0.12);
                        itemScale *= (float) (1.08 + 0.14 * Math.abs(Math.sin(rollElapsed * 1.35)));
                        itemRot = new Quaternionf()
                                .rotateY((float) frame.yawRadians() + shakeY)
                                .rotateZ((float) frame.rollRadians() + shakeZ);
                    } else {
                        itemRot = new Quaternionf()
                                .rotateY((float) frame.yawRadians())
                                .rotateZ((float) frame.rollRadians());
                    }
                    int light = frame.phase() == CatchRollTimeline.Phase.BYEBYE_ASCEND
                            ? CatchRollTimeline.toDisplayBrightness(frame.opacity())
                            : 15;
                    scene.itemDisplay.setBrightness(new Display.Brightness(light, light));
                    scene.itemDisplay.setInterpolationDelay(0);
                    scene.itemDisplay.setTransformation(new Transformation(
                            new Vector3f(tx, ITEM_VERTICAL_OFFSET + ty + chestExtraY, tz),
                            itemRot,
                            new Vector3f(itemScale, itemScale, itemScale),
                            new Quaternionf()
                    ));

                    float textScale = (float) (TEXT_BASE_SCALE * Math.max(0.75, frame.scaleMultiplier()));
                    float textY = TEXT_VERTICAL_OFFSET + ty;
                    Quaternionf textRot = frame.phase() == CatchRollTimeline.Phase.BYEBYE_ASCEND
                            ? new Quaternionf().rotateZ((float) (frame.rollRadians() * 0.45))
                            : new Quaternionf();
                    byte textOpacity = frame.phase() == CatchRollTimeline.Phase.BYEBYE_ASCEND
                            ? CatchRollTimeline.toTextOpacityByte(frame.opacity())
                            : (byte) -1;
                    scene.textDisplay.setTextOpacity(textOpacity);
                    scene.textDisplay.setInterpolationDelay(0);
                    scene.textDisplay.setTransformation(new Transformation(
                            new Vector3f(tx, textY, tz),
                            textRot,
                            new Vector3f(textScale, textScale, textScale),
                            new Quaternionf()
                    ));
                }

                // 3. HUD Director (ActionBar & Epic+ Showcase Title)
                if (hudAllowed) {
                    updateHud(viewer, scene, frame, phaseEntered, elapsed);
                }

                // 4. Phase transition sounds & particles
                Location effectLoc = scene.anchor.clone().add(tx, ty + 0.15, tz);
                if (soundAllowed && phaseEntered) {
                    playPhaseTransitionSound(viewer, scene, frame.phase(), effectLoc);
                }
                if (particleAllowed && frameParticles < PARTICLES_PER_FRAME) {
                    frameParticles += emitFrameParticles(
                            viewer,
                            scene,
                            frame,
                            effectLoc,
                            phaseEntered,
                            PARTICLES_PER_FRAME - frameParticles
                    );
                }
            } catch (RuntimeException | LinkageError error) {
                activeScenes.remove(scene);
                destroyScene(scene);
                warn(error);
            }
        }
        if (activeScenes.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private int tickWheelStage(
            Player viewer,
            Scene scene,
            int wheelTick,
            boolean soundAllowed,
            boolean hudAllowed,
            int remainingParticleBudget
    ) {
        CatchRollTimeline.WheelFrame wf = CatchRollTimeline.sampleWheel(
                scene.tierStyle,
                wheelTick,
                scene.wheelTicks,
                ITEM_BASE_SCALE * 0.72f
        );

        for (int i = 0; i < scene.wheelDisplays.size() && i < wf.slots().length; i++) {
            ItemDisplay slotDisplay = scene.wheelDisplays.get(i);
            if (!slotDisplay.isValid()) continue;
            CatchRollTimeline.WheelSlotPose pose = wf.slots()[i];
            if (!pose.visible()) {
                slotDisplay.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Quaternionf(),
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new Quaternionf()
                ));
                continue;
            }
            float wx = (float) (scene.rightAxis.getX() * pose.offsetRight());
            float wz = (float) (scene.rightAxis.getZ() * pose.offsetRight());
            float wy = (float) pose.offsetY();
            slotDisplay.setBrightness(new Display.Brightness(pose.brightness(), pose.brightness()));
            slotDisplay.setInterpolationDelay(0);
            slotDisplay.setTransformation(new Transformation(
                    new Vector3f(wx, wy, wz),
                    new Quaternionf(),
                    new Vector3f(pose.scale(), pose.scale(), pose.scale()),
                    new Quaternionf()
            ));
        }

        // Update wheel header text & sounds when highlighted material advances
        if (wf.highlightedIndex() != scene.lastWheelHighlight) {
            scene.lastWheelHighlight = wf.highlightedIndex();
            scene.textDisplay.text(buildWheelHeaderText(wf.highlightedIndex(), wf.breakthroughTriggered()));
            if (soundAllowed) {
                float pitch = 0.75f + (wf.highlightedIndex() * 0.16f);
                viewer.playSound(scene.anchor, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.65f, pitch);
                viewer.playSound(scene.anchor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.45f, pitch);
            }
        }

        if (wf.breakthroughTriggered() && !scene.wheelBreakthroughPlayed) {
            scene.wheelBreakthroughPlayed = true;
            scene.textDisplay.text(buildWheelHeaderText(wf.highlightedIndex(), true));
            if (soundAllowed) {
                viewer.playSound(scene.anchor, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.85f, 1.35f);
                viewer.playSound(scene.anchor, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 0.65f);
            }
        }

        if (wf.converging() && !scene.wheelConvergeSoundPlayed) {
            scene.wheelConvergeSoundPlayed = true;
            if (soundAllowed) {
                viewer.playSound(scene.anchor, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.75f, 1.45f);
                viewer.playSound(scene.anchor, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.55f, 1.50f);
            }
        }

        if (hudAllowed && wheelTick % 3 == 0) {
            int percent = (int) Math.round(wf.progress() * 40.0);
            String dots = ".".repeat((wheelTick / 3) % 3 + 1);
            viewer.sendActionBar(EMFSingleMessage.fromString(
                    "<dark_gray>○ <gold>Vòng quay Huyền Thoại</gold> <yellow>" + dots + "</yellow> <dark_gray>[<yellow>" + percent + "%</yellow>]</dark_gray>"
            ).getComponentMessage());
        }

        if (remainingParticleBudget > 0 && PARTICLE_ENCHANT != null) {
            int count = CinematicParticleEffects.reserve(Math.min(3, remainingParticleBudget));
            if (count > 0) {
                viewer.spawnParticle(PARTICLE_ENCHANT, scene.anchor, count, 0.45, 0.45, 0.45, 0.08);
                return count;
            }
        }
        return 0;
    }

    private static Component buildWheelHeaderText(int slotIndex, boolean breakthrough) {
        int clamped = Math.max(0, Math.min(WHEEL_MATERIAL_NAMES.length - 1, slotIndex));
        String badge = breakthrough
                ? "<gradient:#FF4FD8:#8C52FF><bold>[⚡ ĐỘT PHÁ THẦN THOẠI ⚡]</bold></gradient>"
                : "<gold><bold>[VÒNG QUAY BÁU VẬT]</bold></gold>";
        String footer = breakthrough
                ? "<aqua>✦ Vượt ngưỡng giới hạn! ✦</aqua>"
                : "<gray>Đang hội tụ linh khí...</gray>";
        return EMFSingleMessage.fromString(
                badge + "\n" + WHEEL_MATERIAL_NAMES[clamped] + "\n" + footer
        ).getComponentMessage();
    }

    private static void cleanupWheelDisplays(Scene scene) {
        for (ItemDisplay wd : scene.wheelDisplays) {
            try {
                if (wd.isValid()) {
                    wd.remove();
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        scene.wheelDisplays.clear();
    }

    private static void updateHud(
            Player viewer,
            Scene scene,
            CatchRollTimeline.Frame frame,
            boolean phaseEntered,
            long elapsed
    ) {
        switch (frame.phase()) {
            case SLIDE_IN, ROLLING -> {
                if (elapsed % 3 == 0) {
                    double baseRatio = scene.wheelTicks > 0 ? 0.40 : 0.0;
                    double span = scene.wheelTicks > 0 ? 0.60 : 1.0;
                    int percent = Math.min(99, (int) Math.round((baseRatio + frame.progress() * span) * 100.0));
                    String dots = ".".repeat((int) ((elapsed / 3) % 3) + 1);
                    viewer.sendActionBar(EMFSingleMessage.fromString(
                            "<dark_gray>○ <white>Đang kéo cá</white> <yellow>" + dots + "</yellow> <dark_gray>[<yellow>" + percent + "%</yellow>]</dark_gray>"
                    ).getComponentMessage());
                }
            }
            case REVEAL -> {
                if (phaseEntered) {
                    viewer.sendActionBar(scene.resultActionBar);
                }
            }
            case SHOWCASE -> {
                if (phaseEntered) {
                    viewer.sendActionBar(scene.resultActionBar);
                    if (scene.showcaseTitle != null
                            && MainConfig.getInstance().getConfig().getBoolean("animations.rare-catch.cinematic.hud-titles", true)) {
                        viewer.showTitle(scene.showcaseTitle);
                    }
                }
            }
            case BYEBYE_ASCEND -> {
                if (phaseEntered) {
                    viewer.sendActionBar(scene.farewellActionBar);
                }
            }
            default -> {
            }
        }
    }

    private static void playRollingStepSound(Player viewer, Scene scene, CatchRollTimeline.Frame frame) {
        float pitch = (float) (0.80 + 0.85 * frame.progress());
        viewer.playSound(scene.anchor, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.55f, pitch);
        viewer.playSound(scene.anchor, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.38f, pitch);
    }

    private static void playPhaseTransitionSound(
            Player viewer,
            Scene scene,
            CatchRollTimeline.Phase phase,
            Location effectLoc
    ) {
        switch (phase) {
            case SLIDE_IN -> viewer.playSound(scene.anchor, Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.PLAYERS, 0.45f, 1.25f);
            case REVEAL -> {
                switch (scene.tierStyle) {
                    case MYTHICAL -> {
                        viewer.playSound(effectLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.90f, 1.15f);
                        viewer.playSound(effectLoc, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.55f, 1.25f);
                        viewer.playSound(effectLoc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.90f, 1.30f);
                    }
                    case LEGENDARY -> {
                        viewer.playSound(effectLoc, Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.70f, 1.20f);
                        viewer.playSound(effectLoc, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.85f, 1.35f);
                        viewer.playSound(effectLoc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.80f, 1.25f);
                    }
                    case EPIC -> {
                        viewer.playSound(effectLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.75f, 1.25f);
                        viewer.playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.65f, 1.35f);
                    }
                    case STANDARD -> {
                        viewer.playSound(effectLoc, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.65f, 1.40f);
                        viewer.playSound(effectLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.55f, 1.35f);
                    }
                }
            }
            case SHOWCASE -> {
                switch (scene.tierStyle) {
                    case MYTHICAL -> {
                        viewer.playSound(effectLoc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.75f, 1.35f);
                        viewer.playSound(effectLoc, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.70f, 1.40f);
                    }
                    case LEGENDARY -> {
                        viewer.playSound(effectLoc, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.65f, 1.30f);
                        viewer.playSound(effectLoc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, SoundCategory.PLAYERS, 0.60f, 1.50f);
                    }
                    case EPIC -> viewer.playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.65f, 1.35f);
                    default -> {
                    }
                }
            }
            case BYEBYE_ASCEND -> {
                // Cheerful ascending farewell chime when waving goodbye
                viewer.playSound(effectLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.65f, 1.55f);
                viewer.playSound(effectLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.55f, 1.65f);
            }
            default -> {
            }
        }
    }

    private static int emitFrameParticles(
            Player viewer,
            Scene scene,
            CatchRollTimeline.Frame frame,
            Location effectLoc,
            boolean phaseEntered,
            int remainingBudget
    ) {
        if (remainingBudget <= 0) return 0;
        int requested = switch (frame.phase()) {
            case SLIDE_IN -> 1;
            case ROLLING -> scene.tierStyle.isEpicOrAbove() || scene.firstCatch ? 2 : 1;
            case REVEAL -> phaseEntered ? (scene.tierStyle.isEpicOrAbove() || scene.firstCatch ? 6 : 3) : 1;
            case SHOWCASE, HOLD -> scene.tierStyle.isLegendaryOrAbove() ? 1 : 0;
            case BYEBYE_ASCEND -> 1;
            default -> 0;
        };
        if (requested <= 0) return 0;
        int budget = CinematicParticleEffects.reserve(Math.min(requested, remainingBudget));
        if (budget <= 0) return 0;

        switch (frame.phase()) {
            case SLIDE_IN -> {
                if (PARTICLE_SPLASH != null) {
                    viewer.spawnParticle(PARTICLE_SPLASH, scene.anchor, budget, 0.18, 0.10, 0.18, 0.04);
                }
            }
            case ROLLING -> {
                if (scene.tierStyle.isEpicOrAbove() || scene.firstCatch) {
                    // LeDatRNG-style converging spiral soul-pull toward the rolling item
                    double pullRadius = 0.72 * (1.0 - 0.65 * frame.progress());
                    double baseAngle = frame.progress() * Math.PI * 6.0;
                    Particle pullParticle = scene.tierStyle == CatchRollTimeline.TierStyle.MYTHICAL && PARTICLE_SOUL_FIRE != null
                            ? PARTICLE_SOUL_FIRE
                            : (PARTICLE_PORTAL != null ? PARTICLE_PORTAL : PARTICLE_END_ROD);
                    if (pullParticle != null) {
                        int points = Math.min(budget, 2);
                        for (int i = 0; i < points; i++) {
                            double angle = baseAngle + (i * 2.0 * Math.PI / points);
                            double px = effectLoc.getX() + Math.cos(angle) * pullRadius;
                            double py = effectLoc.getY() + Math.sin(frame.progress() * Math.PI * 2.0 + i) * 0.18;
                            double pz = effectLoc.getZ() + Math.sin(angle) * pullRadius;
                            viewer.spawnParticle(pullParticle, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }
                } else if (PARTICLE_END_ROD != null) {
                    viewer.spawnParticle(PARTICLE_END_ROD, effectLoc, 1, 0.14, 0.14, 0.14, 0.01);
                }
            }
            case REVEAL -> {
                if (phaseEntered && (scene.tierStyle.isEpicOrAbove() || scene.doubleCatch || scene.firstCatch) && PARTICLE_END_ROD != null) {
                    // Horizontal shockwave ring like LeDatRNG
                    int ringPoints = Math.min(budget, scene.tierStyle.isLegendaryOrAbove() || scene.doubleCatch ? 6 : 4);
                    double radius = scene.tierStyle.isLegendaryOrAbove() || scene.doubleCatch ? 0.68 : 0.48;
                    for (int i = 0; i < ringPoints; i++) {
                        double angle = (2.0 * Math.PI * i) / ringPoints;
                        double px = effectLoc.getX() + Math.cos(angle) * radius;
                        double pz = effectLoc.getZ() + Math.sin(angle) * radius;
                        viewer.spawnParticle(PARTICLE_END_ROD, px, effectLoc.getY(), pz, 1, 0.0, 0.0, 0.0, 0.02);
                    }
                    if (PARTICLE_TOTEM != null && (scene.tierStyle.isLegendaryOrAbove() || scene.doubleCatch || scene.firstCatch)) {
                        viewer.spawnParticle(PARTICLE_TOTEM, effectLoc, 2, 0.22, 0.22, 0.22, 0.06);
                    }
                } else if (PARTICLE_END_ROD != null) {
                    viewer.spawnParticle(PARTICLE_END_ROD, effectLoc, budget, 0.18, 0.18, 0.18, 0.02);
                }
            }
            case SHOWCASE, HOLD -> {
                if (PARTICLE_END_ROD != null && (scene.tierStyle.isEpicOrAbove() || scene.doubleCatch)) {
                    // Tilted 18-degree rotating halo ring (dual counter-rotating rings for Legendary+ or Double Catch)
                    double angle = frame.progress() * Math.PI * 2.0;
                    double radius = 0.52;
                    double lx = Math.cos(angle) * radius;
                    double lz = Math.sin(angle) * radius;
                    double rx = lx * Math.cos(HALO_TILT_RADIANS);
                    double ry = -lx * Math.sin(HALO_TILT_RADIANS);
                    viewer.spawnParticle(PARTICLE_END_ROD, effectLoc.getX() + rx, effectLoc.getY() + ry, effectLoc.getZ() + lz, 1, 0.0, 0.0, 0.0, 0.0);
                    if ((scene.tierStyle.isLegendaryOrAbove() || scene.doubleCatch) && budget >= 2) {
                        double angle2 = -angle + Math.PI;
                        double lx2 = Math.cos(angle2) * (radius * 1.15);
                        double lz2 = Math.sin(angle2) * (radius * 1.15);
                        double rx2 = lx2 * Math.cos(-HALO_TILT_RADIANS);
                        double ry2 = -lx2 * Math.sin(-HALO_TILT_RADIANS);
                        viewer.spawnParticle(PARTICLE_END_ROD, effectLoc.getX() + rx2, effectLoc.getY() + ry2, effectLoc.getZ() + lz2, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            }
            case BYEBYE_ASCEND -> {
                // Upward goodbye sparkles & soft cloud trail
                if (PARTICLE_END_ROD != null) {
                    viewer.spawnParticle(PARTICLE_END_ROD, effectLoc, Math.max(1, budget - 1), 0.14, 0.18, 0.14, 0.02);
                }
                if (PARTICLE_CLOUD != null && budget >= 2) {
                    viewer.spawnParticle(PARTICLE_CLOUD, effectLoc.getX(), effectLoc.getY() - 0.15, effectLoc.getZ(), 1, 0.08, 0.05, 0.08, 0.01);
                }
            }
            default -> {
            }
        }
        return budget;
    }

    /**
     * Selects decoy entries from a lazily cached pool so catching a fish does not
     * allocate ItemStacks or parse MiniMessage strings on every catch.
     */
    private List<DecoyEntry> selectDecoysForRoll(Fish targetFish, int count, long nowNanos) {
        if (cachedDecoys.isEmpty() || nowNanos - lastDecoyCacheTimeNanos > 60_000_000_000L) {
            rebuildDecoyCache(nowNanos);
        }
        List<DecoyEntry> selected = new ArrayList<>(count);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (!cachedDecoys.isEmpty()) {
            for (int i = 0; i < count; i++) {
                selected.add(cachedDecoys.get(random.nextInt(cachedDecoys.size())));
            }
            return selected;
        }
        // Fallback if catalog was empty
        DecoyEntry fallback = createDecoyEntry(targetFish);
        for (int i = 0; i < count; i++) {
            selected.add(fallback);
        }
        return selected;
    }

    private void rebuildDecoyCache(long nowNanos) {
        cachedDecoys.clear();
        lastDecoyCacheTimeNanos = nowNanos;
        try {
            var rarityMap = FishManager.getInstance().getRarityMap();
            if (rarityMap == null) return;
            for (Rarity r : rarityMap.values()) {
                List<Fish> list = r.getOriginalFishList();
                if (list == null || list.isEmpty()) continue;
                int limitPerRarity = Math.min(list.size(), 8);
                for (int i = 0; i < limitPerRarity; i++) {
                    cachedDecoys.add(createDecoyEntry(list.get(i)));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static ItemStack sanitizeDisplayItem(ItemStack source) {
        if (source == null || source.isEmpty()) {
            ItemStack fallback = new ItemStack(Material.COD);
            com.oheers.fish.FishUtils.disableGlintOverride(fallback);
            return fallback;
        }
        ItemStack copy = source.clone();
        copy.setAmount(1);
        try {
            var meta = copy.getItemMeta();
            if (meta != null) {
                for (var enchant : List.copyOf(meta.getEnchants().keySet())) {
                    meta.removeEnchant(enchant);
                }
                com.oheers.fish.FishUtils.disableGlintOverride(meta);
                copy.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
        }
        return copy;
    }

    private static DecoyEntry createDecoyEntry(Fish sample) {
        ItemStack preview;
        try {
            preview = sample.getFactory().createItem();
            if (preview == null || preview.isEmpty()) {
                preview = sanitizeDisplayItem(new ItemStack(Material.COD));
            } else {
                preview = sanitizeDisplayItem(preview);
            }
        } catch (RuntimeException | LinkageError ignored) {
            preview = sanitizeDisplayItem(new ItemStack(Material.COD));
        }
        return createDecoyEntry(sample, preview);
    }

    private static DecoyEntry createDecoyEntry(Fish sample, ItemStack preview) {
        String rarityLabel = formatRarityBadge(sample.getRarity(), sample);
        String fishName = sample.getDisplayName() != null
                ? sample.getDisplayName().getPlainTextMessage()
                : sample.getName();
        String headerPrefix = rarityLabel + "\n<white>✦ " + escapeMiniMessage(fishName) + " ✦</white>\n";
        Component[] progressTexts = new Component[ROLL_PROGRESS_BARS.length];
        for (int i = 0; i < ROLL_PROGRESS_BARS.length; i++) {
            progressTexts[i] = EMFSingleMessage.fromString(headerPrefix + ROLL_PROGRESS_BARS[i]).getComponentMessage();
        }
        return new DecoyEntry(preview, progressTexts);
    }

    private static Component buildResultText(
            Fish fish,
            CatchRollTimeline.TierStyle tierStyle,
            String oddsStr,
            String coinsStr,
            int comboStreak,
            int comboBonusPercent,
            boolean doubleCatch,
            int caughtAmount,
            boolean byebyeFooter
    ) {
        StringBuilder sb = new StringBuilder();
        if (caughtAmount >= 3) {
            sb.append("<gradient:#00F5FF:#FFE600:#FF0055><bold>✪ ĐẠI BẠO KÍCH THỦY SẢN x3! ✪</bold></gradient>\n");
        } else if (doubleCatch) {
            sb.append("<gradient:#FFD700:#FF55FF><bold>✦ NHÂN ĐÔI THỦY SẢN x2! ✦</bold></gradient>\n");
        }
        Rarity rarity = fish.getRarity();
        String line1 = formatRarityBadge(rarity, fish);
        if (comboStreak >= 2) {
            line1 += " <gold>🔥 Combo x" + comboStreak + " (+" + comboBonusPercent + "%)</gold>";
        }
        sb.append(line1).append("\n");

        String rawFishName = fish.getDisplayName() != null
                ? fish.getDisplayName().getPlainTextMessage()
                : fish.getName();
        String line2 = "<white><bold>✦ " + escapeMiniMessage(rawFishName) + " ✦</bold></white>";
        if (caughtAmount > 1 || doubleCatch) {
            line2 += " <gold><bold>x" + Math.max(2, caughtAmount) + "</bold></gold>";
        }
        sb.append(line2).append("\n");

        String line3 = "<aqua>1 in " + oddsStr + "</aqua>";
        if (fish.getLength() > 0) {
            line3 += " <dark_gray>•</dark_gray> <yellow>" + LENGTH_FORMAT.format(fish.getLength()) + "cm</yellow>";
        }
        sb.append(line3).append("\n");

        String line4 = byebyeFooter
                ? "<aqua>✦ Byebye! Hẹn gặp lại! 👋 ✦</aqua>"
                : "<green>+" + coinsStr + " ●</green>";
        sb.append(line4);

        return EMFSingleMessage.fromString(sb.toString()).getComponentMessage();
    }

    private static Component buildResultActionBar(
            Fish fish,
            CatchRollTimeline.TierStyle tierStyle,
            String oddsStr,
            String coinsStr,
            int comboStreak,
            int comboBonusPercent,
            boolean doubleCatch,
            int caughtAmount
    ) {
        String badge = formatRarityBadge(fish.getRarity(), fish);
        String rawFishName = fish.getDisplayName() != null
                ? fish.getDisplayName().getPlainTextMessage()
                : fish.getName();
        String doubleTag = caughtAmount >= 3
                ? "<gradient:#00F5FF:#FFE600:#FF0055><bold>✪ x3! ✪</bold></gradient> "
                : (doubleCatch ? "<gradient:#FFD700:#FF55FF><bold>✦ x2! ✦</bold></gradient> " : "");
        String comboTag = comboStreak >= 2 ? " <dark_gray>•</dark_gray> <gold>🔥 Combo x" + comboStreak + " (+" + comboBonusPercent + "%)</gold>" : "";
        String amountTag = (caughtAmount > 1 || doubleCatch) ? " <gold>x" + Math.max(2, caughtAmount) + "</gold>" : "";
        return EMFSingleMessage.fromString(
                "<aqua>[Câu Cá]</aqua> " + doubleTag + badge + " <white>" + escapeMiniMessage(rawFishName) + "</white>" + amountTag
                        + " <dark_gray>•</dark_gray> <aqua>1/" + oddsStr + "</aqua> <green>+" + coinsStr + " ●</green>" + comboTag
        ).getComponentMessage();
    }

    private static Title buildShowcaseTitle(
            Fish fish,
            CatchRollTimeline.TierStyle tierStyle,
            String oddsStr,
            boolean doubleCatch,
            int caughtAmount,
            int comboStreak
    ) {
        if (!tierStyle.isEpicOrAbove() && !doubleCatch) {
            return null;
        }
        String rawFishName = escapeMiniMessage(
                fish.getDisplayName() != null ? fish.getDisplayName().getPlainTextMessage() : fish.getName()
        );
        String mainTitle = switch (tierStyle) {
            case MYTHICAL -> "<gradient:#FF4FD8:#8C52FF><bold>✪ THẦN THOẠI • MYTHICAL ✪</bold></gradient>";
            case LEGENDARY -> "<gold><bold>★ HUYỀN THOẠI • LEGENDARY ★</bold></gold>";
            case EPIC -> "<light_purple><bold>✦ SỬ THI • EPIC ✦</bold></light_purple>";
            case STANDARD -> caughtAmount >= 3
                    ? "<gradient:#00F5FF:#FFE600:#FF0055><bold>✪ ĐẠI BẠO KÍCH THỦY SẢN x3! ✪</bold></gradient>"
                    : "<gradient:#FFD700:#FF55FF><bold>✦ NHÂN ĐÔI THỦY SẢN x2! ✦</bold></gradient>";
        };
        String comboSuffix = comboStreak >= 2 ? " <dark_gray>•</dark_gray> <gold>🔥 Combo x" + comboStreak + "</gold>" : "";
        String doubleSuffix = doubleCatch && tierStyle.isEpicOrAbove()
                ? " <gold><bold>[x" + Math.max(2, caughtAmount) + "]</bold></gold>"
                : "";
        String subTitle = switch (tierStyle) {
            case MYTHICAL -> "<light_purple>" + rawFishName + "</light_purple>" + doubleSuffix + " <dark_gray>•</dark_gray> <aqua>1/" + oddsStr + "</aqua>" + comboSuffix;
            case LEGENDARY -> "<yellow>" + rawFishName + "</yellow>" + doubleSuffix + " <dark_gray>•</dark_gray> <gold>1/" + oddsStr + "</gold>" + comboSuffix;
            default -> "<white>" + rawFishName + "</white>" + doubleSuffix + " <dark_gray>•</dark_gray> <aqua>1/" + oddsStr + "</aqua>" + comboSuffix;
        };
        return Title.title(
                EMFSingleMessage.fromString(mainTitle).getComponentMessage(),
                EMFSingleMessage.fromString(subTitle).getComponentMessage(),
                Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(1150), Duration.ofMillis(350))
        );
    }

    private static String resolveCoinsDisplay(
            Fish fish,
            CatchRollTimeline.TierStyle tierStyle,
            int comboBonusPercent,
            int caughtAmount
    ) {
        double multiplier = (1.0 + Math.max(0, comboBonusPercent) / 100.0) * Math.max(1, caughtAmount);
        try {
            Optional<Double> worthOpt = WorthNBT.getValue(fish);
            if (worthOpt.isPresent() && worthOpt.get() > 0.0) {
                return WORTH_FORMAT.format(worthOpt.get() * multiplier);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        long fallbackCoins = switch (tierStyle) {
            case MYTHICAL -> 2500L;
            case LEGENDARY -> 1000L;
            case EPIC -> 400L;
            case STANDARD -> {
                String id = fish.getRarity() != null ? fish.getRarity().getId().toLowerCase(Locale.ROOT) : "";
                if (id.contains("rare") || id.contains("hiem")) yield 150L;
                if (id.contains("uncommon")) yield 60L;
                yield 25L;
            }
        };
        return String.format(Locale.US, "%,d", Math.round(fallbackCoins * multiplier));
    }

    public static String computeOneInOdds(Fish fish) {
        if (fish == null || fish.getRarity() == null) {
            return "1";
        }
        try {
            var rarityMap = FishManager.getInstance().getRarityMap();
            if (rarityMap == null || rarityMap.isEmpty()) {
                return "1";
            }
            double totalRarityWeight = 0.0;
            for (Rarity r : rarityMap.values()) {
                if (r.getWeight() > 0.0 && Double.isFinite(r.getWeight())) {
                    totalRarityWeight += r.getWeight();
                }
            }
            Rarity rarity = fish.getRarity();
            double rarityWeight = rarity.getWeight() > 0.0 ? rarity.getWeight() : 1.0;
            double rarityProb = totalRarityWeight > 0.0 ? (rarityWeight / totalRarityWeight) : 1.0;

            List<Fish> fishInRarity = rarity.getOriginalFishList();
            double totalFishWeight = 0.0;
            if (fishInRarity != null && !fishInRarity.isEmpty()) {
                for (Fish f : fishInRarity) {
                    double fw = f.getWeight() > 0.0 ? f.getWeight() : 1.0;
                    totalFishWeight += fw;
                }
            }
            double fishWeight = fish.getWeight() > 0.0 ? fish.getWeight() : 1.0;
            double fishProb = totalFishWeight > 0.0 ? (fishWeight / totalFishWeight) : 1.0;

            double combinedProb = rarityProb * fishProb;
            if (combinedProb <= 0.0 || !Double.isFinite(combinedProb)) {
                return "1";
            }
            long oneIn = Math.max(1L, Math.round(1.0 / combinedProb));
            return String.format(Locale.US, "%,d", oneIn);
        } catch (RuntimeException | LinkageError ignored) {
            return "1";
        }
    }

    private static String formatRarityBadge(Rarity rarity, Fish fish) {
        if (rarity == null) {
            return "<gray><bold>[Common]</bold></gray>";
        }
        String rawDisplay = rarity.getDisplayName() != null
                ? escapeMiniMessage(rarity.getDisplayName().getPlainTextMessage())
                : escapeMiniMessage(rarity.getId());
        if (rawDisplay.isEmpty()) {
            rawDisplay = escapeMiniMessage(rarity.getId());
        }
        CatchRollTimeline.TierStyle style = CatchRollTimeline.TierStyle.forRarity(
                rarity.getId(), rarity.getIndex(), rarity.getWeight()
        );
        return switch (style) {
            case MYTHICAL -> {
                double fw = fish != null ? fish.getWeight() : 1.0;
                if (fw > 0.0 && fw <= 0.15) {
                    yield "<gradient:#FFE600:#FF0055:#00F5FF><bold>[" + rawDisplay + "]</bold></gradient>";
                }
                if (fw > 0.0 && fw <= 0.40) {
                    yield "<gradient:#00F5FF:#FF4FD8:#FFE600><bold>[" + rawDisplay + "]</bold></gradient>";
                }
                yield "<gradient:#FF4FD8:#8C52FF><bold>[" + rawDisplay + "]</bold></gradient>";
            }
            case LEGENDARY -> "<gold><bold>[" + rawDisplay + "]</bold></gold>";
            case EPIC -> "<light_purple><bold>[" + rawDisplay + "]</bold></light_purple>";
            case STANDARD -> {
                String lower = rarity.getId().toLowerCase(Locale.ROOT);
                if (lower.contains("rare") || lower.contains("hiem")) {
                    yield "<aqua><bold>[" + rawDisplay + "]</bold></aqua>";
                }
                if (lower.contains("uncommon") || lower.contains("khong_pho_bien")) {
                    yield "<green><bold>[" + rawDisplay + "]</bold></green>";
                }
                yield "<gray><bold>[" + rawDisplay + "]</bold></gray>";
            }
        };
    }

    private static String escapeMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replace("<", "").replace(">", "");
    }

    private static Vector computeRightAxis(Location anchor, Location viewer) {
        Vector toViewer = viewer.toVector().subtract(anchor.toVector()).setY(0.0);
        if (toViewer.lengthSquared() < 1.0e-4) {
            return new Vector(1.0, 0.0, 0.0);
        }
        toViewer.normalize();
        Vector right = new Vector(-toViewer.getZ(), 0.0, toViewer.getX());
        if (right.lengthSquared() < 1.0e-4) {
            return new Vector(1.0, 0.0, 0.0);
        }
        return right.normalize();
    }

    private static Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private void warn(Throwable error) {
        long now = System.nanoTime();
        if (now - lastWarning > 30_000_000_000L) {
            lastWarning = now;
            plugin.getLogger().warning("[Fishing cinematic] Cosmetic scene stopped: " + error.getMessage());
        }
    }

    private void destroyScene(Scene scene) {
        if (scene == null) return;
        scene.fireRevealCallbacks();
        cleanupWheelDisplays(scene);
        try {
            scene.itemDisplay.remove();
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
        try {
            scene.textDisplay.remove();
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    public void stop(UUID playerId) {
        flushPendingCallbacks(playerId);
        for (Scene scene : List.copyOf(activeScenes)) {
            if (scene.playerId.equals(playerId)) {
                activeScenes.remove(scene);
                destroyScene(scene);
            }
        }
    }

    public void quit(UUID playerId) {
        stop(playerId);
        lastPlayed.remove(playerId);
    }

    public void clear() {
        for (UUID pid : List.copyOf(pendingRevealCallbacks.keySet())) {
            flushPendingCallbacks(pid);
        }
        for (Scene scene : List.copyOf(activeScenes)) {
            destroyScene(scene);
        }
        activeScenes.clear();
        lastPlayed.clear();
        cachedDecoys.clear();
        lastDecoyCacheTimeNanos = 0L;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
