package com.oheers.fish.fishing.simulator;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.Checks;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.config.simulator.SimulatorConfigLoader;
import com.oheers.fish.config.simulator.SimulatorSettings;
import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import com.oheers.fish.fishing.simulator.ai.FishAttractionPolicy;
import com.oheers.fish.fishing.simulator.school.SchoolConfigLoader;
import com.oheers.fish.fishing.simulator.encounter.RareEncounterConfigLoader;
import com.oheers.fish.fishing.simulator.encounter.RareEncounterDirector;
import com.oheers.fish.fishing.simulator.model.FishModelConfigLoader;
import com.oheers.fish.fishing.simulator.model.FishModelSettings;
import com.oheers.fish.fishing.simulator.model.FishPackAudience;
import com.oheers.fish.fishing.simulator.model.FishRenderPolicy;
import com.oheers.fish.fishing.simulator.environment.EcosystemConfigLoader;
import com.oheers.fish.fishing.simulator.commit.SimulatorCatchService;
import com.oheers.fish.fishing.simulator.effects.SimulatorEffectsConfigLoader;
import com.oheers.fish.fishing.simulator.effects.SimulatorEffectsService;
import com.oheers.fish.fishing.simulator.effects.PremiumCinematicConfigLoader;
import com.oheers.fish.fishing.simulator.presentation.FishingPresentationService;
import com.oheers.fish.fishing.simulator.presentation.SimulatorPresentationConfigLoader;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import com.oheers.fish.fishing.simulator.presentation.gui.HudGuiConfigLoader;
import com.oheers.fish.fishing.simulator.presentation.gui.HudSettingsGui;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.WaterPoint;
import com.oheers.fish.fishing.simulator.engine.NativeHookBridge;
import com.oheers.fish.fishing.simulator.engine.ProbeSession;
import com.oheers.fish.fishing.simulator.zones.SimulatorZoneResolver;
import com.oheers.fish.fishing.simulator.zones.ZoneHologramService;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.permissions.UserPerms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Phase 7: region router and admin-only reward integration gate.
 * Ordinary players always remain on legacy EMF; no production Simulator claim
 * is made until Paper G3/G5/G7 verification passes.
 * Paper main thread only; NOT Folia-compatible.
 */
public final class SimulatorModule {
    private static final int MAX_PENDING_CASTS = 64;
    private static final long TOMBSTONE_TICKS = 100;
    private static final int MAX_TOMBSTONES = 4096;
    private final EvenMoreFish plugin;
    private final NativeHookBridge hookBridge;
    private final SimulatorConfigLoader configs;
    private final SimulatorZoneResolver zones;
    private final ZoneHologramService zoneHolograms;
    private final AmbientPreviewService ambient;
    private final FishAttractionPolicy attraction = new FishAttractionPolicy();
    private final SimulatorCatchService catchService;
    private final SimulatorEffectsConfigLoader effectsConfig;
    private final SchoolConfigLoader schoolConfig;
    private final EcosystemConfigLoader ecosystemConfig;
    private final RareEncounterConfigLoader encounterConfig;
    private final FishModelConfigLoader modelConfig;
    private final FishPackAudience packAudience=new FishPackAudience();
    private final FishRenderPolicy modelPolicy;
    private final PremiumCinematicConfigLoader cinematicConfig;
    private final SimulatorEffectsService effects;
    private final SimulatorPresentationConfigLoader presentationConfig;
    private final FishingPresentationService presentation;
    private final HudGuiConfigLoader hudGuiConfig;
    private final HudSettingsGui hudGui;
    private final Map<UUID,FishAttractionPolicy.Snapshot> aiSnapshots = new HashMap<>();
    private @Nullable FishAiDirector aiDirector;
    private final FishAiDirector.Listener aiFeedback = new FishAiDirector.Listener() {
        @Override public void phase(UUID playerId,UUID hookId,FishAiDirector.Phase phase,
                                    com.oheers.fish.fishing.simulator.population.FishKey fishKey) {
            trace(playerId,hookId,"AI_"+phase+" fish="+fishKey);
            Player player=Bukkit.getPlayer(playerId);
            if(player==null || !player.isOnline() || configs.current()==null)return;
            try {
                presentation.phase(playerId,hookId,phase,ticks,
                    configs.current().aiPreview().reactionWindowTicks());
                var nativeHook=getHook(hookId);
                if(nativeHook!=null && fishKey!=null && configs.current()!=null){
                    effects.phase(player,hookId,phase,fishKey,ticks,
                        configs.current().aiPreview().reactionWindowTicks(),nativeHook.getLocation());
                    if(phase==FishAiDirector.Phase.BITE_READY)
                        ambient.biteAt(nativeHook.getLocation(),playerId,ticks);
                }
            } catch (RuntimeException error) {
                // Visuals are never allowed to terminate an authoritative fishing session.
                effects.clearHud(playerId);
                plugin.getLogger().warning("[Simulator] Visual cue failed: "+error.getMessage());
            }
        }
        @Override public void finished(UUID playerId,UUID hookId,FishAiDirector.Outcome outcome,
                                       com.oheers.fish.fishing.simulator.population.FishKey fishKey) {
            trace(playerId,hookId,"AI_"+outcome+" fish="+fishKey);
            try {
                Player player=Bukkit.getPlayer(playerId);
                if(player!=null && player.isOnline()) {
                    presentation.result(playerId,hookId,outcome,ticks);
                    var nativeHook=getHook(hookId);
                    if(nativeHook!=null)ambient.disturbAt(nativeHook.getLocation(),playerId,ticks);
                    var session=sessions.get(playerId);
                    if(nativeHook!=null && session!=null && fishKey!=null){
                        effects.finished(player,hookId,outcome,fishKey,nativeHook.getLocation(),
                            session.zoneId(),ambient.visualModel(fishKey),ticks);
                    }
                }
            } catch (RuntimeException error) {
                // UI/packet failures must never rewrite a successful reward result.
                plugin.getLogger().warning("[Simulator] Result visual failed: "+error.getMessage());
            } finally {
                // A miss can happen without a reel event; always close the native hook.
                endSession(playerId,true);
            }
        }
    };
    private int tickerPeriod=2;
    private final Deque<String> trace = new ArrayDeque<>();
    private final Map<UUID, PendingCast> pending = new HashMap<>();
    private final List<Permission> ownedPermissions = new ArrayList<>();
    private final Map<UUID, ProbeSession> sessions = new HashMap<>();
    private final Map<UUID, Long> recentlyOwned = new LinkedHashMap<>();
    private @Nullable MyScheduledTask task;
    private long ticks;
    private boolean emergencyOff;
    private int suppressedNativeCatches;
    private int unexpectedNativeBites;
    private int completedProbes;
    private long catastrophicTickFailures;
    private String lastCriticalFailure = "NONE";

