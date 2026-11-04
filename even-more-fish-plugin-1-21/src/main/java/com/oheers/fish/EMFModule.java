package com.oheers.fish;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.oheers.fish.commands.admin.AdminCommand;
import com.oheers.fish.commands.main.MainCommand;
import com.oheers.fish.commands.main.subcommand.AutoFishSubcommand;
import com.oheers.fish.commands.main.subcommand.JournalSubcommand;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.simulator.engine.NativeHookBridge;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.items.ItemConfigResolver;
import com.oheers.fish.items.configs.FireResistantItemConfig;
import com.oheers.fish.items.configs.HideTooltipItemConfig;
import com.oheers.fish.items.configs.ItemRarityItemConfig;
import com.oheers.fish.items.configs.MaxStackSizeItemConfig;
import com.oheers.fish.items.configs.ModernGlowingItemConfig;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class EMFModule extends EvenMoreFish{

    @Override
    protected NativeHookBridge createNativeHookBridge() {
        return new Paper121NativeHookBridge();
    }

    @Override
    protected PacketTransport createPacketTransport() {
        // Class resolution of the optional adapter stays behind a plugin availability check.
        if (java.util.Arrays.stream(getServer().getPluginManager().getPlugins())
            .anyMatch(p -> p.isEnabled() && p.getName().equalsIgnoreCase("packetevents"))) {
            try { return new Paper121PacketEventsTransport(this); }
            catch (LinkageError | RuntimeException failure) {
                getLogger().warning("[Simulator] PacketEvents adapter unavailable: " + failure.getMessage());
            }
        }
        return PacketTransport.unavailable();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        com.oheers.fish.selling.FishExchangeService.install(this);
        // PlayerResourcePackStatusEvent#getID exists in Paper 1.21.1 API;
        // keep the listener only in this variant, not in EMF's older core module.
        getServer().getPluginManager().registerEvents(new Paper121PackStatusListener(this),this);
    }

    @Override
    public void onLoad() {
        registerItemConfigs();
        super.onLoad();
    }

    private void registerItemConfigs() {
        ItemConfigResolver inst = ItemConfigResolver.getInstance();
        inst.setGlowingResolver(ModernGlowingItemConfig::new);
        inst.setFireResistantResolver(FireResistantItemConfig::new);
        inst.setHideTooltipResolver(HideTooltipItemConfig::new);
        inst.setItemRarityResolver(ItemRarityItemConfig::new);
        inst.setMaxStackSizeResolver(MaxStackSizeItemConfig::new);
    }

    @Override
    public void loadCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS.newHandler(event -> {
            event.registrar().register(new MainCommand().get(), MainConfig.getInstance().getMainCommandAliases());
            event.registrar().register(new JournalSubcommand(MainConfig.getInstance().getIndexSubCommandName()).get().build());
            event.registrar().register(new AutoFishSubcommand(MainConfig.getInstance().getAutofishSubCommandName()).get().build());
            if (MainConfig.getInstance().isAdminShortcutCommandEnabled()) {
                String shortcut = MainConfig.getInstance().getAdminShortcutCommandName();
                event.registrar().register(new AdminCommand(shortcut).get());
            }
        }));
    }

    @Override
    public void enableCommands() {
        //nothing
    }

    @Override
    public void registerCommands() {
        //nothing, we register in onLoad()
    }

    @Override
    public void resendCommands() {
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    @Override
    public void disableCommands() {
        //nothing
    }

    // Can probably be moved somewhere else, but they're here for now.

    @Override
    public @NotNull ItemStack getSkullFromUUID(@NotNull UUID uuid) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        skull.editMeta(SkullMeta.class, meta -> {
            PlayerProfile profile = Bukkit.createProfile(uuid, null);
            meta.setPlayerProfile(profile);
        });
        if (MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_21_R4)) {
            NBT.modifyComponents(skull, nbt -> {
                nbt.getOrCreateCompound("minecraft:tooltip_display")
                    .getStringList("hidden_components")
                    .add("minecraft:profile");
            });
        }
        return skull;
    }

    @NotNull
    @Override
    public ItemStack getSkullFromBase64(@NotNull String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        skull.editMeta(SkullMeta.class, meta -> {
            PlayerProfile profile = Bukkit.createProfile(FishUtils.B64_SKULL_UUID, null);
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
        });
        return skull;
    }

}
