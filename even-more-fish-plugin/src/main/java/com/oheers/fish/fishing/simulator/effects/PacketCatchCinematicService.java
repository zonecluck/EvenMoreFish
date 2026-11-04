package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.model.FishRenderSpec;
import com.oheers.fish.fishing.simulator.model.FishRenderPolicy;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.visual.PacketCleanupQueue;
import com.oheers.fish.fishing.simulator.visual.PacketEntityIdAllocator;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sole packet-entity owner for both legacy arcs and V2 premium timelines.
 * Never calls an EMF reward, event, player inventory, or fish-selection method.
 * Packet spawn failure MAY leave a client entity: orphan IDs must be retried.
 * Paper main thread only; NOT Folia-compatible.
 */
public final class PacketCatchCinematicService {
    public interface StageFxSink {
        void stage(Player viewer, Location origin, Stage stage, PremiumCinematicSettings.Profile profile, FishKey fishKey);
        void trail(Player viewer, Location fishPosition, PremiumCinematicSettings.Profile profile);
        default void frame(Player viewer, Location origin, PremiumCinematicTimeline.Frame frame,
                           PremiumCinematicTimeline.Style style, PremiumCinematicSettings.Profile profile,
                           long elapsed) {}
    }
    private static final class Animation {
        final FishAgent fish;
        final CatchArc legacy;
        final PremiumCinematicTimeline premium;
        final PremiumCinematicSettings.Profile premiumProfile;
        final Vec3 origin;
        final FishRenderSpec visual;
        final long start;
        final int duration;
        boolean spawned;
        Stage lastStage;

        Animation(FishAgent fish, CatchArc legacy, PremiumCinematicTimeline premium,
                  PremiumCinematicSettings.Profile premiumProfile, Vec3 origin, long start, int duration,
                  boolean spawned, FishRenderSpec visual) {
            this.fish=fish;
            this.legacy=legacy;
            this.premium=premium;
            this.premiumProfile=premiumProfile;
            this.origin=origin;
            this.visual=visual;
            this.start=start;
            this.duration=duration;
            this.spawned=spawned;
        }
    }
    private final PacketTransport transport;
    private FishRenderPolicy modelPolicy;
    private final PacketCleanupQueue pendingCleanup = new PacketCleanupQueue(1024);
    private final Map<UUID,Animation> active = new HashMap<>();
    private SimulatorEffectsSettings settings = SimulatorEffectsSettings.disabled();
    private PremiumCinematicSettings premiumSettings = PremiumCinematicSettings.disabled();
    private StageFxSink stageFx;
    private long packetsSent, packetsFailed, rejected, scenesPlayed, stageCueFailures;
    private long budgetTick = Long.MIN_VALUE;
    private int usedThisTick;
    private void newTick(long tick) {
        if(budgetTick != tick) { budgetTick=tick; usedThisTick=0; }
    }

    public PacketCatchCinematicService(PacketTransport transport) { this.transport=transport; }
    public void modelPolicy(FishRenderPolicy policy) {
        // Stop scenes before switching visual policy; old entity IDs are destroyed/retried.
        clear(); this.modelPolicy=policy;
    }
    private FishRenderSpec visual(UUID viewer,FishAgent fish){
        return modelPolicy==null ? FishRenderSpec.vanilla(fish.model()) : modelPolicy.choose(viewer,fish);
    }

    public void configure(SimulatorEffectsSettings settings, PremiumCinematicSettings premium, StageFxSink fx) {
        clear();
        this.settings=settings;
        this.premiumSettings=premium;
        this.stageFx=fx;
    }
    /** Compatibility for older tests/components; premium remains disabled. */
    public void configure(SimulatorEffectsSettings settings) {
        configure(settings, PremiumCinematicSettings.disabled(), null);
    }
    public int active(){return active.size();}
    public long packetsSent(){return packetsSent;}
    public long packetsFailed(){return packetsFailed;}
    public long rejected(){return rejected;}
    public long scenesPlayed(){return scenesPlayed;}
    public long stageCueFailures(){return stageCueFailures;}
    public int pendingDestroys(){return pendingCleanup.pending();}
    public boolean premiumEnabled(){return premiumSettings.enabled();}

    public boolean start(Player viewer, Location water, FishKey key, FishVisualModel model,
                         String zoneId, long tick, SimulatorEffectsSettings.Preset fallback) {
        PremiumCinematicSettings.Profile profile = premiumSettings.enabled() ? premiumSettings.profile(key) : null;
        return begin(viewer, water, key, model, zoneId, tick, fallback, profile);
    }