    private record PendingCast(UUID playerId, UUID hookId, EquipmentSlot hand,
                               ItemStack rodFingerprint, long startTick,
                               Consumer<FishHook> onTakeover) {}

    public SimulatorModule(EvenMoreFish plugin, NativeHookBridge hookBridge, PacketTransport transport) {
        this.plugin = plugin;
        this.modelPolicy = new FishRenderPolicy(packAudience, transport::supportsCustom);
        this.hookBridge = hookBridge;
        this.configs = new SimulatorConfigLoader(plugin);
        this.zones = new SimulatorZoneResolver(plugin);
        this.zoneHolograms = new ZoneHologramService(plugin);
        this.ambient = new AmbientPreviewService(plugin,zones,transport);
        this.catchService = new SimulatorCatchService(plugin,attraction);
        this.effectsConfig = new SimulatorEffectsConfigLoader(plugin);
        this.schoolConfig = new SchoolConfigLoader(plugin);
        this.ecosystemConfig = new EcosystemConfigLoader(plugin);
        this.encounterConfig = new RareEncounterConfigLoader(plugin);
        this.modelConfig = new FishModelConfigLoader(plugin);
        this.cinematicConfig = new PremiumCinematicConfigLoader(plugin);
        this.presentationConfig = new SimulatorPresentationConfigLoader(plugin);
        this.presentation = new FishingPresentationService(plugin);
        this.hudGuiConfig = new HudGuiConfigLoader(plugin);
        this.hudGui = new HudSettingsGui(this);
        this.effects = new SimulatorEffectsService(transport,presentation);
        ambient.particleVisibility(presentation::particles);
        ambient.modelPolicy(modelPolicy);
        effects.modelPolicy(modelPolicy);
    }

    public void start() {
        registerPermissions();
        ambient.register();
        zoneHolograms.register();
        plugin.getServer().getPluginManager().registerEvents(hudGui,plugin);
        reload();
    }

    /** Reload is atomic; a malformed file does not cancel sessions or replace the active snapshot. */
    public boolean reload() {
        // All config candidates must parse before replacing the live Simulator snapshot.
        var nextEffects=effectsConfig.readCandidate();
        var nextPresentation=presentationConfig.readCandidate();
        var nextCinematic=cinematicConfig.readCandidate();
        var nextGui=hudGuiConfig.readCandidate();
        var nextSchool=schoolConfig.readCandidate();
        var nextEcosystem=ecosystemConfig.readCandidate();
        var nextEncounters=encounterConfig.readCandidate();
        var nextModels=modelConfig.readCandidate();
        if(nextEffects==null || nextPresentation==null || nextCinematic==null || nextGui==null ||
            nextSchool==null || nextEcosystem==null || nextEncounters==null || nextModels==null || !configs.reload()) return false;
        invalidateAll(true);
        modelPolicy.configure(nextModels);
        presentation.configure(nextPresentation);
        effects.configure(nextEffects,nextCinematic);
        hudGui.configure(nextGui);
        zones.resetWarnings();
        emergencyOff = false;
        ambient.configure(configs.current(),nextSchool,nextEcosystem,nextEncounters);
        zoneHolograms.refresh(configs.current());
        rebuildAiDirector();
        updateTicker();
        return true;
    }

