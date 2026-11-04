package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.presentation.FishingPresentationService;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import com.oheers.fish.fishing.simulator.SimulatorMessages;
import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import com.oheers.fish.fishing.simulator.ai.FishAiDirector;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.fishing.simulator.model.FishRenderPolicy;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owner-thread visual layer. Read-only observation of Simulator state, no reward hooks. */
public final class SimulatorEffectsService {
    private record BiteHud(UUID hookId, long biteTick, int window, long lastTick) {
        BiteHud shown(long now){return new BiteHud(hookId,biteTick,window,now);}
    }
    private final PacketCatchCinematicService cinematic;
    private final FishingPresentationService presentation;
    private SimulatorEffectsSettings settings=SimulatorEffectsSettings.disabled();
    private PremiumCinematicSettings premium=PremiumCinematicSettings.disabled();
    private long fxTick=Long.MIN_VALUE;
    private int fxEmissionsThisTick;
    private long stageCues, trailEmissions;
    private void resetFxBudget(long tick) {
        if(fxTick!=tick){fxTick=tick;fxEmissionsThisTick=0;}
    }
    private final Map<UUID,BiteHud> hud=new HashMap<>();
    private final Set<UUID> optedOut=new HashSet<>();
    private long cueCount, hudUpdates;

    public SimulatorEffectsService(PacketTransport packets,FishingPresentationService presentation) {
        this.cinematic=new PacketCatchCinematicService(packets);
        this.presentation=presentation;
    }
    public void modelPolicy(FishRenderPolicy policy){cinematic.modelPolicy(policy);}
    public void configure(SimulatorEffectsSettings candidate){
        configure(candidate,PremiumCinematicSettings.disabled());
    }
    public void configure(SimulatorEffectsSettings candidate, PremiumCinematicSettings scenes){
        clear(); settings=candidate; premium=scenes;
        cinematic.configure(candidate,scenes,new PacketCatchCinematicService.StageFxSink() {
            @Override public void stage(Player viewer,Location origin,Stage stage,
                    PremiumCinematicSettings.Profile profile,FishKey fishKey) {
                sceneStage(viewer,origin,stage,profile,fishKey);
            }
            @Override public void trail(Player viewer,Location origin,PremiumCinematicSettings.Profile profile) {
                sceneTrail(viewer,origin,profile);
            }
            @Override public void frame(Player viewer,Location origin,PremiumCinematicTimeline.Frame frame,
                    PremiumCinematicTimeline.Style style,PremiumCinematicSettings.Profile profile,long elapsed) {
                if(!viewer.isOnline() || optedOut.contains(viewer.getUniqueId()) ||
                    !presentation.particles(viewer.getUniqueId()))return;
                int allowance=Math.max(0,premium.maxParticleEmissionsPerTick()-fxEmissionsThisTick);
                var emission=CinematicParticleEffects.emit(viewer,style,frame.stage(),
                    new Vec3(origin.getX(),origin.getY(),origin.getZ()),
                    frame.position(),elapsed,frame.progress(),allowance);
                fxEmissionsThisTick+=emission.consumed();
                if(emission.failure()!=null)throw new IllegalStateException("Cinematic particle frame failed",emission.failure());
            }
        });
    }
    public boolean enabled(){return settings.enabled();}
    public int activeCinematics(){return cinematic.active();}
    public int activeHud(){return hud.size();}
    public long packetsSent(){return cinematic.packetsSent();}
    public long packetFailures(){return cinematic.packetsFailed();}
    public long cinematicRejected(){return cinematic.rejected();}
    public int cinematicPendingDestroys(){return cinematic.pendingDestroys();}
    public long cueCount(){return cueCount;}
    public boolean premiumEnabled(){return cinematic.premiumEnabled();}
    public long premiumScenes(){return cinematic.scenesPlayed();}
    public long stageCueFailures(){return cinematic.stageCueFailures();}
    public long stageCues(){return stageCues;}
    public long trailEmissions(){return trailEmissions;}
    public boolean preview(Player viewer, Location water, String tier, long now) {
        if(!settings.enabled() || !settings.cinematicEnabled() ||
            optedOut.contains(viewer.getUniqueId()) || !presentation.cinematic(viewer.getUniqueId()))return false;
        // Explicitly synthetic key for an ADMIN-ONLY visual scene; never enters fish catalog or rewards.
        FishKey visualOnly=new FishKey("Preview", "VisualOnly");
        return cinematic.preview(viewer,water,visualOnly,FishVisualModel.TROPICAL_FISH,now,tier);
    }
    public void stopCinematic(UUID playerId){cinematic.stop(playerId);}
    public long hudUpdates(){return hudUpdates;}
    public boolean effectsEnabledFor(UUID player){return !optedOut.contains(player);}
    public void setEffectsEnabled(UUID player,boolean enabled){
        if(enabled)optedOut.remove(player);
        else{optedOut.add(player);cancel(player);}
    }
    public void quit(UUID player){cancel(player);cinematic.forgetDisconnected(player);optedOut.remove(player);}
    public void cancel(UUID player){hud.remove(player);cinematic.stop(player);}
    public void clearHud(UUID player){hud.remove(player);}
    public void clear(){hud.clear();cinematic.clear();}

