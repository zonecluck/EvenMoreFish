package com.oheers.fish.fishing;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class FishingHookHolograms {

    private enum HologramState {
        WAITING, BITE, MISSED
    }

    private static final double HOLOGRAM_Y_OFFSET = 0.7D;
    private static final int MISSED_DISPLAY_TICKS = 40;
    private static final float BASE_TEXT_SCALE = 1.05f;
    private static final org.bukkit.Particle PARTICLE_SPLASH = findParticle("SPLASH", "WATER_SPLASH");

    private static @Nullable org.bukkit.Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return org.bukkit.Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    // Cache the animated headers; native countdown text is refreshed at most ten times per second.
    private static final Component[] BITE_ANIMATION_FRAMES = new Component[120];
    // Slow aqua pulse; every frame keeps the plain text exactly "Đang chờ cá".
    private static final Component[] WAITING_ANIMATION_FRAMES = new Component[8];
    // Warm amber pulse when fish is swimming toward the bobber.
    private static final Component[] APPROACH_ANIMATION_FRAMES = new Component[6];
    // Red fade-out while the missed notice is visible.
    private static final Component[] MISSED_ANIMATION_FRAMES = new Component[4];

    private static final Component BITE_ACTION_BAR = MiniMessage.miniMessage().deserialize(
            "<dark_gray>○ <gradient:#FFE600:#FF4FD8:#00F5FF>⚡ Cá đang cắn câu! Nhấp chuột phải để giật cần ngay! ⚡</gradient>"
    ).decoration(TextDecoration.BOLD, false);

    private static final Component MISSED_ACTION_BAR = MiniMessage.miniMessage().deserialize(
            "<dark_gray>○ <red>✗ Hụt cá rồi! Hãy chú ý nhịp cắn câu tiếp theo.</red>"
    ).decoration(TextDecoration.BOLD, false);

    static {
        for (int i = 0; i < 120; i++) {
            int dotFrame = (i / 5) % 3;
            String dots = ".".repeat(dotFrame + 1);

            double phase = i * (2.0 * Math.PI / 30.0);
            String c1 = getLeDatNeonColor(phase);
            String c2 = getLeDatNeonColor(phase + 1.4);
            String c3 = getLeDatNeonColor(phase + 2.8);

            String format = "<gradient:" + c1 + ":" + c2 + ":" + c3 + ">⚡ Hãy giật cần" + dots + " ⚡</gradient>";
            BITE_ANIMATION_FRAMES[i] = MiniMessage.miniMessage().deserialize(format)
                .decoration(TextDecoration.BOLD, false);
        }
        for (int i = 0; i < WAITING_ANIMATION_FRAMES.length; i++) {
            // LeDatRNG-style cyan-ice-aqua shimmer; keeps plain text "Đang chờ cá".
            double glow = (Math.sin(i * Math.PI * 2 / WAITING_ANIMATION_FRAMES.length - Math.PI / 2) + 1) / 2;
            int bright = (int) (175 + glow * 80);
            String color = String.format("#%02x%02x%02x", Math.max(0, bright - 55), bright, 255);
            WAITING_ANIMATION_FRAMES[i] = MiniMessage.miniMessage().deserialize(
                "<gradient:#00F5FF:" + color + ":#55FFFF>Đang chờ cá</gradient>")
                .decoration(TextDecoration.BOLD, false);
        }
        for (int i = 0; i < APPROACH_ANIMATION_FRAMES.length; i++) {
            String dots = ".".repeat((i % 3) + 1);
            APPROACH_ANIMATION_FRAMES[i] = MiniMessage.miniMessage().deserialize(
                "<gradient:#FFE259:#FFA751>🐟 Cá đang đớp mồi" + dots + "</gradient>")
                .decoration(TextDecoration.BOLD, false);
        }
        String[] missed = {"#FF5555", "#E04444", "#A0A0A0", "#777777"};
        for (int i = 0; i < MISSED_ANIMATION_FRAMES.length; i++) {
            MISSED_ANIMATION_FRAMES[i] = MiniMessage.miniMessage().deserialize(
                "<color:" + missed[i] + ">✗ Hụt rồi!</color>")
                .decoration(TextDecoration.BOLD, false);
        }
    }

    private static String getLeDatNeonColor(double phase) {
        int r = (int) (Math.sin(phase) * 55 + 200);
        int g = (int) (Math.sin(phase + 2.0 * Math.PI / 3.0) * 75 + 155);
        int b = (int) (Math.sin(phase + 4.0 * Math.PI / 3.0) * 60 + 195);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private final EvenMoreFish plugin;
    private final Map<UUID, HookHologram> holograms = new HashMap<>();
    private @Nullable MyScheduledTask updateTask;

    FishingHookHolograms(@NotNull EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    void showWaiting(@NotNull FishHook hook) {
        boolean isNewCast = !holograms.containsKey(hook.getUniqueId());
        showOrUpdate(hook, WAITING_ANIMATION_FRAMES[0], 0, HologramState.WAITING);
        if (isNewCast) {
            playCastCue(hook);
        }
    }

    void showBite(@NotNull FishHook hook) {
        showOrUpdate(hook, BITE_ANIMATION_FRAMES[0], 0, HologramState.BITE);
        playBiteCue(hook);
    }

    void showMissed(@NotNull FishHook hook) {
        showOrUpdate(hook, MISSED_ANIMATION_FRAMES[0], MISSED_DISPLAY_TICKS, HologramState.MISSED);
        playMissedCue(hook);
    }

    void remove(@NotNull FishHook hook) {
        remove(hook.getUniqueId());
    }

    void clear() {
        holograms.values().forEach(HookHologram::removeDisplay);
        holograms.clear();
        stopUpdater();
    }

    private void showOrUpdate(@NotNull FishHook hook, @NotNull Component text, int temporaryTicks, @NotNull HologramState state) {
        if (!hook.isValid()) {
            remove(hook);
            return;
        }

        HookHologram hologram = holograms.get(hook.getUniqueId());
        if (hologram == null || !hologram.isValid()) {
            hologram = create(hook, text);
            holograms.put(hook.getUniqueId(), hologram);
        } else {
            hologram.setText(text);
        }

        hologram.setState(state);
        hologram.setTemporaryTicks(temporaryTicks);
        hologram.teleportToHook();
        ensureUpdater();
    }

    private @NotNull HookHologram create(@NotNull FishHook hook, @NotNull Component text) {
        TextDisplay display = hook.getWorld().spawn(getDisplayLocation(hook), TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setGlowing(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setTextOpacity((byte) -1);
            entity.setSeeThrough(false);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setLineWidth(240);
            entity.setViewRange(32F);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
            entity.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf(),
                    new Vector3f(BASE_TEXT_SCALE, BASE_TEXT_SCALE, BASE_TEXT_SCALE),
                    new Quaternionf()
            ));
        });
        return new HookHologram(hook, display, text);
    }

    private static void playCastCue(@NotNull FishHook hook) {
        try {
            if (hook.getShooter() instanceof Player player && player.isOnline()) {
                Location loc = hook.getLocation();
                if (loc != null) {
                    player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.35f, 1.35f);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void playBiteCue(@NotNull FishHook hook) {
        try {
            if (hook.getShooter() instanceof Player player && player.isOnline()) {
                Location loc = hook.getLocation();
                if (loc != null) {
                    player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 0.70f, 1.55f);
                    player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.65f, 1.70f);
                    player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.65f, 1.55f);
                    player.playSound(loc, Sound.ENTITY_FISH_SWIM, SoundCategory.PLAYERS, 0.85f, 1.25f);
                    if (loc.getWorld() != null && PARTICLE_SPLASH != null) {
                        loc.getWorld().spawnParticle(PARTICLE_SPLASH, loc.clone().add(0, 0.25, 0), 20, 0.25, 0.15, 0.25, 0.08);
                    }
                }
                player.sendActionBar(BITE_ACTION_BAR);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void playMissedCue(@NotNull FishHook hook) {
        try {
            if (hook.getShooter() instanceof Player player && player.isOnline()) {
                Location loc = hook.getLocation();
                if (loc != null) {
                    player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.45f, 0.75f);
                }
                player.sendActionBar(MISSED_ACTION_BAR);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private void ensureUpdater() {
        if (updateTask == null || updateTask.isCancelled()) {
            updateTask = EvenMoreFish.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void tick() {
        Iterator<Map.Entry<UUID, HookHologram>> iterator = holograms.entrySet().iterator();
        while (iterator.hasNext()) {
            HookHologram hologram = iterator.next().getValue();
            if (!hologram.tick()) {
                iterator.remove();
            }
        }

        if (holograms.isEmpty()) {
            stopUpdater();
        }
    }

    private void remove(@NotNull UUID hookId) {
        HookHologram hologram = holograms.remove(hookId);
        if (hologram != null) {
            hologram.removeDisplay();
        }
        if (holograms.isEmpty()) {
            stopUpdater();
        }
    }

    private void stopUpdater() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private static @NotNull Location getDisplayLocation(@NotNull FishHook hook) {
        return hook.getLocation().add(0, HOLOGRAM_Y_OFFSET, 0);
    }

    private static final class HookHologram {

        private final FishHook hook;
        private final TextDisplay display;
        private int temporaryTicks;
        private HologramState state = HologramState.WAITING;
        private int biteTicks = 0;
        private int renderTicks;
        private boolean observedPositiveWait = false;
        private final HookCountdownLine countdown = new HookCountdownLine();
        private Component lastText;

        private HookHologram(@NotNull FishHook hook, @NotNull TextDisplay display, @NotNull Component text) {
            this.hook = hook;
            this.display = display;
            this.lastText = text;
        }

        private boolean tick() {
            if (!isValid()) {
                removeDisplay();
                return false;
            }

            teleportToHook();

            if (state == HologramState.BITE) {
                biteTicks++;
                if (biteTicks % 6 == 0) {
                    playBiteTensionTick();
                }
            } else {
                if (temporaryTicks > 0 && --temporaryTicks == 0) {
                    setState(HologramState.WAITING);
                }
            }
            // Native BITE initializes its window after the event; never sample it inside showBite.
            if (++renderTicks % 2 == 0) {
                updateText();
                updateMotionPose();
            }
            return true;
        }

        private void playBiteTensionTick() {
            try {
                if (hook.getShooter() instanceof Player player && player.isOnline()) {
                    Location loc = hook.getLocation();
                    if (loc != null) {
                        float pitch = Math.min(1.85f, 1.30f + 0.05f * (biteTicks / 6));
                        player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.28f, pitch);
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        private void updateMotionPose() {
            try {
                float scale = BASE_TEXT_SCALE;
                float offsetY = 0.0f;
                float rollZ = 0.0f;
                if (state == HologramState.BITE) {
                    scale = BASE_TEXT_SCALE * (1.08f + 0.08f * (float) Math.sin(biteTicks * 0.65));
                    offsetY = 0.04f * (float) Math.abs(Math.sin(biteTicks * 0.55));
                    rollZ = 0.055f * (float) Math.sin(biteTicks * 0.85);
                } else if (state == HologramState.WAITING) {
                    scale = BASE_TEXT_SCALE * (1.0f + 0.025f * (float) Math.sin(renderTicks * 0.14));
                    offsetY = 0.025f * (float) Math.sin(renderTicks * 0.14);
                } else if (state == HologramState.MISSED) {
                    float progress = 1.0f - (Math.max(0, temporaryTicks) / (float) MISSED_DISPLAY_TICKS);
                    scale = BASE_TEXT_SCALE * Math.max(0.55f, 1.0f - 0.35f * progress);
                    offsetY = 0.08f * progress;
                }
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(
                        new Vector3f(0.0f, offsetY, 0.0f),
                        new Quaternionf().rotateZ(rollZ),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf()
                ));
            } catch (RuntimeException | LinkageError ignored) {
            }
        }

        private void updateText() {
            if (state == HologramState.MISSED) {
                int elapsed = MISSED_DISPLAY_TICKS - Math.max(0, temporaryTicks);
                int frame = Math.min(MISSED_ANIMATION_FRAMES.length - 1,
                    elapsed * MISSED_ANIMATION_FRAMES.length / MISSED_DISPLAY_TICKS);
                setText(MISSED_ANIMATION_FRAMES[frame]);
                return;
            }
            boolean biting = state == HologramState.BITE;
            NativeHookTimers.Reading reading = NativeHookTimers.read(hook, biting);
            if (biting && reading.phase() == NativeHookTimers.Phase.BITE && reading.remainingTicks() == 0) {
                // A later listener may cancel BITE; no live native window means no reel prompt.
                setState(HologramState.WAITING);
                biting = false;
                reading = NativeHookTimers.read(hook, false);
            }
            if (reading.phase() == NativeHookTimers.Phase.WAITING && reading.remainingTicks() > 0) {
                observedPositiveWait = true;
            }
            boolean approaching = !biting && (reading.phase() == NativeHookTimers.Phase.APPROACH
                || (observedPositiveWait && reading.phase() == NativeHookTimers.Phase.UNKNOWN));
            Component header = biting
                ? BITE_ANIMATION_FRAMES[biteTicks % 120]
                : (approaching
                    ? APPROACH_ANIMATION_FRAMES[(renderTicks / 4) % APPROACH_ANIMATION_FRAMES.length]
                    : WAITING_ANIMATION_FRAMES[(renderTicks / 8) % WAITING_ANIMATION_FRAMES.length]);
            if (hook.getShooter() instanceof Player shooter && shooter.getUniqueId() != null) {
                int streak = FishingProcessor.getComboStreak(shooter.getUniqueId());
                if (streak >= 2) {
                    int bonusPct = FishingProcessor.getComboBonusPercent(streak);
                    Component comboTag = MiniMessage.miniMessage().deserialize(
                            " <gold>🔥 x" + streak + " (+" + bonusPct + "%)</gold>"
                    ).decoration(TextDecoration.BOLD, false);
                    header = header.append(comboTag);
                }
            }
            if (biting && reading.phase() == NativeHookTimers.Phase.UNKNOWN && hook.isInWater()) {
                reading = new NativeHookTimers.Reading(NativeHookTimers.Phase.BITE, Math.max(1, 24 - biteTicks));
            }
            if (approaching && reading.phase() == NativeHookTimers.Phase.UNKNOWN) {
                Component approachBar = MiniMessage.miniMessage().deserialize(
                        "<gradient:#FFE259:#FFA751>" + "▏".repeat(20) + "</gradient> <yellow>Sắp cắn!</yellow>"
                ).decoration(TextDecoration.BOLD, false);
                setText(header.append(Component.newline()).append(approachBar));
                return;
            }
            setText(countdown.appendTo(header, reading));
        }

        private boolean isValid() {
            return hook.isValid() && display.isValid()
                && (!(hook.getShooter() instanceof Player player)
                    || player.isOnline() && player.getWorld().equals(hook.getWorld()));
        }

        private void setText(@NotNull Component text) {
            if (!text.equals(lastText)) {
                display.text(text);
                lastText = text;
            }
        }

        private void setState(@NotNull HologramState state) {
            if (this.state != state) countdown.reset();
            this.state = state;
            if (state != HologramState.BITE) {
                this.biteTicks = 0;
            }
        }

        private void setTemporaryTicks(int temporaryTicks) {
            this.temporaryTicks = temporaryTicks;
        }

        private void teleportToHook() {
            display.teleport(getDisplayLocation(hook));
        }

        private void removeDisplay() {
            if (display.isValid()) {
                display.remove();
            }
        }

    }

}
