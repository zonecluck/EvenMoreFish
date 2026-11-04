package com.oheers.fish.fishing.simulator;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MessageConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Shares the existing EMF messages.yml and locale migration/fallback system. */
public final class SimulatorMessages {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Set<String> MISSING = new HashSet<>();

    private SimulatorMessages() {}

    public static void send(@NotNull CommandSender sender, @NotNull String key) {
        send(sender, key, Map.of());
    }

    public static void send(@NotNull CommandSender sender, @NotNull String key, Map<String, String> variables) {
        Component text=render(key,variables);
        if(text!=null)sender.sendMessage(text);
    }

    public static void actionBar(@NotNull Player player, @NotNull String key, Map<String,String> variables) {
        Component text=render(key,variables);
        if(text!=null)player.sendActionBar(text);
    }

    /** Optional localized reveal only; no camera manipulation or item messages. */
    public static void title(@NotNull Player player, String titleKey, String subtitleKey, Map<String,String> variables){
        Component title=render(titleKey,variables);
        Component subtitle=render(subtitleKey,variables);
        if(title!=null && subtitle!=null){
            player.showTitle(Title.title(title,subtitle,Title.Times.times(
                Duration.ofMillis(150),Duration.ofMillis(850),Duration.ofMillis(250))));
        }
    }

    private static Component render(@NotNull String key,Map<String,String> variables){
        var config = MessageConfig.getInstance().getConfig();
        String value = config.getString("simulator." + key);
        if (value == null && config.getDefaults() != null) {
            value = config.getDefaults().getString("simulator." + key);
        }
        if (value == null) {
            if (MISSING.add(key)) {
                EvenMoreFish.getInstance().getLogger().warning("[Simulator] Missing locale message simulator." + key);
            }
            value = config.getString("simulator.generic-error");
            if (value == null && config.getDefaults() != null) {
                value = config.getDefaults().getString("simulator.generic-error");
            }
            if (value == null) return null; // fail silently for player, log only once above
        }
        String prefix = config.getString("prefix-admin", "");
        // Both templates and prefix originate from administrator-controlled locale YAML.
        value = value.replace("{prefix}", prefix);
        for (var entry : variables.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", MINI.escapeTags(entry.getValue()));
        }
        return MINI.deserialize(value);
    }
}
