package com.oheers.fish.fishing.simulator;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.simulator.SimulatorSettings;
import com.oheers.fish.fishing.simulator.catalog.FishCatalogAdapter;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishPopulationService;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.population.WaterPoint;
import com.oheers.fish.fishing.simulator.visual.PacketEntityIdAllocator;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.fishing.simulator.model.FishRenderPolicy;
import com.oheers.fish.fishing.simulator.visual.PacketVisibilityService;
import com.oheers.fish.fishing.simulator.visual.VisualMappingConfig;
import com.oheers.fish.fishing.simulator.water.WaterIndexService;
import com.oheers.fish.fishing.simulator.school.FishSchoolService;
import com.oheers.fish.fishing.simulator.school.SchoolSettings;
import com.oheers.fish.fishing.simulator.environment.EcosystemSettings;
import com.oheers.fish.fishing.simulator.environment.EnvironmentSampler;
import com.oheers.fish.fishing.simulator.environment.EnvironmentSnapshot;
import com.oheers.fish.fishing.simulator.behavior.FishPersonalityService;
import com.oheers.fish.fishing.simulator.waterfx.SurfaceFxService;
import org.bukkit.Particle;
import org.bukkit.Color;
import com.oheers.fish.fishing.simulator.encounter.RareEncounterSettings;
import com.oheers.fish.fishing.simulator.encounter.RareEncounterDirector;
import com.oheers.fish.fishing.simulator.zones.SimulatorZoneResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Phase 4/5: admin-only ambient fish preview. Zero reward and zero PlayerFishEvent hooks.
 * Runs exclusively on Paper's main thread. Packet backend is an optional, versioned adapter.
 * Not Folia-safe until all cross-region ownership is redesigned.
 */
public final class AmbientPreviewService implements Listener {
    private static final String PREVIEW_PERMISSION="emf.admin.simulator.preview";
    // Core EMF compiles against Paper 1.20.1; enum names for dust/enchant were
    // renamed in 1.20.5+. Resolve ONCE by name to preserve both binary targets.
    private static final Particle ENCOUNTER_DUST=resolveParticle("DUST","REDSTONE",Particle.END_ROD);
    private static final Particle ENCOUNTER_ENCHANT=resolveParticle("ENCHANT","ENCHANTMENT_TABLE",Particle.END_ROD);
    private static final Particle WATER_BUBBLE=resolveParticle("BUBBLE","WATER_BUBBLE",Particle.END_ROD);
    private static final Particle WATER_SPLASH=resolveParticle("SPLASH","WATER_SPLASH",Particle.END_ROD);
    private static final Particle WATER_WAKE=resolveParticle("FISHING","WATER_WAKE",Particle.END_ROD);
    private static Particle resolveParticle(String modern,String legacy,Particle fallback){
        try {return Particle.valueOf(modern);}
        catch(IllegalArgumentException ignored){
            try{return Particle.valueOf(legacy);}
            catch(IllegalArgumentException missing){return fallback;}
        }
    }
    private static final long WATER_FLOW_DEBOUNCE_TICKS=40;
    private static final int MAX_FLOW_DEBOUNCE_ENTRIES=512;
    private final EvenMoreFish plugin;
    private final SimulatorZoneResolver resolver;
    private final PacketTransport packets;
    private FishRenderPolicy modelPolicy;
    private SimulatorSettings settings;
    private WaterIndexService water;
    private FishPopulationService population;
    private PacketVisibilityService visibility;
    private SchoolSettings schoolSettings = SchoolSettings.disabled();
    private FishSchoolService schooling;
    private EcosystemSettings ecosystemSettings = EcosystemSettings.disabled();
    private EnvironmentSampler environment;
    private FishPersonalityService personality;
    private SurfaceFxService waterFx;
    private RareEncounterSettings encounterSettings = RareEncounterSettings.disabled();
    private RareEncounterDirector encounters;
    private Predicate<UUID> playerParticles = id -> true;
    // A backend outage can leave virtual entity IDs on still-connected clients.
    // Keep the failed cleanup ledger across stop/reload rather than discarding it.
    private PacketVisibilityService retiredVisibility;
    private FishCatalogAdapter catalog;
    private VisualMappingConfig visualMapping;
    private boolean catalogReady;
    private long lastTick;
    private long spawnAttempts;
    private long previewTicks;
    private long cumulativePreviewNanos;
    private long maxPreviewNanos;
    private long overTwoMsTicks;
    // Persist diagnostics across emergency off/reload even when the renderer is gone.
    private long previousPacketCount, previousDeferred, previousFailures, previousDestroyFailures;
    private int historicalPacketPeak;
    private List<UUID> previewViewerCache=List.of();
    private long lastViewerScanTick=-1;
    // High-volume BlockFromToEvent must not reindex the same waterfall every tick.
    private final Map<WaterIndexService.ChunkKey,Long> flowInvalidationTimes = new LinkedHashMap<>();

