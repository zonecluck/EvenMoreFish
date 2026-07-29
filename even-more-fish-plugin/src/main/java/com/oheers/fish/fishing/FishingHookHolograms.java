package com.oheers.fish.fishing;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
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
    private static final Component WAITING_TEXT = Component.text("Đang đợi...", NamedTextColor.YELLOW);
    private static final Component BITE_TEXT = Component.text("Hãy giật cần...", NamedTextColor.GREEN);
    private static final Component MISSED_TEXT = Component.text("Hụt rồi :(", NamedTextColor.RED);

    private static final Title.Times BITE_TITLE_TIMES = Title.Times.times(
        Duration.ZERO,
        Duration.ofMillis(500),
        Duration.ZERO
    );

    // Precomputed cache of BITE animation frames to eliminate String.format, Math.sin, MiniMessage.deserialize and object allocation on tick
    private static final Component[] BITE_ANIMATION_FRAMES = new Component[120];

    static {
        for (int i = 0; i < 120; i++) {
            int dotFrame = (i / 5) % 3;
            String dots = ".".repeat(dotFrame + 1);

            double phase = i * (2.0 * Math.PI / 40.0);
            String c1 = getGalaxyColor(phase);
            String c2 = getGalaxyColor(phase + 1.5);
            String c3 = getGalaxyColor(phase + 3.0);

            String format = "<gradient:" + c1 + ":" + c2 + ":" + c3 + "><bold>Hãy giật cần" + dots + "</bold></gradient>";
            BITE_ANIMATION_FRAMES[i] = MiniMessage.miniMessage().deserialize(format);
        }
    }

    private static String getGalaxyColor(double phase) {
        int r = (int) (Math.sin(phase) * 64 + 191);
        int g = (int) (Math.sin(phase + 2.0 * Math.PI / 3.0) * 50 + 80);
        int b = (int) (Math.sin(phase + 4.0 * Math.PI / 3.0) * 64 + 191);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private final EvenMoreFish plugin;
    private final Map<UUID, HookHologram> holograms = new HashMap<>();
    private @Nullable MyScheduledTask updateTask;

    FishingHookHolograms(@NotNull EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    void showWaiting(@NotNull FishHook hook) {
        showOrUpdate(hook, WAITING_TEXT, 0, HologramState.WAITING);
    }

    void showBite(@NotNull FishHook hook) {
        showOrUpdate(hook, BITE_TEXT, 0, HologramState.BITE);
    }

    void showMissed(@NotNull FishHook hook) {
        showOrUpdate(hook, MISSED_TEXT, MISSED_DISPLAY_TICKS, HologramState.MISSED);
        if (hook.getShooter() instanceof Player player) {
            Title title = Title.title(
                Component.empty(),
                MISSED_TEXT,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(250))
            );
            player.showTitle(title);
        }
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
            if (hologram.getState() == HologramState.BITE && state != HologramState.BITE) {
                if (hook.getShooter() instanceof Player player) {
                    player.clearTitle();
                }
            }
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
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setShadowed(true);
            entity.setSeeThrough(true);
            entity.setLineWidth(160);
            entity.setViewRange(32F);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(1);
        });
        return new HookHologram(hook, display);
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

        private HookHologram(@NotNull FishHook hook, @NotNull TextDisplay display) {
            this.hook = hook;
            this.display = display;
        }

        private boolean tick() {
            if (!isValid()) {
                removeDisplay();
                return false;
            }

            teleportToHook();

            if (state == HologramState.BITE) {
                biteTicks++;
                updateBiteAnimation();
            } else {
                if (temporaryTicks > 0 && --temporaryTicks == 0) {
                    setText(WAITING_TEXT);
                    setState(HologramState.WAITING);
                }
            }
            return true;
        }

        private void updateBiteAnimation() {
            Component animatedText = BITE_ANIMATION_FRAMES[biteTicks % 120];

            setText(animatedText);

            if (hook.getShooter() instanceof Player player) {
                Title title = Title.title(Component.empty(), animatedText, BITE_TITLE_TIMES);
                player.showTitle(title);
            }
        }

        private boolean isValid() {
            return hook.isValid() && display.isValid();
        }

        private void setText(@NotNull Component text) {
            display.text(text);
        }

        private void setState(@NotNull HologramState state) {
            this.state = state;
            if (state != HologramState.BITE) {
                this.biteTicks = 0;
            }
        }

        private @NotNull HologramState getState() {
            return this.state;
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
            if (hook.getShooter() instanceof Player player) {
                player.clearTitle();
            }
        }

    }

}