    public void phase(Player player,UUID hookId,FishAiDirector.Phase phase,FishKey fishKey,
                      long now,int reactionWindow,Location water){
        if(!settings.enabled() || optedOut.contains(player.getUniqueId()) || fishKey==null)return;
        var preset=settings.forFish(fishKey.rarityId(),fishKey.fishId());
        if(preset==null)return;
        if(phase==FishAiDirector.Phase.INTERESTED){cue(player,water,preset.interest());}
        else if(phase==FishAiDirector.Phase.BITE_READY){
            cue(player,water,preset.bite());
            if(settings.hudEnabled())hud.put(player.getUniqueId(),new BiteHud(hookId,now,reactionWindow+1,-1));
        }
    }

    public void finished(Player player,UUID hookId,FishAiDirector.Outcome result,FishKey fishKey,
                         Location water,String zoneId,FishVisualModel model,long now){
        hud.remove(player.getUniqueId());
        if(!settings.enabled() || optedOut.contains(player.getUniqueId()) || fishKey==null)return;
        var preset=settings.forFish(fishKey.rarityId(),fishKey.fishId());
        if(preset==null)return;
        boolean success=result==FishAiDirector.Outcome.SUCCESS_PREVIEW_ONLY ||
            result==FishAiDirector.Outcome.SUCCESS_COMMITTED;
        boolean failure=result==FishAiDirector.Outcome.EARLY_REEL ||
            result==FishAiDirector.Outcome.MISSED_WINDOW || result==FishAiDirector.Outcome.LOST_FISH;
        if(success){
            cue(player,water,preset.success());
            if(presentation.cinematic(player.getUniqueId())) {
                // CINEMATIC chooses the V2 storyboard; BALANCED keeps the short low-noise arc.
                if(presentation.profile(player.getUniqueId())==UiProfile.CINEMATIC)
                    cinematic.start(player,water,fishKey,model,zoneId,now,preset);
                else
                    cinematic.startLegacy(player,water,fishKey,model,zoneId,now,preset);
            }
        } else if(failure)cue(player,water,preset.failure());
    }

