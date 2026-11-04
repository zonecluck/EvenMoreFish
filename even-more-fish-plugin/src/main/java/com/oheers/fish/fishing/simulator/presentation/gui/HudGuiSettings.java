package com.oheers.fish.fishing.simulator.presentation.gui;

import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;
import java.util.Map;

/** Validated immutable GUI template; text originates from simulator/hud-gui.yml. */
public record HudGuiSettings(boolean enabled, Component title, int size,
    Material filler, Component fillerName, Sound clickSound,
    Map<UiProfile,Button> buttons) {
    public HudGuiSettings { buttons=Map.copyOf(buttons); }
    public static HudGuiSettings disabled() {
        return new HudGuiSettings(false, Component.empty(),27,Material.GRAY_STAINED_GLASS_PANE,
            Component.empty(),Sound.UI_BUTTON_CLICK,Map.of());
    }
    public record Button(int slot, Material material, Component name, List<Component> lore, boolean glow) {
        public Button {lore=List.copyOf(lore);}
    }
}
