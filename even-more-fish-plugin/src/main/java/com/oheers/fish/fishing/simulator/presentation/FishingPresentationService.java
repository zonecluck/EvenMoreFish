package com.oheers.fish.fishing.simulator.presentation;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.SimulatorMessages;
import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Single Simulator ActionBar owner (Paper main thread). It never changes AI or catches.
 * Async preference load is protected against player quit, manual edits, and reconnect races.
 */
public final class FishingPresentationService {
    private static final class ProfileEntry {
        UiProfile value;
        boolean dirty;
        ProfileEntry(UiProfile value) { this.value=value; }
    }
    private final EvenMoreFish plugin;
    private final PresentationArbiter arbiter = new PresentationArbiter();
    private final VisualPreferenceStore persistence;
    private final Map<UUID,ProfileEntry> profiles = new HashMap<>();
    private final Set<UUID> muted = new HashSet<>();
    private SimulatorPresentationSettings settings=SimulatorPresentationSettings.disabled();
    private long asyncFailures;

    public FishingPresentationService(EvenMoreFish plugin) {
        this.plugin=plugin;
        this.persistence=new VisualPreferenceStore(plugin.getDataFolder().toPath().resolve("simulator/player-prefs"));
    }
    public void configure(SimulatorPresentationSettings newSettings) {
        settings=newSettings;
        arbiter.clear();
    }
    public void begin(UUID playerId, UUID hookId) {
        arbiter.begin(playerId,hookId);
        prefetch(playerId);
    }
    public void phase(UUID playerId,UUID hookId,FishAiDirector.Phase phase,long tick,int window) {
        if(!canShow(playerId))return;
        if(phase==FishAiDirector.Phase.INTERESTED && settings.interestedHint()) {
            arbiter.offer(playerId,hookId,PresentationArbiter.Channel.ACTION_BAR,
                PresentationArbiter.Priority.APPROACHING,"ai-interested",Map.of(),tick,settings.biteTtlTicks());
        } else if(phase==FishAiDirector.Phase.BITE_READY) {
            arbiter.offer(playerId,hookId,PresentationArbiter.Channel.ACTION_BAR,
                PresentationArbiter.Priority.BITE_READY,"ai-bite",Map.of(
                    "time",Integer.toString(window)),tick,
                    settings.biteTtlTicks());
        }
    }
    public void countdown(UUID playerId,UUID hookId,long tick,int remaining,int total,int segments,
                          String filledGlyph,String emptyGlyph) {
        if(!canShow(playerId) || total<1 || segments<1)return;
        int complete=Math.max(0,Math.min(segments,(int)Math.ceil((double)segments*remaining/total)));
        String bar=filledGlyph.repeat(complete)+emptyGlyph.repeat(segments-complete);
        arbiter.offer(playerId,hookId,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"presentation-bite",Map.of(
                "bar",bar,"time",String.format(Locale.ROOT,"%.1fs",Math.max(0,remaining)/20.0)),tick,
                settings.biteTtlTicks());
    }
    public void result(UUID playerId,UUID hookId,FishAiDirector.Outcome outcome,long tick) {
        if(!canShow(playerId))return;
        String key=switch(outcome) {
            case SUCCESS_PREVIEW_ONLY -> "ai-success-preview";
            case SUCCESS_COMMITTED -> "ai-success-committed";
            case COMMIT_REJECTED -> "ai-commit-rejected";
            case COMMIT_FAILED -> "ai-commit-failed";
            case EARLY_REEL -> "ai-early";
            case MISSED_WINDOW -> "ai-missed";
            case NO_FISH -> "ai-no-target";
            case LOST_FISH -> "ai-lost";
            default -> "ai-lost";
        };
        arbiter.offer(playerId,hookId,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.CATCH_RESULT,key,Map.of(),tick,settings.resultTtlTicks());
    }
    public void tick(long tick) {
        if(!settings.enabled())return;
        for(var message:arbiter.drain(tick,settings.minimumIntervalTicks())) {
            Player viewer=Bukkit.getPlayer(message.playerId());
            if(viewer==null || !viewer.isOnline() || !canShow(message.playerId()))continue;
            try { SimulatorMessages.actionBar(viewer,message.messageKey(),message.variables()); }
            catch(RuntimeException error) {
                plugin.getLogger().warning("[Simulator] HUD render failed: " + error.getMessage());
            }
        }
    }
    public void closeSession(UUID playerId,UUID hookId) { arbiter.close(playerId,hookId); }
    public void cancel(UUID playerId) { arbiter.forget(playerId); }
    public void quit(UUID playerId) { cancel(playerId); muted.remove(playerId); profiles.remove(playerId); }
    public void clear() { arbiter.clear(); muted.clear(); }
    public boolean canShow(UUID playerId) { return settings.enabled() && !muted.contains(playerId) && profile(playerId).hud(); }
    public boolean sound(UUID playerId) { return !muted.contains(playerId) && profile(playerId).sound(); }
    public boolean particles(UUID playerId) { return !muted.contains(playerId) && profile(playerId).particle(); }
    public boolean cinematic(UUID playerId) { return !muted.contains(playerId) && profile(playerId).cinematic(); }
    public UiProfile profile(UUID playerId) { return profiles.containsKey(playerId) ? profiles.get(playerId).value : settings.defaultProfile(); }
    public void mute(UUID playerId,boolean quiet) {
        if(quiet) { muted.add(playerId); cancel(playerId); }
        else muted.remove(playerId);
    }
    public void changeProfile(UUID playerId,UiProfile profile) {
        ProfileEntry entry=profiles.computeIfAbsent(playerId,id->new ProfileEntry(settings.defaultProfile()));
        entry.value=profile;entry.dirty=true;
        if(profile==UiProfile.OFF)cancel(playerId);
        persistence.save(playerId,profile).whenComplete((ignored,error)->{
            if(error!=null) {
                synchronized(this) { asyncFailures++; }
                plugin.getLogger().warning("[Simulator] Could not save visual preference for " + playerId + ": " + error.getMessage());
            }
        });
    }
    /** Lazy async load; never overwrites a local edit or a newly joined session. */
    public void prefetch(UUID playerId) {
        if(profiles.containsKey(playerId))return;
        ProfileEntry entry=new ProfileEntry(settings.defaultProfile());
        profiles.put(playerId,entry);
        persistence.load(playerId,settings.defaultProfile()).whenComplete((profile,error)->{
            if(error!=null) {
                synchronized(this) { asyncFailures++; }
                plugin.getLogger().warning("[Simulator] Could not load visual preference for " + playerId + ": " + error.getMessage());
            }
            // Never touch player/session state from the storage worker.
            if(!plugin.isEnabled())return;
            EvenMoreFish.getScheduler().runTask(()->{
                if(plugin.isEnabled() && profiles.get(playerId)==entry && !entry.dirty &&
                    Bukkit.getPlayer(playerId)!=null) entry.value=error==null?profile:settings.defaultProfile();
            });
        });
    }
    public int activeHudSessions() { return arbiter.sessions(); }
    public long blockedStale() { return arbiter.blockedStaleSession(); }
    public long blockedLower() { return arbiter.blockedLowerPriority(); }
    public long sent() { return arbiter.emissions(); }
    public synchronized long asyncFailures() { return asyncFailures; }
    public void shutdown() {
        clear();profiles.clear();
        if(!persistence.shutdown(Duration.ofSeconds(3)))
            plugin.getLogger().warning("[Simulator] Visual preference flush timed out during shutdown");
    }
}
