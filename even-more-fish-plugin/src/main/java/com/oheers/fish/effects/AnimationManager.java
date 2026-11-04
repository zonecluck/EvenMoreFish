package com.oheers.fish.effects;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionEntry;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.messages.EMFSingleMessage;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class AnimationManager {

    private static AnimationManager instance;
    private final Random random = new Random();

    private final @Nullable Particle happyParticle = findParticle("HAPPY_VILLAGER", "VILLAGER_HAPPY");
    private final @Nullable Particle totemParticle = findParticle("TOTEM_OF_UNDYING", "TOTEM");
    private final @Nullable Particle splashParticle = findParticle("SPLASH", "WATER_SPLASH");
    private final @Nullable Particle bubbleParticle = findParticle("BUBBLE_POP", "WATER_BUBBLE");
    private final @Nullable Particle wakeParticle = findParticle("FISHING", "WATER_WAKE");
    private final @Nullable Particle waxParticle = findParticle("WAX_ON", "CRIT");
    private final @Nullable Particle endRodParticle = findParticle("END_ROD");
    private final @Nullable Particle enchantParticle = findParticle("ENCHANT", "ENCHANTMENT_TABLE");

    private AnimationManager() {}

    public static @NotNull AnimationManager getInstance() {
        if (instance == null) {
            instance = new AnimationManager();
        }
        return instance;
    }

    private static @Nullable Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    private Section getAnimationConfig() {
        return MainConfig.getInstance().getConfig().getSection("animations");
    }

    private boolean isEnabled(String path, boolean def) {
        Section section = getAnimationConfig();
        return section == null || section.getBoolean(path, def);
    }

    /**
     * Plays cinematic title, sound, and particle effects when a competition starts.
     */
    public void playCompetitionStart(@NotNull Competition competition) {
        if (!isEnabled("competition-start.enabled", true)) {
            return;
        }

        String titleStr = MainConfig.getInstance().getConfig().getString(
                "animations.competition-start.title",
                "<gradient:#00C0FF:#42E8E0><bold>⚡ CUỘC THI CÂU CÁ ⚡</gradient>"
        );
        String subTitleStr = MainConfig.getInstance().getConfig().getString(
                "animations.competition-start.subtitle",
                "<yellow>Cuộc thi đã bắt đầu! Hãy quăng cần và tranh tài!</yellow>"
        );

        Component mainTitle = EMFSingleMessage.fromString(titleStr).getComponentMessage();
        Component subTitle = EMFSingleMessage.fromString(subTitleStr).getComponentMessage();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofMillis(3000),
                Duration.ofMillis(800)
        );
        Title title = Title.title(mainTitle, subTitle, times);

        Sound sound = Sound.sound(
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE.key(),
                Sound.Source.MASTER,
                1.0f,
                1.1f
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(sound, Sound.Emitter.self());
            if (happyParticle != null) {
                spawnCircleParticles(player.getLocation().add(0, 1, 0), happyParticle, 15, 1.2);
            }
        }
    }

    /**
     * Plays celebration title and fireworks for winners when a competition ends.
     */
    /**
     * Plays celebration title and particle bursts for winners when a competition ends.
     */
    public void playCompetitionEnd(@NotNull Competition competition, @NotNull List<CompetitionEntry> entries) {
        if (!isEnabled("competition-end.enabled", true)) {
            return;
        }

        if (entries.isEmpty()) {
            return;
        }

        CompetitionEntry topEntry = entries.get(0);
        Player topPlayer = Bukkit.getPlayer(topEntry.getPlayer());
        String winnerName = topPlayer != null ? topPlayer.getName() : "Không rõ";

        String titleStr = MainConfig.getInstance().getConfig().getString(
                "animations.competition-end.title",
                "<gradient:#FFD700:#FFA500><bold>🏆 CUỘC THI KẾT THÚC 🏆</gradient>"
        );
        String subTitleStr = MainConfig.getInstance().getConfig().getString(
                "animations.competition-end.subtitle",
                "<yellow>Chúc mừng <gold><bold>" + winnerName + "</bold></gold> đã đạt <yellow>Top 1!</yellow>"
        );

        Component mainTitle = EMFSingleMessage.fromString(titleStr).getComponentMessage();
        Component subTitle = EMFSingleMessage.fromString(subTitleStr).getComponentMessage();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofMillis(4000),
                Duration.ofMillis(1000)
        );
        Title title = Title.title(mainTitle, subTitle, times);

        Sound sound = Sound.sound(
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE.key(),
                Sound.Source.MASTER,
                1.0f,
                0.9f
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(sound, Sound.Emitter.self());
        }

        // Spawn celebration particle bursts for top 3 winners (no entity fireworks).
        int topLimit = Math.min(3, entries.size());
        for (int i = 0; i < topLimit; i++) {
            Player winner = Bukkit.getPlayer(entries.get(i).getPlayer());
            if (winner != null && winner.isOnline()) {
                spawnWinnerCelebration(winner.getLocation(), i);
            }
        }
    }

    /**
     * Plays visual & sound effects when a player catches a rare or legendary fish.
     */
    public void playRareFishCatch(@NotNull Player player, @NotNull Fish fish) {
        Rarity rarity = fish.getRarity();
        if (!isEnabled("rare-catch.enabled", true)) {
            return;
        }

        var cinematic = EvenMoreFish.getInstance().getCatchCinematics();
        boolean isRare = cinematic != null ? cinematic.eligible(fish)
            : RareCatchPolicy.eligible(rarity.getId(), rarity.getIndex(), rarity.getWeight(),
                List.of("Rare", "Epic", "Legendary", "Mythical"), List.of());
        if (!isRare) {
            return;
        }
        var simulator = EvenMoreFish.getInstance().getSimulatorModule();
        if (simulator != null && !simulator.ownEffectsEnabled(player.getUniqueId())) return;

        String titleStr = "<gold>★ <white>CÂU ĐƯỢC CÁ HIẾM <gold>★";
        String subTitleStr = fish.getDisplayName() + " <gray>(<white>" + rarity.getId() + "<gray>)";

        Component mainTitle = EMFSingleMessage.fromString(titleStr).getComponentMessage();
        Component subTitle = EMFSingleMessage.fromString(subTitleStr).getComponentMessage();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofMillis(650),
                Duration.ofMillis(200)
        );
        Title title = Title.title(mainTitle, subTitle, times);

        if (isEnabled("rare-catch.title-enabled", false)
            && (simulator == null || simulator.ownHudProfile(player.getUniqueId()).hud())) player.showTitle(title);

        Sound sound = Sound.sound(
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key(),
                Sound.Source.MASTER,
                0.3f,
                1.2f
        );
        if (simulator == null || simulator.ownHudProfile(player.getUniqueId()).sound())
            player.playSound(sound, Sound.Emitter.self());

        if (totemParticle != null && (simulator == null || simulator.ownHudProfile(player.getUniqueId()).particle())) {
            // Rare fish: rising spiral + burst ring instead of a single puff.
            Location center = player.getLocation().add(0, 0.7, 0);
            spawnPrivateCatchParticles(player, center, totemParticle, 18, true);
            if (endRodParticle != null) {
                spawnPrivateCatchParticles(player, center.clone().add(0, 0.3, 0), endRodParticle, 12, false);
            }
        }
    }

    /**
     * Plays double catch visual and sound effects when luck duplicates fish reward.
     */
    public void playDoubleFishAnimation(@NotNull Player player, @NotNull Fish fish) {
        if (!isEnabled("double-catch.enabled", true)) {
            return;
        }
        var simulator = EvenMoreFish.getInstance().getSimulatorModule();
        if (simulator != null && !simulator.ownEffectsEnabled(player.getUniqueId())) return;

        String msg = MainConfig.getInstance().getConfig().getString(
                "animations.double-catch.message",
                "<gradient:#FFE600:#FF5500>⚡ <bold>MAY MẮN NHÂN ĐÔI!</bold> Bạn nhận được x2 {fish}! ⚡</gradient>"
        );

        EMFSingleMessage singleMessage = EMFSingleMessage.fromString(msg);
        singleMessage.setVariable("{fish}", fish.getDisplayName());
        if (simulator == null || simulator.ownHudProfile(player.getUniqueId()).hud())
            player.sendActionBar(singleMessage.getComponentMessage());

        Sound sound = Sound.sound(
                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP.key(),
                Sound.Source.MASTER,
                1.0f,
                1.5f
        );
        if (simulator == null || simulator.ownHudProfile(player.getUniqueId()).sound())
            player.playSound(sound, Sound.Emitter.self());

        if (waxParticle != null && (simulator == null || simulator.ownHudProfile(player.getUniqueId()).particle())) {
            Location loc = player.getLocation().add(0, 1.2, 0);
            try {
                player.spawnParticle(waxParticle, loc, 12, 0.4, 0.4, 0.4, 0.05);
            } catch (Throwable ignored) {}
        }
    }

    private void spawnPrivateCatchParticles(Player viewer, Location center, Particle particle, int count, boolean spiral) {
        try {
            for (int i = 0; i < count; i++) {
                double angle = spiral ? 0.4 * i : 2 * Math.PI * i / count;
                double radius = spiral ? 0.8 : 0.9;
                viewer.spawnParticle(particle, center.clone().add(radius * Math.cos(angle), spiral ? 0.1 * i : 0, radius * Math.sin(angle)),
                    1, 0, 0, 0, spiral ? 0.02 : 0);
            }
        } catch (RuntimeException | LinkageError ignored) {}
    }

    /** Private bite cue after the cancellable native event has been accepted. */
    public void playHookBiteParticles(@NotNull Player viewer, @Nullable Location hookLocation) {
        if (hookLocation == null || hookLocation.getWorld() == null
            || !viewer.getWorld().equals(hookLocation.getWorld()) || !isEnabled("hook-bite.enabled", true)) return;
        var simulator = EvenMoreFish.getInstance().getSimulatorModule();
        if (simulator != null && (!simulator.ownEffectsEnabled(viewer.getUniqueId())
            || !simulator.ownHudProfile(viewer.getUniqueId()).particle())) return;
        try {
            if (splashParticle != null) viewer.spawnParticle(splashParticle, hookLocation, 8, 0.25, 0.1, 0.25, 0.08);
            if (bubbleParticle != null) viewer.spawnParticle(bubbleParticle, hookLocation.clone().add(0, 0.1, 0), 5, 0.15, 0.15, 0.15, 0.03);
            if (wakeParticle != null) viewer.spawnParticle(wakeParticle, hookLocation, 6, 0.25, 0.1, 0.25, 0.05);
        } catch (RuntimeException | LinkageError ignored) {}
    }

    /** Legacy public cue retained for addon binary compatibility. Native fishing uses the viewer overload. */
    public void playHookBiteParticles(@Nullable Location hookLocation) {
        if (hookLocation == null || hookLocation.getWorld() == null) {
            return;
        }
        if (!isEnabled("hook-bite.enabled", true)) {
            return;
        }

        try {
            if (splashParticle != null) {
                hookLocation.getWorld().spawnParticle(splashParticle, hookLocation, 25, 0.3, 0.1, 0.3, 0.15);
            }
            if (bubbleParticle != null) {
                hookLocation.getWorld().spawnParticle(bubbleParticle, hookLocation.clone().add(0, 0.1, 0), 10, 0.2, 0.2, 0.2, 0.05);
            }
            if (wakeParticle != null) {
                hookLocation.getWorld().spawnParticle(wakeParticle, hookLocation, 15, 0.3, 0.1, 0.3, 0.1);
            }
            if (endRodParticle != null) {
                spawnCircleParticles(hookLocation.clone().add(0, 0.15, 0), endRodParticle, 8, 0.45);
            }
        } catch (Throwable ignored) {}
    }

    private void spawnCircleParticles(Location center, Particle particle, int count, double radius) {
        if (center.getWorld() == null || particle == null) return;
        try {
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                center.getWorld().spawnParticle(particle, center.clone().add(x, 0, z), 1, 0, 0, 0, 0);
            }
        } catch (Throwable ignored) {}
    }

    private void spawnSpiralParticles(Location center, Particle particle, int count) {
        if (center.getWorld() == null || particle == null) return;
        try {
            for (int i = 0; i < count; i++) {
                double angle = 0.4 * i;
                double y = 0.1 * i;
                double x = 0.8 * Math.cos(angle);
                double z = 0.8 * Math.sin(angle);
                center.getWorld().spawnParticle(particle, center.clone().add(x, y, z), 1, 0, 0, 0, 0.02);
            }
        } catch (Throwable ignored) {}
    }

    private void spawnWinnerCelebration(Location location, int rank) {
        if (location.getWorld() == null) return;
        try {
            Location center = location.clone().add(0, 1, 0);
            // Gold for #1, silver-blue for #2, bronze-orange for #3.
            if (happyParticle != null) {
                spawnCircleParticles(center, happyParticle, 20, 1.4);
                spawnSpiralParticles(center, happyParticle, 24);
            }
            if (enchantParticle != null) {
                location.getWorld().spawnParticle(enchantParticle, center, 18, 0.6, 0.8, 0.6, 0.15);
            }
            if (endRodParticle != null && rank == 0) {
                location.getWorld().spawnParticle(endRodParticle, center.clone().add(0, 0.6, 0), 24, 0.8, 0.6, 0.8, 0.1);
            }
        } catch (Throwable ignored) {}
    }
}