    public void shutdown() {
        stopTicker();
        zoneHolograms.clear();
        ambient.stop();
        modelPolicy.configure(FishModelSettings.disabled());
        invalidateAll(true);
        effects.clear();
        hudGui.closeAll();
        presentation.shutdown();
        aiDirector=null;
        aiSnapshots.clear();
        pending.clear();
        recentlyOwned.clear();
        for (Permission permission : ownedPermissions) {
            plugin.getServer().getPluginManager().removePermission(permission);
        }
        ownedPermissions.clear();
    }

    public @Nullable SimulatorSettings settings() { return configs.current(); }
    public void onCatalogReady() {
        invalidateAll(true);
        ambient.catalogReady();
        zoneHolograms.refresh(configs.current());
        rebuildAiDirector();
        updateTicker();
    }
    public void refreshHolograms() {
        zoneHolograms.refresh(configs.current());
    }
    public boolean aiPreviewActive(){return aiDirector!=null;}
    public int activeAiSessions(){return aiDirector==null?0:aiDirector.active();}
    public long aiPreviewSuccesses(){return aiDirector==null?0:aiDirector.successes();}
    public long aiPreviewFailures(){return aiDirector==null?0:aiDirector.failures();}
    public long aiPreviewMisses(){return aiDirector==null?0:aiDirector.misses();}
    public boolean rewardBridgeEnabled(){return configs.current()!=null && configs.current().rewardBridge().enabled();}
    public long rewardBridgeDelivered(){return catchService.deliveredCount();}
    public long rewardBridgeRejected(){return catchService.rejectedCount();}
    public long rewardBridgeFailed(){return catchService.failedDeliveryCount();}
    public boolean effectsEnabled(){return effects.enabled();}
    public int activeCinematics(){return effects.activeCinematics();}
    public int activeHud(){return effects.activeHud();}
    public long cinematicPackets(){return effects.packetsSent();}
    public long cinematicPacketFailures(){return effects.packetFailures();}
    public int cinematicPendingDestroys(){return effects.cinematicPendingDestroys();}
    public long cinematicRejected(){return effects.cinematicRejected();}
    public long effectCues(){return effects.cueCount();}
    public boolean premiumCinematicsEnabled(){return effects.premiumEnabled();}
    public long premiumCinematicsPlayed(){return effects.premiumScenes();}
    public long premiumCinematicCueFailures(){return effects.stageCueFailures();}
    public long premiumStageCues(){return effects.stageCues();}
    public long premiumTrailEmissions(){return effects.trailEmissions();}
    /** Only for staff preview. Requires a real water ray trace and never creates a reward. */
    public boolean premiumCinematicPreview(Player player, Location water, String preset){
        if(emergencyOff || configs.current()==null || !configs.current().enabled() || !ambient.running())return false;
        return effects.preview(player,water,preset,ticks);
    }
    public void stopOwnCinematic(UUID playerId){
        effects.stopCinematic(playerId);
        if(plugin.getCatchCinematics()!=null)plugin.getCatchCinematics().stop(playerId);
    }
    public void setOwnEffects(UUID playerId, boolean enabled){
        effects.setEffectsEnabled(playerId,enabled);
        presentation.mute(playerId,!enabled);
        if(!enabled && plugin.getCatchCinematics()!=null)plugin.getCatchCinematics().stop(playerId);
    }
    public boolean ownEffectsEnabled(UUID playerId){return effects.effectsEnabledFor(playerId);}
    public void setOwnHudProfile(UUID playerId,UiProfile profile){
        presentation.changeProfile(playerId,profile);
        if(!profile.cinematic()) effects.cancel(playerId); // Immediately stop an active packet cinematic.
        if(!profile.cinematic() && plugin.getCatchCinematics()!=null)plugin.getCatchCinematics().stop(playerId);
    }
    public UiProfile ownHudProfile(UUID playerId){ return presentation.profile(playerId); }
    public void prefetchHud(UUID playerId){presentation.prefetch(playerId);}
    public boolean openHudGui(Player player){return hudGui.open(player);}
    public int openHudMenus(){return hudGui.openSessions();}
    public FishPackAudience modelPackAudience(){return packAudience;}
    public boolean customModelsEnabled(){return modelPolicy.settings().enabled();}
    public int customModelsReadyViewers(){return packAudience.readyCount();}
    public long modelReplacements(){return ambient.modelReplacements();}
    public long hudStaleDrops(){return presentation.blockedStale();}
    public long hudPriorityDrops(){return presentation.blockedLower();}
    public long hudMessagesSent(){return presentation.sent();}
    public long hudStorageFailures(){return presentation.asyncFailures();}