    /** Balanced visual mode remains the shorter Phase 8 arc even when Premium is enabled. */
    public boolean startLegacy(Player viewer, Location water, FishKey key, FishVisualModel model,
                               String zoneId, long tick, SimulatorEffectsSettings.Preset fallback) {
        return begin(viewer,water,key,model,zoneId,tick,fallback,null);
    }

    /** Admin-only visual preview; the caller must check permission and validate the target location. */
    public boolean preview(Player viewer, Location water, FishKey key, FishVisualModel model,
                           long tick, String requestedPreset) {
        if(!premiumSettings.enabled())return false;
        PremiumCinematicSettings.Profile profile = premiumSettings.preview(requestedPreset);
        return profile!=null && begin(viewer, water, key, model, "ADMIN_VISUAL_PREVIEW", tick, null, profile);
    }

    private boolean begin(Player viewer, Location water, FishKey key, FishVisualModel model,
                          String zoneId, long tick, SimulatorEffectsSettings.Preset fallback,
                          PremiumCinematicSettings.Profile profile) {
        newTick(tick);
        if(!settings.enabled() || !settings.cinematicEnabled() || !transport.available() ||
            viewer==null || water==null || key==null || model==null || water.getWorld()==null ||
            !viewer.isOnline() || !viewer.getWorld().equals(water.getWorld()) ||
            water.distanceSquared(viewer.getLocation()) > settings.cinematicMaxDistance()*settings.cinematicMaxDistance() ||
            !pendingCleanup.hasCapacity() || pendingCleanup.pendingFor(viewer.getUniqueId()) ||
            active.size()>=settings.maxConcurrentCinematics() || active.containsKey(viewer.getUniqueId()) ||
            (profile==null && (fallback==null || usedThisTick+2>settings.cinematicPacketBudgetPerTick()))) {
            rejected++; return false;
        }
        Location eye=viewer.getEyeLocation().clone().add(0,-.35,0);
        Vec3 from=new Vec3(water.getX(), water.getY()+.2, water.getZ());
        Vec3 to=new Vec3(eye.getX(),eye.getY(),eye.getZ());
        PremiumCinematicTimeline timeline = profile==null ? null : new PremiumCinematicTimeline(
            profile.durations(),from,to,profile.arcHeight(),profile.orbit(),premiumSettings.style(profile));
        int duration=timeline==null?fallback.cinematicDurationTicks():timeline.duration();
        FishAgent fish=new FishAgent(UUID.randomUUID(),PacketEntityIdAllocator.next(),key,model,
            zoneId,water.getWorld().getUID(),viewer.getUniqueId(), tick+duration+3,from);
        FishRenderSpec render=visual(viewer.getUniqueId(),fish);
        boolean spawned = false;
        if (timeline==null) {
            // Old behavior preserved if Premium V2 is OFF.
            usedThisTick+=2;
            spawned=sendSpawn(viewer.getUniqueId(),fish,render);
            if(!spawned)return false;
        }
        active.put(viewer.getUniqueId(), new Animation(fish,
            timeline==null?new CatchArc(from,to,fallback.cinematicArcHeight()):null,
            timeline,profile,from,tick,duration,spawned,render));
        if(timeline!=null) scenesPlayed++;
        return true;
    }

    private boolean sendSpawn(UUID viewer, FishAgent fish, FishRenderSpec visual) {
        boolean spawned;
        try { spawned=transport.spawn(viewer,fish,visual); }
        catch(RuntimeException | LinkageError error) { spawned=false; }
        if(!spawned) {
            packetsFailed++;
            if(!pendingCleanup.record(viewer,fish.entityId()))
                throw new IllegalStateException("Cinematic spawn cleanup queue exhausted");
            return false;
        }
        packetsSent+=2; // spawn and metadata packet
        return true;
    }

