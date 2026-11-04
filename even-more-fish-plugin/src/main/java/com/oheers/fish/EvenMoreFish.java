package com.oheers.fish;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.oheers.fish.api.EMFAPI;
import com.oheers.fish.api.baits.AbstractBaitManager;
import com.oheers.fish.api.economy.Economy;
import com.oheers.fish.api.events.EMFPluginReloadEvent;
import com.oheers.fish.api.fishing.items.AbstractFishManager;
import com.oheers.fish.api.plugin.EMFPlugin;
import com.oheers.fish.api.registry.EMFRegistry;
import com.oheers.fish.baits.manager.BaitManager;
import com.oheers.fish.competition.AutoRunner;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionQueue;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.events.McMMOTreasureEvent;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.simulator.SimulatorModule;
import com.oheers.fish.effects.VanillaCatchCinematicService;
import com.oheers.fish.fishing.pool.FishingPoolService;
import com.oheers.fish.fishing.pool.HookedFishPacketService;
import com.oheers.fish.fishing.simulator.engine.NativeHookBridge;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.fishing.rods.RodManager;
import com.oheers.fish.autofish.AutoFishMenuManager;
import com.oheers.fish.autofish.AutoFishingService;
import com.oheers.fish.gui.guis.FishIndexMenuManager;
import com.oheers.fish.gui.guis.StorageFishShopGui;
import com.oheers.fish.messages.ConfigMessage;
import com.oheers.fish.plugin.ConfigurationManager;
import com.oheers.fish.plugin.DependencyManager;
import com.oheers.fish.plugin.EventManager;
import com.oheers.fish.plugin.IntegrationManager;
import com.oheers.fish.plugin.MetricsManager;
import com.oheers.fish.plugin.PluginDataManager;
import com.oheers.fish.selling.TurtleStorageFishBridge;
import com.oheers.fish.update.UpdateChecker;
import de.themoep.inventorygui.InventoryGui;
import de.tr7zw.changeme.nbtapi.NBT;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import uk.firedev.vanishchecker.VanishChecker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public abstract class EvenMoreFish extends EMFPlugin {
    private final Random random = ThreadLocalRandom.current();
    private final Toggle toggle;

    // Do some fish in some rarities have the comp-check-exempt: true.
    private boolean raritiesCompCheckExempt = false;
    private CompetitionQueue competitionQueue;
    private final AutoRunner autoRunner = new AutoRunner();

    // this is for pre-deciding a rarity and running particles if it will be chosen
    // it's a work-in-progress solution and probably won't stick.
    private Map<UUID, Rarity> decidedRarities;
    private volatile boolean isUpdateAvailable;

    private DependencyManager dependencyManager;
    private ConfigurationManager configurationManager;
    private PluginDataManager pluginDataManager;
    private IntegrationManager integrationManager;
    private EventManager eventManager;
    private MetricsManager metricsManager;
    private SimulatorModule simulatorModule;
    private VanillaCatchCinematicService catchCinematics;
    private FishingPoolService fishingPools;
    private HookedFishPacketService hookedFishPackets;
    private AutoFishingService autoFishing;

    private static EvenMoreFish instance;
    private static TaskScheduler scheduler;
    private EMFAPI api;

    public static @NotNull EvenMoreFish getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Plugin not initialized yet!");
        }
        return instance;
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public EvenMoreFish() {
        this.toggle = new Toggle(this);
    }

    @Override
    public void onLoad() {
        if (!NBT.preloadApi()) {
            throw new RuntimeException("NBT-API wasn't initialized properly, disabling the plugin");
        }
        instance = this;
        loadCommands();
    }

    /**
     * Stuff to do onLoad() with commands
     */
    public abstract void loadCommands();

    public abstract void enableCommands();

    public abstract void registerCommands();

    public abstract void resendCommands();

    @Override
    public void onEnable() {
        enableCommands();

        scheduler = UniversalScheduler.getScheduler(this);

        this.api = new EMFAPI();

        this.decidedRarities = new HashMap<>();

        this.configurationManager = new ConfigurationManager(this);
        this.configurationManager.loadConfigurations(); //need to test, order may be important

        this.dependencyManager = new DependencyManager(this);
        this.dependencyManager.checkDependencies(); // need to test, order may be important, if it is, we introduce multiple stages with events

        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.loadAddons();

        this.pluginDataManager = new PluginDataManager(this);

        // Simulator is an optional engine router. No second fishing listener is registered.
        PacketTransport fishingTransport = createPacketTransport();
        this.simulatorModule = new SimulatorModule(this, createNativeHookBridge(), fishingTransport);
        this.simulatorModule.start();
        this.catchCinematics = new VanillaCatchCinematicService(this);
        this.fishingPools = new FishingPoolService(this);
        this.fishingPools.reload();
        this.hookedFishPackets = new HookedFishPacketService(this, fishingTransport);
        this.autoFishing = new AutoFishingService(this);

        this.eventManager = new EventManager(this);
        this.eventManager.registerCoreListeners();
        this.eventManager.registerOptionalListeners();

        FishManager.getInstance().load();

        // Always load this after FishManager
        BaitManager.getInstance().load();

        // Always load this after BaitManager
        RodManager.getInstance().load();
        // Fish catalog is available now; preview never mutates templates or grants rewards.
        this.simulatorModule.onCatalogReady();

        // Always load this after RodManager
        this.competitionQueue = new CompetitionQueue();
        this.competitionQueue.load();

        // check for updates on the Modrinth page
        new UpdateChecker(this).checkUpdate().thenAccept(available -> {
            isUpdateAvailable = available;
            if (available) {
                getLogger().warning("A new update is available! Download it from https://modrinth.com/plugin/evenmorefish");
            }
        });

        this.metricsManager = new MetricsManager(this);
        this.metricsManager.setupMetrics();

        autoRunner.start();

        registerCommands();

        // Preload GUI & selling classes into the classloader to prevent NoClassDefFoundError if the jar is replaced at runtime
        preloadRuntimeClasses();

        // Attempt to resume a competition if the temporary file exists.
        Competition.resumeFromFile();

        getLogger().info(() -> "EvenMoreFish by Oheers : Enabled");
    }

    private void preloadRuntimeClasses() {
        String[] classesToPreload = new String[]{
            "com.oheers.fish.utils.nbt.NbtUtils",
            "com.oheers.fish.utils.nbt.NbtKeys",
            "com.oheers.fish.utils.nbt.NbtVersion",
            "com.oheers.fish.utils.WeightedRandom",
            "com.oheers.fish.gui.guis.SellGui",
            "com.oheers.fish.gui.guis.SellGui$SellState",
            "com.oheers.fish.gui.guis.StorageFishShopGui",
            "com.oheers.fish.gui.guis.MainMenuGui",
            "com.oheers.fish.gui.guis.FishJournalGui",
            "com.oheers.fish.gui.guis.BaitsGui",
            "com.oheers.fish.gui.guis.ApplyBaitsGui",
            "com.oheers.fish.gui.ConfigGui",
            "com.oheers.fish.gui.GuiUtils",
            "com.oheers.fish.selling.SellHelper",
            "com.oheers.fish.selling.SoldFish",
            "com.oheers.fish.selling.TurtleStorageFishBridge",
            "com.oheers.fish.selling.WorthNBT",
            "com.oheers.fish.commands.CommandUtils",
            "com.oheers.fish.gui.guis.FishIndexMenuManager",
            "com.oheers.fish.autofish.AutoFishMenuManager"
        };
        ClassLoader loader = getClass().getClassLoader();
        for (String className : classesToPreload) {
            try {
                Class.forName(className, true, loader);
            } catch (Throwable ignored) {
            }
        }
        try {
            java.io.File jarFile = getFile();
            if (jarFile != null && jarFile.isFile()) {
                try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
                    java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        java.util.jar.JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!entry.isDirectory() && name.endsWith(".class") && name.startsWith("com/oheers/fish/")) {
                            String className = name.substring(0, name.length() - 6).replace('/', '.');
                            try {
                                Class.forName(className, false, loader);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public abstract void disableCommands();

    @Override
    public void onDisable() {
        // Stop all owned Simulator probes before plugins, hooks and fish catalogs unload.
        if (simulatorModule != null) simulatorModule.shutdown();
        if (catchCinematics != null) catchCinematics.clear();
        if (fishingPools != null) fishingPools.shutdown();
        if (hookedFishPackets != null) hookedFishPackets.shutdown();
        if (autoFishing != null) autoFishing.shutdown();
        // Do this first.
        autoRunner.stop();

        disableCommands();

        terminateGuis();
        // Ends the current competition in case the plugin is being disabled when the server will continue running
        Competition active = Competition.getCurrentlyActive();
        if (active != null) {
            active.end(false, true);
        }

        // Don't use the scheduler here because it will throw errors on disable
        if (this.pluginDataManager != null) {
            this.pluginDataManager.shutdown();
        }

        // Make sure this is in the reverse order of loading.
        this.competitionQueue.unload();
        RodManager.getInstance().unload();
        BaitManager.getInstance().unload();
        FishManager.getInstance().unload();

        getLogger().info(() -> "EvenMoreFish by Oheers : Disabled");
    }


    @Override
    public boolean isDebugSession() {
        return MainConfig.getInstance().shouldDebug();
    }

    // gets called on server shutdown to simulate all players closing their Guis
    private void terminateGuis() {
        try {
            FishIndexMenuManager.getInstance().closeAll();
        } catch (Throwable ignored) {
        }
        try {
            AutoFishMenuManager.getInstance().closeAll();
        } catch (Throwable ignored) {
        }
        try {
            StorageFishShopGui.getInstance().closeAll();
        } catch (Throwable ignored) {
        }
        getServer().getOnlinePlayers().forEach(player -> {
            try {
                InventoryGui inventoryGui = InventoryGui.getOpen(player);
                if (inventoryGui != null) {
                    inventoryGui.close();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public void reload(@Nullable CommandSender sender) {
        terminateGuis();

        this.configurationManager.reloadConfigurations();
        if (catchCinematics != null) catchCinematics.clear();
        if (hookedFishPackets != null) hookedFishPackets.clear();
        if (fishingPools != null) fishingPools.reload();
        if (simulatorModule != null) simulatorModule.reload();

        FishManager.getInstance().reload();
        TurtleStorageFishBridge.clearCache();
        BaitManager.getInstance().reload();
        RodManager.getInstance().reload();
        if (simulatorModule != null) simulatorModule.onCatalogReady();

        HandlerList.unregisterAll(McMMOTreasureEvent.getInstance());

        this.eventManager.registerOptionalListeners();

        competitionQueue.reload();

        // Refresh global economy instance with any new EconomyTypes that may have been registered.
        Economy.getInstance().setEconomyTypes(EMFRegistry.ECONOMY_TYPE.getRegistry().values());

        if (sender != null) {
            ConfigMessage.RELOAD_SUCCESS.getMessage().send(sender);
        }

        resendCommands();

        // This event is not cancellable.
        new EMFPluginReloadEvent().callEvent();
    }

    /** Unavailable by default for old server variants; Paper 1.21 overrides this adapter. */
    protected NativeHookBridge createNativeHookBridge() {
        return NativeHookBridge.unsupported();
    }

    protected PacketTransport createPacketTransport() {
        return PacketTransport.unavailable(); // other variants remain fully standalone
    }

    public SimulatorModule getSimulatorModule() {
        return simulatorModule;
    }

    public VanillaCatchCinematicService getCatchCinematics() { return catchCinematics; }
    public FishingPoolService getFishingPools() { return fishingPools; }
    public HookedFishPacketService getHookedFishPackets() { return hookedFishPackets; }
    public AutoFishingService getAutoFishing() { return autoFishing; }

    public Random getRandom() {
        return random;
    }

    public Toggle getToggle() {
        return toggle;
    }

    public boolean isRaritiesCompCheckExempt() {
        return raritiesCompCheckExempt;
    }

    public void setRaritiesCompCheckExempt(boolean exempt) {
        this.raritiesCompCheckExempt = exempt;
    }

    public CompetitionQueue getCompetitionQueue() {
        return competitionQueue;
    }

    public AutoRunner getAutoRunner() {
        return autoRunner;
    }

    public Map<UUID, Rarity> getDecidedRarities() {
        return decidedRarities;
    }

    public boolean isUpdateAvailable() {
        return isUpdateAvailable;
    }

    /**
     * @deprecated The methods this class provided can now be found in {@link AbstractFishManager} and {@link AbstractBaitManager}.
     */
    @Deprecated(forRemoval = true)
    public EMFAPI getApi() {
        return api;
    }

    public List<Player> getVisibleOnlinePlayers() {
        if (MainConfig.getInstance().shouldRespectVanish()) {
            return VanishChecker.getVisibleOnlinePlayers();
        }
        return List.copyOf(Bukkit.getOnlinePlayers());
    }

    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }

    public PluginDataManager getPluginDataManager() {
        return pluginDataManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public MetricsManager getMetricsManager() {
        return metricsManager;
    }

    // Can probably be moved somewhere else, but they're here for now.

    @ApiStatus.Internal
    public abstract @NotNull ItemStack getSkullFromUUID(@NotNull UUID uuid);

    @ApiStatus.Internal
    public abstract @NotNull ItemStack getSkullFromBase64(@NotNull String base64);

    @Override
    public @NotNull Component getRewardCatchupMessage() {
        return ConfigMessage.REWARD_CATCHUP.getMessage().getComponentMessage();
    }

}