    private void rebuildAiDirector(){
        if(aiDirector!=null)aiDirector.clear();
        aiSnapshots.clear();aiDirector=null;
        SimulatorSettings config=configs.current();
        if(config==null || !config.enabled() || !config.aiPreview().enabled() || !ambient.running()
            || !hookBridge.supported())return;
        var cfg=config.aiPreview();
        aiDirector=new FishAiDirector(ambient.population(),new FishAiDirector.WaterNavigation(){
            @Override public boolean valid(WaterPoint cell){return cell!=null && ambient.water()!=null
                && ambient.water().isIndexedWater(cell);}
            @Override public List<WaterPoint> adjacent(WaterPoint cell){return ambient.water()==null
                ?List.of():ambient.water().adjacent(cell);}
            @Override public boolean traversable(WaterPoint cell,String zoneId){
                if(!valid(cell))return false;
                var world=Bukkit.getWorld(cell.worldId());
                if(world==null || !world.isChunkLoaded(cell.chunkX(),cell.chunkZ()))return false;
                var match=zones.resolve(new Location(world,cell.x()+.5,cell.y()+.32,cell.z()+.5),
                    configs.current());
                return match.found() && !match.denied() && match.zone().id().equals(zoneId);
            }
        },new FishAiDirector.Tuning(cfg.maxActive(),cfg.candidatesPerSearch(),cfg.maxPathNodes(),
            cfg.searchIntervalTicks(),cfg.interestTicks(),cfg.reactionWindowTicks(),cfg.graceTicks(),
            cfg.maxRetargets(),cfg.castCooldownTicks(),cfg.maxWaitTicks(),cfg.attractionRadius(),
            cfg.swimSpeed(),cfg.biteRadius()));
    }
    public AmbientPreviewService ambient() { return ambient; }
    /** Visual-only: preview can only attach to an existing rendered, eligible fish. */
    public boolean previewOwnEncounter(Player player,RareEncounterDirector.Kind kind) {
        if(player==null || !player.isOnline() || emergencyOff || !ambient.running())return false;
        return ambient.previewEncounter(player,kind,ticks);
    }
    public boolean emergencyOff() { return emergencyOff; }
    public boolean hookBridgeSupported() { return hookBridge.supported(); }
    public int activeProbes() { return sessions.size(); }
    public int pendingCasts() { return pending.size(); }
    public int suppressedNativeCatches() { return suppressedNativeCatches; }
    public int unexpectedNativeBites() { return unexpectedNativeBites; }
    public int completedProbes() { return completedProbes; }
    public long catastrophicTickFailures() { return catastrophicTickFailures; }
    public String lastCriticalFailure() { return lastCriticalFailure; }
    public List<String> recentTrace() { return List.copyOf(trace); }
    private void trace(UUID player, UUID hook, String phase) {
        if (trace.size() >= 128) trace.removeFirst();
        trace.addLast("tick=" + ticks + " player=" + player + " hook=" + hook + " event=" + phase);
    }
    public SimulatorZoneResolver zoneResolver() { return zones; }

    public boolean packetEventsPresent() {
        for (org.bukkit.plugin.Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            if (installed.isEnabled() && installed.getName().equalsIgnoreCase("packetevents")) return true;
        }
        return false;
    }

    private void registerPermissions() {
        List<String> adminNodes = List.of(
            "emf.admin.simulator.status", "emf.admin.simulator.zone",
            "emf.admin.simulator.inspect", "emf.admin.simulator.reload",
            "emf.admin.simulator.manage", "emf.admin.simulator.probe",
            "emf.admin.simulator.preview"
        );
        for (String node : adminNodes) registerPermission(node, PermissionDefault.OP);
        registerPermission("emf.simulator.use", PermissionDefault.FALSE);
        registerPermission("emf.simulator.preferences", PermissionDefault.TRUE);
    }

    private void registerPermission(String node, PermissionDefault value) {
        if (plugin.getServer().getPluginManager().getPermission(node) != null) return;
        Permission permission = new Permission(node, value);
        plugin.getServer().getPluginManager().addPermission(permission);
        ownedPermissions.add(permission);
    }

    /** Emergency disable (never enable production engine via runtime command). */
    public void setEmergencyOff(boolean disabled) {
        this.emergencyOff = disabled;
        if (disabled) {
            invalidateAll(true);
            ambient.stop();
            aiDirector=null;aiSnapshots.clear();
            effects.clear();
            presentation.clear();
            stopTicker();
        } else {
            ambient.configure(configs.current());
            rebuildAiDirector();
            updateTicker();
        }
    }