    public AmbientPreviewService(EvenMoreFish plugin,SimulatorZoneResolver resolver,PacketTransport packets){
        this.plugin=plugin;this.resolver=resolver;this.packets=packets;
    }
    public void register(){plugin.getServer().getPluginManager().registerEvents(this,plugin);}
    public void modelPolicy(FishRenderPolicy policy){this.modelPolicy=policy;}
    public long modelReplacements(){return visibility==null?0:visibility.modelReplacements();}
    /** Player visual preference is read only on the owner thread. */
    public void particleVisibility(Predicate<UUID> predicate){playerParticles=java.util.Objects.requireNonNull(predicate);}
    /** Must be called after FishManager has loaded its catalog on onEnable(). */
    public void catalogReady(){
        catalog=new FishCatalogAdapter();
        catalogReady=catalog.count()>0;
        if(!catalogReady)plugin.getLogger().warning("[Simulator] Ambient preview disabled: no catchable EMF catalog entries");
        if(settings!=null) configure(settings);
    }
    public void configure(SimulatorSettings next) { configure(next,schoolSettings,ecosystemSettings); }

    public void configure(SimulatorSettings next,SchoolSettings schoolCandidate) {
        configure(next,schoolCandidate,ecosystemSettings,encounterSettings);
    }
    public void configure(SimulatorSettings next,SchoolSettings schoolCandidate,EcosystemSettings ecosystemCandidate) {
        configure(next,schoolCandidate,ecosystemCandidate,encounterSettings);
    }
    public void configure(SimulatorSettings next,SchoolSettings schoolCandidate,EcosystemSettings ecosystemCandidate,
                          RareEncounterSettings encounterCandidate) {
        schoolSettings=java.util.Objects.requireNonNull(schoolCandidate);
        ecosystemSettings=java.util.Objects.requireNonNull(ecosystemCandidate);
        encounterSettings=java.util.Objects.requireNonNull(encounterCandidate);
        // This is only invoked after a VALID, atomically loaded config snapshot.
        stop();
        retryRetiredCleanup();
        settings=next;
        SimulatorSettings.Ambient cfg=next.ambient();
        if(!next.enabled() || !cfg.enabled() || !catalogReady)return;
        if(retiredVisibility!=null) {
            plugin.getLogger().severe("[Simulator] Preview refused: " +
                retiredVisibility.pendingDestroys() + " virtual entity IDs still await cleanup. " +
                "Reconnect affected clients, then restart staging with healthy PacketEvents; config reload cannot reset a tripped transport.");
            return;
        }
        if(!packets.available()){
            plugin.getLogger().warning("[Simulator] Ambient preview requested but PacketEvents backend unavailable; legacy EMF untouched");
            return;
        }
        water=new WaterIndexService(cfg.maxCachedChunks(),cfg.maxQueuedChunks(),cfg.maxCachedCells(),cfg.minimumWaterDepth());
        schooling=new FishSchoolService(schoolSettings);
        if(ecosystemSettings.enabled()) {
            if(ecosystemSettings.samplingEnabled()) {
                environment=new EnvironmentSampler(ecosystemSettings.samples(),this::readEnvironment);
            }
            if(ecosystemSettings.personalityEnabled()) {
                personality=new FishPersonalityService(ecosystemSettings.rarityProfiles(),
                    ecosystemSettings.fishOverrides(),ecosystemSettings.shyRadius());
            }
            if(ecosystemSettings.waterFxEnabled())waterFx=new SurfaceFxService(ecosystemSettings.fx());
        }
        population=new FishPopulationService(cfg.maxAgents(),cfg.maxPerZone(),cfg.maxPrivate(),cfg.maxPrivatePerViewer());
        if(encounterSettings.enabled())encounters=new RareEncounterDirector(encounterSettings,ThreadLocalRandom.current());
        visibility=new PacketVisibilityService(packets,cfg.maxVisiblePerViewer(),cfg.packetBudgetPerTick(),cfg.viewDistance(),modelPolicy);
        visualMapping=VisualMappingConfig.load(plugin);
        plugin.getLogger().info("[Simulator] Admin ambient fish preview ready; no Simulator rewards/catch gameplay enabled");
    }
    public boolean running(){return water!=null && population!=null && visibility!=null;}
    public void stop(){
        if(visibility!=null){
            // Capture before clearing so a failed backend cannot erase evidence.
            visibility.clearBestEffort();
            previousPacketCount+=visibility.packetsSent();
            previousDeferred+=visibility.packetsDeferred();
            previousFailures+=visibility.failures();
            previousDestroyFailures+=visibility.destroyFailures();
            historicalPacketPeak=Math.max(historicalPacketPeak,visibility.peakPacketUsage());
            if(visibility.pendingDestroys()>0) {
                retiredVisibility=visibility;
                plugin.getLogger().warning("[Simulator] " + visibility.pendingDestroys() +
                    " virtual entity IDs still need client cleanup after preview stop");
            }
        }
        if(schooling!=null)schooling.clear();
        if(environment!=null)environment.clear();
        if(waterFx!=null)waterFx.clear();
        if(encounters!=null)encounters.clear();
        if(population!=null)population.clear();
        if(water!=null)water.clear();
        water=null;population=null;visibility=null;visualMapping=null;schooling=null;
        environment=null;personality=null;waterFx=null;encounters=null;
        previewViewerCache=List.of();
        lastViewerScanTick=-1;
        flowInvalidationTimes.clear();
    }
    private void retryRetiredCleanup(){
        if(retiredVisibility==null)return;
        long beforeSent=retiredVisibility.packetsSent();
        long beforeFailures=retiredVisibility.failures();
        long beforeDestroyFailures=retiredVisibility.destroyFailures();
        retiredVisibility.clearBestEffort();
        previousPacketCount+=retiredVisibility.packetsSent()-beforeSent;
        previousFailures+=retiredVisibility.failures()-beforeFailures;
        previousDestroyFailures+=retiredVisibility.destroyFailures()-beforeDestroyFailures;
        if(retiredVisibility.pendingDestroys()==0)retiredVisibility=null;
    }
    public boolean schoolingEnabled(){return schooling!=null && schooling.enabled();}
    public int schools(){return schooling==null?0:schooling.schools();}
    public int schoolMembers(){return schooling==null?0:schooling.members();}
    public long schoolDecisions(){return schooling==null?0:schooling.decisions();}
    public long schoolNeighborChecks(){return schooling==null?0:schooling.neighborChecks();}
    public long schoolBudgetHits(){return schooling==null?0:schooling.budgetExhaustions();}
    public long schoolScatters(){return schooling==null?0:schooling.scatters();}
    public boolean ecosystemEnabled(){return ecosystemSettings.enabled() && running();}
    public int environmentCache(){return environment==null?0:environment.cached();}
    public long environmentReads(){return environment==null?0:environment.reads();}
    public long environmentDrops(){return environment==null?0:environment.budgetDrops();}
    public long environmentFailures(){return environment==null?0:environment.failedReads();}
    public long personalityScatters(){return personality==null?0:personality.shyScatters();}
    public long waterFxParticles(){return waterFx==null?0:waterFx.particles();}
    public long waterFxDrops(){return waterFx==null?0:waterFx.dropped();}
    /** Cosmetic only, initiated after the rod is safely taken over for admin preview. */
    public void lureAt(Location location,UUID owner,long tick){signalAt(location,owner,tick,SurfaceFxService.Kind.LURE_PULSE);}
    public void biteAt(Location location,UUID owner,long tick){signalAt(location,owner,tick,SurfaceFxService.Kind.BITE_DISTURBANCE);}
    private void signalAt(Location location,UUID owner,long tick,SurfaceFxService.Kind kind) {
        if(waterFx==null || location==null || location.getWorld()==null)return;
        var match=resolver.resolve(location,settings);
        if(!match.found()||match.denied())return;
        waterFx.signal(owner,location.getWorld().getUID(),match.zone().id(),
            new Vec3(location.getX(),location.getY(),location.getZ()),kind,tick);
    }
    public void disturbAt(Location location,UUID owner,long tick) {
        if(!running() || location==null || location.getWorld()==null)return;
        var match=resolver.resolve(location,settings);
        if(!match.found() || match.denied())return;
        Vec3 point=new Vec3(location.getX(),location.getY(),location.getZ());
        if(schooling!=null && schooling.enabled()) {
            schooling.scatter(point,location.getWorld().getUID(),match.zone().id(),null,population,tick);
            // Only triggering owner can disturb their private school.
            schooling.scatter(point,location.getWorld().getUID(),match.zone().id(),owner,population,tick);
        }
        if(personality!=null)personality.disturb(point,location.getWorld().getUID(),match.zone().id(),
            owner,population,behaviorNavigation());
        signalAt(location,owner,tick,SurfaceFxService.Kind.BITE_DISTURBANCE);
    }
    public boolean encountersEnabled(){return encounters!=null && encounters.enabled();}
    public int activeEncounters(){return encounters==null?0:encounters.active();}
    public long encounterSpawns(){return encounters==null?0:encounters.spawned();}
    public long encounterObservations(){return encounters==null?0:encounters.observations();}
    public long encounterExpires(){return encounters==null?0:encounters.expired();}
    public long encounterCompleted(){return encounters==null?0:encounters.completed();}
    public long encounterParticles(){return encounters==null?0:encounters.particles();}
    public long encounterFxDrops(){return encounters==null?0:encounters.drops();}
    public long encounterFxFailures(){return encounters==null?0:encounters.failures();}
    public void encounterCommitted(UUID fishId){if(encounters!=null && fishId!=null)encounters.committed(fishId);}
    public boolean previewEncounter(Player player,RareEncounterDirector.Kind kind,long tick){
        if(!running() || encounters==null || kind==null || player==null ||
            !player.isOnline() || !player.hasPermission(PREVIEW_PERMISSION) ||
            !playerParticles.test(player.getUniqueId()))return false;
        var match=resolver.resolve(player.getLocation(),settings);
        if(!match.found() || match.denied())return false;
        Vec3 location=new Vec3(player.getLocation().getX(),player.getLocation().getY(),player.getLocation().getZ());
        // Only target an EXISTING agent which is already rendered to this player.
        for(FishAgent fish:population.agents().stream()
                .filter(a->a.worldId().equals(player.getWorld().getUID()) &&
                    a.zoneId().equals(match.zone().id()) && a.visibleTo(player.getUniqueId()) &&
                    a.cell()!=null && !a.reserved() && a.position().distanceSquared(location)<=256 &&
                    visibility.shownTo(player.getUniqueId(),a.id()))
                .sorted(Comparator.comparingDouble(a->a.position().distanceSquared(location))).limit(16).toList()) {
            if(encounters.preview(kind,fish,tick,population,this::encounterEnvironment,this::encounterSchool))
                return true;
        }
        return false;
    }
    public int agents(){return population==null?0:population.size();}
    public int privateAgents(){return population==null?0:population.privateCount();}
    public int shown(){return visibility==null?0:visibility.totalVisible();}
    public int viewers(){return visibility==null?0:visibility.viewers();}
    public long packetCount(){return previousPacketCount+(visibility==null?0:visibility.packetsSent());}
    public long deferredPackets(){return previousDeferred+(visibility==null?0:visibility.packetsDeferred());}
    public long packetFailures(){return previousFailures+(visibility==null?0:visibility.failures());}
    public long destroyFailures(){return previousDestroyFailures+(visibility==null?0:visibility.destroyFailures());}
    public int pendingDestroys(){return (visibility==null?0:visibility.pendingDestroys())+
        (retiredVisibility==null?0:retiredVisibility.pendingDestroys());}
    public int packetPeak(){return Math.max(historicalPacketPeak,visibility==null?0:visibility.peakPacketUsage());}
    public boolean backendAvailable(){return packets.available();}
    public long previewTicks(){return previewTicks;}
    public long averagePreviewNanos(){return previewTicks==0?0:cumulativePreviewNanos/previewTicks;}
    public long peakPreviewNanos(){return maxPreviewNanos;}
    public long overTwoMsTicks(){return overTwoMsTicks;}
    public long droppedWaterJobs(){return water==null?0:water.droppedJobs();}
    public int waterCells(){return water==null?0:water.cachedCells();}
    public int waterQueued(){return water==null?0:water.queued();}
    /** Accessors only on the server owner thread. Never expose these as cross-thread services. */
    FishPopulationService population(){return population;}
    public com.oheers.fish.fishing.simulator.population.FishVisualModel visualModel(FishKey key) {
        return visualMapping == null ?
            com.oheers.fish.fishing.simulator.population.FishVisualModel.COD : visualMapping.resolve(key);
    }
    WaterIndexService water(){return water;}
    void indexHook(Location loc) {
        if(running() && loc.getWorld()!=null)
            water.requestAround(loc.getWorld(),loc.getBlockX()>>4,loc.getBlockZ()>>4,1);
    }
    WaterPoint nearestHookWater(Location loc,String zoneId){
        if(!running() || loc.getWorld()==null)return null;
        return water.nearest(loc.getWorld().getUID(),loc.getX(),loc.getY(),loc.getZ(),2,cell->{
            var match=resolver.resolve(new Location(loc.getWorld(),cell.x()+.5,
                cell.y()+.32,cell.z()+.5),settings);
            return match.found() && !match.denied() && match.zone().id().equals(zoneId);
        });
    }
    public void tick(long tick){tick(tick,()->{});}
    /** AI advances after water/ambient movement, before this tick's packet visibility sync. */
    public void tick(long tick,Runnable beforeVisibility) {
        if(!running())return;
        lastTick=tick;
        long started=System.nanoTime();
        if(!packets.available()) {stop();plugin.getLogger().warning("[Simulator] PacketEvents lost, preview stopped safely");return;}
        SimulatorSettings.Ambient cfg=settings.ambient();
        List<Player> eligible=previewPlayers(cfg,tick);
        List<PacketVisibilityService.Viewer> viewerData=new ArrayList<>();
        for(Player player:eligible){
            Location loc=player.getLocation();
            SimulatorZoneResolver.Match match=resolver.resolve(loc,settings);
            if(!match.found() || match.denied())continue;
            viewerData.add(new PacketVisibilityService.Viewer(player.getUniqueId(),player.getWorld().getUID(),
                match.zone().id(),new Vec3(loc.getX(),loc.getY(),loc.getZ())));
            if(tick%40==0)water.requestAround(player.getWorld(),loc.getBlockX()>>4,loc.getBlockZ()>>4,cfg.scanRadiusChunks());
        }
        water.scan(cfg.scanColumnsPerTick(),cfg.scanNanosPerTick());
        // Simulated fish whose water chunk disappeared are removed immediately, not kept in stale water.
        if(tick%10==0)pruneInvalidWater();
        population.expire(tick);
        if(!viewerData.isEmpty() && tick%cfg.spawnIntervalTicks()==0)spawn(viewerData,cfg,tick);
        boolean move = tick%cfg.movementIntervalTicks()==0;
        if(move)moveAgents(tick);
        beforeVisibility.run();
        if(encounters!=null) {
            try {encounters.tick(tick,population,this::encounterEnvironment,this::encounterSchool);}
            catch(RuntimeException error) {
                encounters.clear();encounters=null;
                plugin.getLogger().warning("[Simulator] Rare encounter visuals disabled safely: "+error.getMessage());
            }
        }
        // Visibility changes and destructions are evaluated every scheduler tick; LOD throttles movement.
        visibility.beginTick();
        // sync() reconciles removed viewers and stale fish globally BEFORE spawns.
        visibility.sync(viewerData,new ArrayList<>(population.agents()),tick,move);
        if(encounters!=null) {
            try {renderEncounters(tick,viewerData);}
            catch(RuntimeException error) {
                encounters.clear();encounters=null;
                plugin.getLogger().warning("[Simulator] Encounter FX disabled safely: "+error.getMessage());
            }
        }
        if(waterFx!=null && tick%ecosystemSettings.fx().intervalTicks()==0) {
            try {renderAmbientFx(tick,viewerData);}
            catch(RuntimeException exception) {
                // Visual-only failure must not terminate authoritative fish sessions or reward logic.
                waterFx.clear();waterFx=null;
                plugin.getLogger().warning("[Simulator] Water FX disabled after error: "+exception.getMessage());
            }
        }
        long elapsed=Math.max(0,System.nanoTime()-started);
        previewTicks++;
        // Saturating additions preserve useful telemetry even on a months-long server.
        cumulativePreviewNanos=Long.MAX_VALUE-cumulativePreviewNanos<elapsed
            ?Long.MAX_VALUE:cumulativePreviewNanos+elapsed;
        maxPreviewNanos=Math.max(maxPreviewNanos,elapsed);
        if(elapsed>2_000_000L)overTwoMsTicks++;
    }
    private EnvironmentSnapshot encounterEnvironment(FishAgent fish,long tick){
        return environment==null || fish.cell()==null?null:environment.observe(fish.cell(),tick);
    }
    private List<UUID> encounterSchool(FishAgent fish){
        if(schooling==null || !schooling.enabled())return List.of();
        return schooling.membersOf(fish.id());
    }
    private void renderEncounters(long tick,List<PacketVisibilityService.Viewer> viewers){
        List<RareEncounterDirector.Viewer> subjects=new ArrayList<>(viewers.size());
        for(var viewer:viewers)subjects.add(new RareEncounterDirector.Viewer(
            viewer.id(),viewer.worldId(),viewer.zoneId(),viewer.position(),playerParticles.test(viewer.id())));
        encounters.render(tick,subjects,population,visibility::shownTo,this::emitEncounterParticle);
    }
    private boolean emitEncounterParticle(UUID viewerId,RareEncounterDirector.Cue cue){
        Player viewer=Bukkit.getPlayer(viewerId);
        if(viewer==null || !viewer.isOnline() || !playerParticles.test(viewerId))return false;
        World world=viewer.getWorld();
        if(!world.isChunkLoaded(((int)Math.floor(cue.position().x()))>>4,
                                 ((int)Math.floor(cue.position().z()))>>4))return false;
        Particle particle=switch(cue.kind()){
            case GOLDEN -> ENCOUNTER_DUST;
            case SHADOW -> Particle.SQUID_INK;
            case GHOST -> Particle.SOUL;
            case RARE_SCHOOL -> ENCOUNTER_ENCHANT;
            case RARE_PRESENCE -> Particle.END_ROD;
        };
        for(int i=0;i<cue.particles();i++){
            double angle=(cue.tick()*.16)+Math.PI*2*i/Math.max(1,cue.particles());
            double radius=cue.kind()==RareEncounterDirector.Kind.GOLDEN?.4:.25;
            Location point=new Location(world,cue.position().x()+Math.cos(angle)*radius,
                cue.position().y()+.15+.06*Math.sin(angle*2),
                cue.position().z()+Math.sin(angle)*radius);
            if(particle==ENCOUNTER_DUST && particle.getDataType()==Particle.DustOptions.class)
                viewer.spawnParticle(particle,point,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(255,195,45),1.15f));
            else viewer.spawnParticle(particle,point,1,0,0,0,0);
        }
        return true;
    }
    private List<Player> previewPlayers(SimulatorSettings.Ambient cfg,long tick) {
        // Scan all online players at most once per viewer-interval, not once per animation tick.
        if(lastViewerScanTick<0 || tick-lastViewerScanTick>=cfg.viewerIntervalTicks()){
            lastViewerScanTick=tick;
            List<UUID> ids=new ArrayList<>();
            for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission(PREVIEW_PERMISSION))ids.add(p.getUniqueId());
            ids.sort(Comparator.comparing(UUID::toString));
            if(ids.size()>cfg.maxPreviewViewers())ids=ids.subList(0,cfg.maxPreviewViewers());
            previewViewerCache=List.copyOf(ids);
        }
        List<Player> active=new ArrayList<>();
        for(UUID id:previewViewerCache){
            Player player=Bukkit.getPlayer(id);
            if(player!=null && player.isOnline() && player.hasPermission(PREVIEW_PERMISSION))active.add(player);
        }
        return active;
    }
    private void spawn(List<PacketVisibilityService.Viewer> viewers,SimulatorSettings.Ambient cfg,long tick){
        for(PacketVisibilityService.Viewer viewer:viewers){
            if(population.size()>=cfg.maxAgents())break;
            Player player=Bukkit.getPlayer(viewer.id());if(player==null)continue;
            UUID owner=ThreadLocalRandom.current().nextDouble()<cfg.privateChance()?viewer.id():null;
            FishKey key=catalog.sample(ThreadLocalRandom.current());if(key==null)return;
            var model=visualMapping.resolve(key);
            WaterPoint waterPoint=schooling==null?null:schooling.preferredSpawnCell(
                viewer.worldId(),viewer.zoneId(),owner,model,population,schoolNavigation(),tick);
            if(waterPoint==null)waterPoint=water.randomNear(viewer.worldId(),player.getLocation().getBlockX()>>4,
                player.getLocation().getBlockZ()>>4,cfg.scanRadiusChunks());
            if(waterPoint==null)continue;
            // Cache may contain cells on both sides of an external-region boundary.
            World world=Bukkit.getWorld(waterPoint.worldId());
            if(world==null || !world.isChunkLoaded(waterPoint.chunkX(),waterPoint.chunkZ()))continue;
            Location candidate=new Location(world,waterPoint.x()+.5,waterPoint.y()+.3,waterPoint.z()+.5);
            var match=resolver.resolve(candidate,settings);
            if(!match.found()||match.denied()||!match.zone().id().equals(viewer.zoneId()))continue;
            if(candidate.distanceSquared(player.getLocation())>cfg.viewDistance()*cfg.viewDistance())continue;
            FishAgent fish=new FishAgent(UUID.randomUUID(),PacketEntityIdAllocator.next(),key,model,
                viewer.zoneId(),viewer.worldId(),owner,tick+cfg.ttlTicks(),waterPoint.swimmingPosition());
            fish.placeAt(waterPoint);
            if(population.add(fish)) {
                spawnAttempts++;
                if(schooling!=null)schooling.onSpawn(fish,population,tick);
            }
        }
    }
    private FishSchoolService.Navigation schoolNavigation(){
        return new FishSchoolService.Navigation() {
            @Override public List<WaterPoint> adjacent(WaterPoint cell) {
                return water.adjacent(cell);
            }
            @Override public boolean traversable(FishAgent fish,WaterPoint cell) {
                if(cell==null || !fish.worldId().equals(cell.worldId()) || !water.isIndexedWater(cell))return false;
                World world=Bukkit.getWorld(cell.worldId());
                if(world==null || !world.isChunkLoaded(cell.chunkX(),cell.chunkZ()))return false;
                var match=resolver.resolve(new Location(world,cell.x()+.5,cell.y()+.32,cell.z()+.5),settings);
                return match.found() && !match.denied() && match.zone().id().equals(fish.zoneId());
            }
        };
    }
    private FishPersonalityService.Navigation behaviorNavigation() {
        FishSchoolService.Navigation delegate=schoolNavigation();
        return new FishPersonalityService.Navigation() {
            @Override public List<WaterPoint> adjacent(WaterPoint cell){return delegate.adjacent(cell);}
            @Override public boolean traversable(FishAgent agent,WaterPoint cell){return delegate.traversable(agent,cell);}
        };
    }
    /** Bukkit/world reads only occur from the Paper owner ticker, with a strict sample budget. */
    private EnvironmentSnapshot readEnvironment(WaterPoint cell,long tick) {
        World world=Bukkit.getWorld(cell.worldId());
        if(world==null || !world.isChunkLoaded(cell.chunkX(),cell.chunkZ()) ||
            world.getBlockAt(cell.x(),cell.y(),cell.z()).getType()!=Material.WATER)return null;
        int depth=0;
        for(int y=cell.y();y>=world.getMinHeight() && depth<ecosystemSettings.depthScanBlocks();y--) {
            if(world.getBlockAt(cell.x(),y,cell.z()).getType()!=Material.WATER)break;
            depth++;
        }
        EnvironmentSnapshot.Weather weather=world.isThundering()
            ?EnvironmentSnapshot.Weather.THUNDER:world.hasStorm()
                ?EnvironmentSnapshot.Weather.RAIN:EnvironmentSnapshot.Weather.CLEAR;
        long time=world.getTime();
        EnvironmentSnapshot.TimeOfDay day=(time>=13000 && time<23000)
            ?EnvironmentSnapshot.TimeOfDay.NIGHT:EnvironmentSnapshot.TimeOfDay.DAY;
        String biome=world.getBiome(cell.x(),cell.y(),cell.z()).toString();
        return new EnvironmentSnapshot(cell,EnvironmentSnapshot.depthFor(depth),day,weather,biome,depth,tick);
    }
    private void renderAmbientFx(long tick,List<PacketVisibilityService.Viewer> viewers) {
        List<SurfaceFxService.Viewer> subjects=new ArrayList<>(viewers.size());
        for(var viewer:viewers)subjects.add(new SurfaceFxService.Viewer(viewer.id(),viewer.worldId(),
            viewer.zoneId(),viewer.position(),playerParticles.test(viewer.id())));
        waterFx.tick(tick,subjects,new ArrayList<>(population.agents()),personality,
            visibility::shownTo,this::emitWaterParticle);
    }
    private boolean emitWaterParticle(UUID viewerId,SurfaceFxService.Cue cue) {
        Player player=Bukkit.getPlayer(viewerId);
        if(player==null || !player.isOnline() || !playerParticles.test(viewerId))return false;
        World world=player.getWorld();
        int cx=((int)Math.floor(cue.position().x()))>>4;
        int cz=((int)Math.floor(cue.position().z()))>>4;
        if(!world.isChunkLoaded(cx,cz))return false;
        Particle particle=switch(cue.kind()) {
            case BUBBLE_TRAIL -> WATER_BUBBLE;
            case RIPPLE, BITE_DISTURBANCE -> WATER_SPLASH;
            case LURE_PULSE -> WATER_WAKE;
        };
        // Send one particle per position, each counted against the global FX budget.
        for(int i=0;i<cue.particles();i++) {
            double angle=(2*Math.PI*i)/Math.max(1,cue.particles());
            double radius=cue.kind()==SurfaceFxService.Kind.BUBBLE_TRAIL?.12:.4;
            Location point=new Location(world,cue.position().x()+radius*Math.cos(angle),
                cue.position().y()+(cue.kind()==SurfaceFxService.Kind.BUBBLE_TRAIL?.15:.6),
                cue.position().z()+radius*Math.sin(angle));
            player.spawnParticle(particle,point,1,0,0,0,0);
        }
        return true;
    }
    private void moveAgents(long tick) {
        var rng=ThreadLocalRandom.current();
        FishSchoolService.Navigation navigation=schoolNavigation();
        FishPersonalityService.Navigation personalityNavigation=personality==null?null:behaviorNavigation();
        if(schooling!=null)schooling.steer(tick,population,navigation);
        for(FishAgent agent:population.agents()){
            if(agent.state()==FishAgent.State.FLEEING){agent.move(tick,.12);continue;}
            if(agent.state()!=FishAgent.State.WANDERING || agent.cell()==null)continue;
            EnvironmentSnapshot sample=environment==null?null:environment.observe(agent.cell(),tick);
            double multiplier=personality==null?1.0:personality.movementFactor(agent,sample);
            if(schooling!=null && schooling.assigned(agent.id())){
                agent.move(tick,schooling.speed(agent)*multiplier);
                continue;
            }
            if(agent.position().distanceSquared(agent.waypoint())<=.0025) {
                List<WaterPoint> steps=water.adjacent(agent.cell());
                // Unschoolled fish also respect region and loaded-water boundaries.
                if(!steps.isEmpty()){
                    int start=rng.nextInt(steps.size());
                    WaterPoint next=personality==null?null:personality.preferNext(agent,steps,
                        personalityNavigation,start);
                    if(next!=null)agent.travelTo(next);
                    else if(personality==null)for(int i=0;i<steps.size();i++){
                        WaterPoint step=steps.get((start+i)%steps.size());
                        if(navigation.traversable(agent,step)){agent.travelTo(step);break;}
                    }
                }
            }
            agent.move(tick,.12*multiplier);
        }
    }
    private void pruneInvalidWater(){
        for(FishAgent agent:population.agents()){
            WaterPoint cell=agent.cell();
            if(cell==null){population.remove(agent.id());continue;}
            WaterPoint next=agent.nextCell();
            World world=Bukkit.getWorld(agent.worldId());
            if(world==null||!world.isChunkLoaded(cell.chunkX(),cell.chunkZ()) ||
                !water.isIndexedWater(cell) || world.getBlockAt(cell.x(),cell.y(),cell.z()).getType()!=Material.WATER ||
                (next!=null && (!world.isChunkLoaded(next.chunkX(),next.chunkZ()) ||
                    !water.isIndexedWater(next)))){
                population.remove(agent.id());
            }
        }
    }
    private void invalidateNear(Location at){
        if(!running() || at.getWorld()==null)return;
        water.invalidateNear(at.getWorld().getUID(),at.getBlockX()>>4,at.getBlockZ()>>4);
        if(environment!=null)environment.invalidateNear(at.getWorld().getUID(),at.getBlockX()>>4,at.getBlockZ()>>4);
    }
    private void invalidateFlow(Location at) {
        if (!running() || at.getWorld() == null) return;
        var key = new WaterIndexService.ChunkKey(at.getWorld().getUID(),at.getBlockX() >> 4,at.getBlockZ() >> 4);
        Long prior = flowInvalidationTimes.get(key);
        if (prior != null && lastTick - prior < WATER_FLOW_DEBOUNCE_TICKS) return;
        if (flowInvalidationTimes.size() >= MAX_FLOW_DEBOUNCE_ENTRIES) flowInvalidationTimes.clear();
        flowInvalidationTimes.put(key,lastTick);
        invalidateNear(at);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event){invalidateNear(event.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent event){invalidateNear(event.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onWaterFlow(BlockFromToEvent event){
        if(event.getBlock().isLiquid() || event.getToBlock().isLiquid())invalidateFlow(event.getToBlock().getLocation());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event){invalidateNear(event.getBlock().getLocation());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onBucketFill(PlayerBucketFillEvent event){invalidateNear(event.getBlock().getLocation());}
    @EventHandler public void onChunkUnload(ChunkUnloadEvent event){
        if(!running())return;
        var c=event.getChunk();UUID worldId=c.getWorld().getUID();
        water.invalidate(worldId,c.getX(),c.getZ());
        if(environment!=null)environment.invalidate(worldId,c.getX(),c.getZ());
        for(FishAgent agent:population.agents())if(agent.worldId().equals(worldId)
            && ((agent.cell()!=null && agent.cell().chunkX()==c.getX() && agent.cell().chunkZ()==c.getZ())
                || (agent.nextCell()!=null && agent.nextCell().chunkX()==c.getX() && agent.nextCell().chunkZ()==c.getZ())))
            population.remove(agent.id());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event){
        UUID player=event.getPlayer().getUniqueId();
        if(waterFx!=null)waterFx.forgetViewer(player);
        if(encounters!=null)encounters.forgetViewer(player);
        forgetRetiredViewer(player);
        if(!running())return;
        population.removeOwned(player);
        visibility.dropDisconnected(player);
        previewViewerCache=previewViewerCache.stream().filter(id->!id.equals(player)).toList();
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event){
        UUID player=event.getPlayer().getUniqueId();
        if(waterFx!=null)waterFx.forgetViewer(player);
        if(encounters!=null)encounters.forgetViewer(player);
        forgetRetiredViewer(player);
        if(!running())return;
        population.removeOwned(player);
        visibility.dropDisconnected(player);
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event){
        if(waterFx!=null)waterFx.forgetViewer(event.getPlayer().getUniqueId());
        if(encounters!=null)encounters.forgetViewer(event.getPlayer().getUniqueId());
        forgetRetiredViewer(event.getPlayer().getUniqueId());
        if(!running())return;
        population.removeOwned(event.getPlayer().getUniqueId());
        // The client unloads old-world entity state on dimension switch. Reset viewer map.
        visibility.dropDisconnected(event.getPlayer().getUniqueId());
    }
    private void forgetRetiredViewer(UUID viewer){
        if(retiredVisibility==null)return;
        retiredVisibility.dropDisconnected(viewer);
        if(retiredVisibility.pendingDestroys()==0)retiredVisibility=null;
    }
}