    public void tick(long now, FishAiDirector ai){
        resetFxBudget(now);
        cinematic.tick(now);
        if(!settings.enabled() || !settings.hudEnabled()){hud.clear();return;}
        for(var entry:List.copyOf(hud.entrySet())){
            UUID id=entry.getKey();BiteHud state=entry.getValue();
            Player player=Bukkit.getPlayer(id);
            FishAiDirector.View view=ai==null?null:ai.view(id);
            if(player==null || !player.isOnline() || optedOut.contains(id) || view==null ||
                view.phase()!=FishAiDirector.Phase.BITE_READY || !view.hookId().equals(state.hookId())){
                hud.remove(id);continue;
            }
            if(state.lastTick()>=0 && now-state.lastTick()<settings.hudIntervalTicks())continue;
            int remain=(int)Math.max(0,state.biteTick()+state.window()-now);
            presentation.countdown(id,state.hookId(),now,remain,state.window(),settings.hudSegments(),
                settings.filledGlyph(),settings.emptyGlyph());
            hud.put(id,state.shown(now));hudUpdates++;
        }
    }

    private void sceneStage(Player viewer,Location water,Stage stage,
                            PremiumCinematicSettings.Profile profile,FishKey fishKey) {
        if(optedOut.contains(viewer.getUniqueId()) || !viewer.isOnline())return;
        PremiumCinematicSettings.Cue cue=profile.cue(stage);
        if(cue==null)return;
        if(presentation.sound(viewer.getUniqueId()) && cue.sound()!=null)
            viewer.playSound(Sound.sound().type(cue.sound()).volume(cue.volume()).pitch(cue.pitch()).build(),
                Sound.Emitter.self());
        if(presentation.particles(viewer.getUniqueId()) && cue.particle()!=null && cue.count()>0){
            int allowed=CinematicParticleEffects.reserve(Math.min(cue.count(),
                Math.max(0,premium.maxParticleEmissionsPerTick()-fxEmissionsThisTick)));
            fxEmissionsThisTick+=allowed;
            // Only viewer-local particles. VORTEX gets a small actual ring; other stages use a soft burst.
            for(int i=0;i<allowed;i++){
                Location point=water.clone();
                double theta=2*Math.PI*i/Math.max(1,allowed);
                double radius=stage==Stage.VORTEX?cue.radius():cue.radius()*.3;
                point.add(Math.cos(theta)*radius,stage==Stage.SURFACE_BREAK?.2:0,Math.sin(theta)*radius);
                viewer.spawnParticle(cue.particle(),point,1,0,.04,0,.005);
            }
        }
        // Optional short reveal, NEVER fake a reward: display catalog identity only after success.
        if(stage==Stage.HERO_REVEAL && profile.durations().reveal()>=8 &&
            !fishKey.rarityId().equals("Preview") && presentation.cinematic(viewer.getUniqueId())) {
            SimulatorMessages.title(viewer,"cinematic-reveal-title", "cinematic-reveal-subtitle",
                Map.of("rarity",fishKey.rarityId(),"fish",fishKey.fishId()));
        }
        stageCues++;
    }
    private void sceneTrail(Player viewer,Location fishPosition,PremiumCinematicSettings.Profile profile){
        if(optedOut.contains(viewer.getUniqueId()) || !presentation.particles(viewer.getUniqueId()) ||
            profile.trail()==null || fxEmissionsThisTick>=premium.maxParticleEmissionsPerTick() ||
            CinematicParticleEffects.reserve(1)==0)return;
        fxEmissionsThisTick++;
        viewer.spawnParticle(profile.trail(),fishPosition,1,.08,.08,.08,0);
        trailEmissions++;
    }

    private void cue(Player player,Location position,SimulatorEffectsSettings.Cue cue){
        if(cue==null || position==null || position.getWorld()==null ||
            !position.getWorld().equals(player.getWorld()))return;
        if(presentation.sound(player.getUniqueId()) && cue.sound()!=null){
            Sound sound=Sound.sound().type(cue.sound()).volume(cue.volume()).pitch(cue.pitch()).build();
            player.playSound(sound,Sound.Emitter.self());
        }
        if(presentation.particles(player.getUniqueId()) && cue.particle()!=null && cue.count()>0){
            player.spawnParticle(cue.particle(),position,cue.count(),.25,.18,.25,.015);
        }
        cueCount++;
    }
}