    /** Called only by the existing FishingProcessor — no second catch listener. */
    public boolean onFishEvent(PlayerFishEvent event, Consumer<FishHook> onTakeover,
                               FishingProcessor fishingProcessor) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        UUID hookId = event.getHook().getUniqueId();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            discardPlayerState(playerId, true);
            if (event.isCancelled()) return false;
            SimulatorSettings config = configs.current();
            if (config == null || !config.enabled() || emergencyOff) return false;
            // Native treasure and bait-loot routes do not have packet agents yet.
            // Preserve the old EMF catch behavior rather than silently losing loot.
            if(config.rewardBridge().enabled() && SimulatorCatchService.legacyLootCompatibilityRequired())return false;
            if (!config.probe().enabled() && config.onUnavailable() == SimulatorSettings.UnavailablePolicy.LEGACY_EMF) return false;
            if (!worldMayMatch(event.getHook().getWorld().getName(), config)) return false;
            if (pending.size() >= MAX_PENDING_CASTS) return false;
            EquipmentSlot hand = event.getHand();
            if (hand == null) return false;
            ItemStack rod = player.getInventory().getItem(hand);
            if (!eligible(player, rod, player.getLocation(), config, false)) return false;
            pending.put(playerId, new PendingCast(playerId, hookId, hand, fingerprint(rod), ticks, onTakeover));
            return false; // Legacy EMF still handles the cast until a landed hook is explicitly taken over.
        }

        ProbeSession session = sessions.get(playerId);
        if (session == null || !session.hookId().equals(hookId)) {
            PendingCast p = pending.get(playerId);
            if (p != null && p.hookId().equals(hookId) &&
                (isTerminal(event.getState()) || event.getState() == PlayerFishEvent.State.BITE)) {
                pending.remove(playerId);
            }
            // Tombstone guards late CAUGHT_FISH after probe cleanup (never grant legacy loot).
            if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && recentlyOwned.containsKey(hookId)) {
                stopNativeLoot(event);
                return true;
            }
            return false;
        }

        trace(playerId, hookId, event.getState().name() + " canceledOnEntry=" + event.isCancelled());
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            stopNativeLoot(event);
            suppressedNativeCatches++;
            endSession(playerId, true);
            return true;
        }
        if (event.isCancelled()) {
            endSession(playerId, true);
            return true;
        }
        switch (event.getState()) {
            case BITE -> {
                // Native state was not adequately suppressed: stop without granting anything.
                unexpectedNativeBites++;
                event.setCancelled(true);
                endSession(playerId, true);
                plugin.getLogger().warning("[Simulator] Native BITE escaped probe for " + playerId + "; hook removed.");
                return true;
            }
            case REEL_IN, FAILED_ATTEMPT, IN_GROUND -> {
                // Suppress any native experience; Simulator's XP is delivered
                // only after its own item transaction has completed.
                event.setExpToDrop(0);
                // Phase 7 may issue a fish ONLY behind the admin runtime gate.
                if(aiDirector!=null && aiDirector.view(playerId)!=null){
                    SimulatorSettings config=configs.current();
                    var snapshot=aiSnapshots.get(playerId);
                    boolean bridge=config!=null && config.rewardBridge().enabled() &&
                        config.rewardBridge().paperGatesVerified() && snapshot!=null &&
                        event.getState()==PlayerFishEvent.State.REEL_IN &&
                        player.hasPermission("emf.admin.simulator.preview") &&
                        player.hasPermission("emf.admin.simulator.probe");
                    if(bridge){
                        // No client packet may initiate this: only a real server fish event.
                        var catchView=aiDirector.view(playerId);
                        var result=aiDirector.reelWithCommit(playerId,hookId,ticks,aiFeedback,
                            (view,fish)->catchService.prepare(event,session,view,fish,snapshot,
                                fishingProcessor,config.rewardBridge(),
                                ()->validateRewardReel(event,session,config)));
                        if(result==FishAiDirector.Outcome.SUCCESS_COMMITTED && catchView!=null)
                            ambient.encounterCommitted(catchView.fishId());
                    } else aiDirector.reel(playerId,hookId,ticks,aiFeedback);
                } else completedProbes++;
                endSession(playerId, true);
                return true;
            }
            case CAUGHT_ENTITY -> {
                endSession(playerId, false); // Preserve native entity-hook semantics.
                return true;
            }
            default -> { return true; }
        }
    }

    /** Strict owner-thread recheck before and after the cancellable EMF event. */
    private boolean validateRewardReel(PlayerFishEvent event,ProbeSession session,SimulatorSettings config) {
        Player player=event.getPlayer();
        if(!plugin.isEnabled() || emergencyOff || !config.enabled() ||
            configs.current()!=config || config.revision()!=session.revision() ||
            !config.rewardBridge().enabled() || !config.rewardBridge().paperGatesVerified() ||
            event.isCancelled() || event.getState()!=PlayerFishEvent.State.REEL_IN ||
            !player.isOnline() || sessions.get(player.getUniqueId())!=session || session.isClosed() ||
            !session.hookId().equals(event.getHook().getUniqueId()) ||
            !player.hasPermission("emf.admin.simulator.probe") ||
            !player.hasPermission("emf.admin.simulator.preview") ||
            SimulatorCatchService.legacyLootCompatibilityRequired())return false;
        FishHook hook=getHook(session.hookId());
        if(hook==null || !hook.isValid() || !nearEnough(player,hook.getLocation(),config.probe().maxReelDistance()))return false;
        ItemStack rod=player.getInventory().getItem(session.castHand());
        if(!fingerprint(rod).isSimilar(session.rodFingerprint()) ||
            !eligible(player,rod,hook.getLocation(),config,true))return false;
        SimulatorZoneResolver.Match zone=zones.resolve(hook.getLocation(),config);
        return zone.found() && !zone.denied() && zone.zone().id().equals(session.zoneId());
    }

    public void quit(UUID playerId) {
        discardPlayerState(playerId, true);
        effects.quit(playerId);
        presentation.quit(playerId);
    }

    private static boolean isTerminal(PlayerFishEvent.State state) {
        return switch (state) {
            case CAUGHT_FISH, CAUGHT_ENTITY, REEL_IN, FAILED_ATTEMPT, IN_GROUND -> true;
            default -> false;
        };
    }

    private static void stopNativeLoot(PlayerFishEvent event) {
        event.setExpToDrop(0);
        event.setCancelled(true);
        if (event.getCaught() instanceof Item item && item.isValid()) item.remove();
    }

    private void tick() {
        try { tickUnchecked(); }
        catch (RuntimeException | LinkageError error) { tripCritical(error); }
    }

    /** All Bukkit, region, native-hook and packet calls stay on the Paper owner thread. */
    private void tickUnchecked() {
        SimulatorSettings config = configs.current();
        if (config == null || emergencyOff || !config.enabled()) return;
        ticks += tickerPeriod;
        if(aiDirector!=null && !ambient.running()){
            invalidateAll(true);aiDirector=null;aiSnapshots.clear();
        }
        recentlyOwned.entrySet().removeIf(e -> e.getValue() <= ticks);
        Iterator<Map.Entry<UUID, PendingCast>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingCast candidate = iterator.next().getValue();
            if (ticks - candidate.startTick() >= config.probe().settleTicks()) {
                iterator.remove();
                continue;
            }
            Player player = Bukkit.getPlayer(candidate.playerId());
            FishHook hook = getHook(candidate.hookId());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            if (hook == null) continue; // Hook might not yet be registered for the first tick.
            if (!(hook.getShooter() instanceof Player shooter) ||
                !shooter.getUniqueId().equals(candidate.playerId())) {
                iterator.remove();
                continue;
            }
            if (hook.getState() != FishHook.HookState.BOBBING || !isWaterAt(hook.getLocation())) continue;
            iterator.remove();
            Location water = hook.getLocation();
            SimulatorZoneResolver.Match match = zones.resolve(water, config);
            if (!match.found() || match.denied()) continue; // Legacy gets the cast.
            ItemStack rod = player.getInventory().getItem(candidate.hand());
            if (!fingerprint(rod).isSimilar(candidate.rodFingerprint()) ||
                !eligible(player, rod, water, config, true) ||
                !nearEnough(player, water, config.probe().maxReelDistance())) continue;

            // Phase 7 remains strictly admin-only, even when the reward bridge is
            // configured: all other casts keep the original EMF logic.
            boolean wantsAi=aiDirector!=null && player.hasPermission("emf.admin.simulator.preview");
            if(config.rewardBridge().enabled() && SimulatorCatchService.legacyLootCompatibilityRequired())continue;
            boolean allowedProbe = config.probe().enabled() && hookBridge.supported()
                && player.hasPermission("emf.admin.simulator.probe") && sessions.size() < config.probe().maxActive()
                && (!wantsAi || aiDirector.canBegin(candidate.playerId(),ticks));
            if (allowedProbe && hookBridge.suppressBite(hook, config.probe().waitTicks())) {
                trace(player.getUniqueId(), hook.getUniqueId(), "PROBE_TAKEOVER zone=" + match.zone().id());
                sessions.put(player.getUniqueId(), new ProbeSession(player.getUniqueId(),
                    hook.getUniqueId(), match.zone().id(), config.revision(), ticks, candidate.hand(), candidate.rodFingerprint()));
                // Ownership callback may involve legacy hologram teardown; abort
                // instead of leaving a hook suppressed with an incomplete session.
                try {
                    candidate.onTakeover().accept(hook);
                    if(wantsAi){
                        if(!aiDirector.begin(player.getUniqueId(),hook.getUniqueId(),match.zone().id(),
                            water.getWorld().getUID(),ticks)){
                            trace(player.getUniqueId(),hook.getUniqueId(),"AI_BEGIN_REJECTED");
                            endSession(player.getUniqueId(),true);
                        } else {
                            presentation.begin(player.getUniqueId(),hook.getUniqueId());
                            aiSnapshots.put(player.getUniqueId(),attraction.capture(rod));
                            ambient.indexHook(water);
                            ambient.lureAt(water,player.getUniqueId(),ticks);
                            trace(player.getUniqueId(),hook.getUniqueId(),
                                config.rewardBridge().enabled() ? "AI_START_ADMIN_REWARD_GATE" : "AI_START_NO_REWARDS");
                        }
                    }
                } catch(RuntimeException exception){
                    plugin.getLogger().warning("[Simulator] AI preview initialization failed: " + exception.getMessage());
                    endSession(player.getUniqueId(),true);
                }
            } else if (config.onUnavailable() == SimulatorSettings.UnavailablePolicy.DENY) {
                // Explicit admin policy, not an automatic fallback. Deny only after zone confirmation.
                candidate.onTakeover().accept(hook);
                trace(player.getUniqueId(), hook.getUniqueId(), "UNAVAILABLE_DENY");
                hook.remove();
                SimulatorMessages.send(player, "unavailable-deny");
            }
        }

        for (ProbeSession session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId());
            FishHook hook = getHook(session.hookId());
            if (player == null || hook == null || !player.isOnline()
                || config.revision() != session.revision()
                || ticks - session.startTick() > config.probe().maxSessionTicks()
                || !nearEnough(player, hook.getLocation(), config.probe().maxReelDistance())) {
                endSession(session.playerId(), true);
                continue;
            }
            SimulatorZoneResolver.Match matched = zones.resolve(hook.getLocation(), config);
            if (!matched.found() || matched.denied() || !matched.zone().id().equals(session.zoneId())
                || !fingerprint(player.getInventory().getItem(session.castHand())).isSimilar(session.rodFingerprint())
                || !player.hasPermission("emf.admin.simulator.probe")
                || !eligible(player, player.getInventory().getItem(session.castHand()), hook.getLocation(), config, true)) {
                endSession(session.playerId(), true);
                continue;
            }
            // Continuously re-arm in the diagnostic window and fail closed on error.
            if (!hookBridge.suppressBite(hook, config.probe().waitTicks())) {
                endSession(session.playerId(), true);
                plugin.getLogger().warning("[Simulator] Native wait control lost for " + session.playerId());
            }
        }
        // Advance the AI after water scanning and ambient movement, before the packet sync.
        // If a provider crashes at ANY point in this owner tick, the outer
        // circuit-breaker closes all sessions rather than silently dying.
        ambient.tick(ticks,()->tickAi(ticks));
        // Bounded FX render belongs to the same owner tick; no async Bukkit operations.
        // A packet FX failure must never cancel the main Simulator repeating task.
        try {
            effects.tick(ticks,aiDirector);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("[Simulator] Visual layer suspended after failure: " + error.getMessage());
            effects.clear();
        }
        try { presentation.tick(ticks); }
        catch (RuntimeException error) {
            plugin.getLogger().warning("[Simulator] HUD layer suspended after failure: " + error.getMessage());
            presentation.clear();
        }
    }

    private void tickAi(long now){
        FishAiDirector director=aiDirector;
        if(director==null || !ambient.running())return;
        for(ProbeSession session:List.copyOf(sessions.values())){
            UUID playerId=session.playerId();
            if(director.view(playerId)==null)continue;
            Player player=Bukkit.getPlayer(playerId);
            FishHook hook=getHook(session.hookId());
            var snapshot=aiSnapshots.get(playerId);
            if(player==null || hook==null || snapshot==null){endSession(playerId,true);continue;}
            Location hookLocation=hook.getLocation();
            if(director.view(playerId).goal()==null && now%5==0) {
                WaterPoint nearest=ambient.nearestHookWater(hookLocation,session.zoneId());
                if(nearest==null && now%20==0)ambient.indexHook(hookLocation);
                else director.setGoal(playerId,session.hookId(),nearest);
            }
            try {
                director.tick(playerId,now,
                    fish->attraction.attraction(player,hookLocation,snapshot,fish),aiFeedback);
            } catch(RuntimeException exception){
                // A bad external requirements provider must not break the central ticker.
                plugin.getLogger().warning("[Simulator] AI preview cancelled safely: "+exception.getMessage());
                endSession(playerId,true);
            }
        }
    }

    private boolean eligible(Player player, ItemStack rod, Location location, SimulatorSettings config, boolean atHook) {
        if (rod == null || !Checks.canUseRod(rod) || !MainConfig.getInstance().getEnabled()
            || !MainConfig.getInstance().isCatchEnabled()
            || plugin.getToggle().isCustomFishingDisabled(player)) return false;
        if (config.permissionRequired() && !player.hasPermission("emf.simulator.use")) return false;
        if (MainConfig.getInstance().requireFishingPermission() && !player.hasPermission(UserPerms.USE_ROD)) return false;
        Competition active = Competition.getCurrentlyActive();
        if (active != null && !active.getCompetitionFile().isAllowFishing()
            && !plugin.isRaritiesCompCheckExempt()) return false;
        if (active == null && MainConfig.getInstance().isFishCatchOnlyInCompetition()
            && !plugin.isRaritiesCompCheckExempt()) return false;
        if (!FishUtils.checkWorld(location)) return false;
        if (atHook) {
            var allowedRegions = MainConfig.getInstance().getAllowedRegions();
            // Unlike legacy EMF's historical fallback, Simulator must not ignore a
            // protection whitelist just because its external provider is missing.
            if (!allowedRegions.isEmpty() &&
                !plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard") &&
                !plugin.getServer().getPluginManager().isPluginEnabled("RedProtect")) return false;
            if (!FishUtils.checkRegion(location, allowedRegions) || Checks.isMcMMOOverfishing(player)) return false;
        }
        return true;
    }

    private static boolean nearEnough(Player player, Location hook, double distance) {
        return player.getWorld().equals(hook.getWorld()) &&
            player.getLocation().distanceSquared(hook) <= distance * distance;
    }
    private static boolean isWaterAt(Location loc) {
        return loc.getBlock().getType() == Material.WATER ||
            loc.clone().add(0, -0.35, 0).getBlock().getType() == Material.WATER;
    }
    private static ItemStack fingerprint(@Nullable ItemStack stack) {
        ItemStack clone = stack == null ? new ItemStack(Material.AIR) : stack.clone();
        if (clone.getItemMeta() instanceof Damageable damage) {
            damage.setDamage(0);
            clone.setItemMeta(damage);
        }
        return clone;
    }
    private static @Nullable FishHook getHook(UUID uuid) {
        Entity e = Bukkit.getEntity(uuid);
        return e instanceof FishHook hook && hook.isValid() ? hook : null;
    }
    private static boolean worldMayMatch(String world, SimulatorSettings config) {
        return config.zones().stream().anyMatch(zone -> zone.isInWorld(world));
    }
    private void endSession(UUID playerId, boolean removeHook) {
        effects.clearHud(playerId); // A successful cinematic may outlive its catch session.
        aiSnapshots.remove(playerId);
        if(aiDirector!=null)aiDirector.abort(playerId);
        ProbeSession session = sessions.remove(playerId);
        if (session == null || !session.close()) return;
        presentation.closeSession(playerId,session.hookId());
        trace(session.playerId(), session.hookId(), "SESSION_CLOSE removeHook=" + removeHook);
        // Never clear all tombstones at the capacity edge: that reopens an
        // unsafe window for stale CAUGHT_FISH after a rapid series of casts.
        if (recentlyOwned.size() >= MAX_TOMBSTONES) {
            Iterator<UUID> oldest=recentlyOwned.keySet().iterator();
            if (oldest.hasNext()) { oldest.next(); oldest.remove(); }
        }
        recentlyOwned.put(session.hookId(), ticks + TOMBSTONE_TICKS);
        if (removeHook) {
            FishHook hook = getHook(session.hookId());
            if (hook != null) hook.remove();
        }
    }
    private void discardPlayerState(UUID player, boolean removeHook) {
        effects.cancel(player); // A new cast replaces a stale cinematic for that viewer.
        presentation.cancel(player);
        pending.remove(player);
        endSession(player, removeHook);
    }
    private void invalidateAll(boolean removeHooks) {
        effects.clear();
        presentation.clear();
        pending.clear();
        for (UUID uuid : List.copyOf(sessions.keySet())) endSession(uuid, removeHooks);
    }
    private void tripCritical(Throwable error) {
        catastrophicTickFailures++;
        lastCriticalFailure=error.getClass().getSimpleName();
        plugin.getLogger().severe("[Simulator] Preview ticker failed closed (" + lastCriticalFailure + "): " + error.getMessage());
        emergencyOff=true;
        stopTicker();
        try { invalidateAll(true); }
        catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().severe("[Simulator] Native hook cleanup failed: " + failure.getMessage());
        }
        try { ambient.stop(); }
        catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().severe("[Simulator] Packet visual cleanup failed: " + failure.getMessage());
        }
        try { effects.clear(); }
        catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().severe("[Simulator] FX cleanup failed: " + failure.getMessage());
        }
        aiDirector=null;
        aiSnapshots.clear();
        pending.clear();
    }

    private void stopTicker() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
    private void updateTicker() {
        stopTicker();
        SimulatorSettings settings = configs.current();
        if (settings != null && settings.enabled() &&
            (settings.probe().enabled() || settings.onUnavailable() == SimulatorSettings.UnavailablePolicy.DENY
                || ambient.running())) {
            tickerPeriod = ambient.running() ? 1 : settings.probe().pollTicks();
            task = EvenMoreFish.getScheduler().runTaskTimer(plugin, this::tick,
                tickerPeriod, tickerPeriod);
        }
    }
}