    /** One shared tick; FX and fish teleports are bounded, cleanup attempts always come first. */
    public void tick(long tick) {
        newTick(tick);
        if(!transport.available()) {clear();return;}
        for(var entry:List.copyOf(active.entrySet())) {
            Animation a=entry.getValue();Player viewer=Bukkit.getPlayer(entry.getKey());
            if(viewer==null || !viewer.isOnline() || !viewer.getWorld().getUID().equals(a.fish.worldId()) ||
                tick-a.start>=a.duration)stop(entry.getKey());
        }
        var cleanup=pendingCleanup.retry(transport,
            Math.max(0,settings.cinematicPacketBudgetPerTick()-usedThisTick));
        usedThisTick+=cleanup.attempts();
        packetsSent+=cleanup.cleared();
        packetsFailed+=cleanup.failed();
        if(pendingCleanup.pending()>0 || active.isEmpty())return;
        for(var entry:List.copyOf(active.entrySet())) {
            UUID playerId=entry.getKey();Animation a=entry.getValue();
            // Cancel this scene if the viewer changes pack eligibility mid-animation.
            if(!a.visual.equals(visual(playerId,a.fish))){stop(playerId);continue;}
            Player viewer=Bukkit.getPlayer(playerId);
            if(viewer==null || !viewer.isOnline() || !viewer.getWorld().getUID().equals(a.fish.worldId())) {
                stop(playerId);continue;
            }
            Location position=viewer.getLocation();
            if(position.distanceSquared(new Location(viewer.getWorld(),a.origin.x(),a.origin.y(),a.origin.z()))
                >settings.cinematicMaxDistance()*settings.cinematicMaxDistance()) {
                stop(playerId);continue;
            }
            long elapsed=tick-a.start;
            if(elapsed>=a.duration) {stop(playerId);continue;}
            Vec3 destination;
            if(a.premium!=null) {
                var frame=a.premium.sample(elapsed);
                if(a.lastStage!=frame.stage()) {
                    a.lastStage=frame.stage();
                    safelyStage(viewer,a,frame);
                }
                safelyFrame(viewer,a,frame,elapsed);
                if(!frame.visible())continue; // Water anticipation uses particles only.
                destination=frame.position();
                if(!a.spawned) {
                    if(usedThisTick+2>settings.cinematicPacketBudgetPerTick())continue;
                    usedThisTick+=2; // Reserve prior to sending, including partial spawn failures.
                    if(!sendSpawn(playerId,a.fish,a.visual)) {active.remove(playerId);continue;}
                    a.spawned=true;
                }
                if(a.premiumProfile.trail()!=null && elapsed%a.premiumProfile.trailInterval()==0)
                    safelyTrail(viewer,a,destination);
            } else {
                destination=a.legacy.sample((double)Math.max(0,elapsed)/a.duration);
            }
            if(usedThisTick>=settings.cinematicPacketBudgetPerTick())continue;
            a.fish.waypoint(destination);
            a.fish.move(tick,100);
            usedThisTick++;
            boolean moved;
            try { moved=transport.teleport(playerId,a.fish); }
            catch(RuntimeException | LinkageError error) { moved=false; }
            if(moved)packetsSent++;
            else { packetsFailed++;stop(playerId); }
        }
    }

    private void safelyStage(Player viewer, Animation animation, PremiumCinematicTimeline.Frame frame) {
        if(stageFx==null)return;
        Vec3 location=frame.visible() && animation.premium.style()!=PremiumCinematicTimeline.Style.STANDARD
            ? frame.position() : animation.origin;
        try { stageFx.stage(viewer,new Location(viewer.getWorld(),location.x(),
            location.y(),location.z()), frame.stage(),animation.premiumProfile,animation.fish.fishKey()); }
        catch(RuntimeException | LinkageError failure) { stageCueFailures++; }
    }
    private void safelyFrame(Player viewer, Animation animation, PremiumCinematicTimeline.Frame frame, long elapsed) {
        if(stageFx==null || animation.premium.style()==PremiumCinematicTimeline.Style.STANDARD || elapsed%2!=0)return;
        try { stageFx.frame(viewer,new Location(viewer.getWorld(),animation.origin.x(),
            animation.origin.y(),animation.origin.z()),frame,animation.premium.style(),animation.premiumProfile,elapsed); }
        catch(RuntimeException | LinkageError failure) { stageCueFailures++; }
    }
    private void safelyTrail(Player viewer, Animation animation, Vec3 destination) {
        if(stageFx==null)return;
        try { stageFx.trail(viewer,new Location(viewer.getWorld(),destination.x(),destination.y(),destination.z()),
            animation.premiumProfile); }
        catch(RuntimeException | LinkageError failure) { stageCueFailures++; }
    }
    public void stop(UUID playerId) {
        Animation a=active.remove(playerId);
        if(a==null || !a.spawned)return;
        boolean destroyed;
        try { destroyed=transport.destroy(playerId,a.fish.entityId()); }
        catch(RuntimeException | LinkageError error) { destroyed=false; }
        if(destroyed)packetsSent++;
        else {
            packetsFailed++;
            if(!pendingCleanup.record(playerId,a.fish.entityId()))
                throw new IllegalStateException("Cinematic destroy cleanup queue exhausted");
        }
    }
    public void forgetDisconnected(UUID id) { pendingCleanup.forgetDisconnected(id); }
    public void clear() {
        for(UUID id:List.copyOf(active.keySet()))stop(id);
        var retry=pendingCleanup.retry(transport,1024);
        packetsSent+=retry.cleared();
        packetsFailed+=retry.failed();
    }
}
