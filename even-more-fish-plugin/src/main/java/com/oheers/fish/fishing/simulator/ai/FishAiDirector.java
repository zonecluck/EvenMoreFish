package com.oheers.fish.fishing.simulator.ai;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishPopulationService;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.population.WaterPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Owner-thread fish AI. Phase 6 preview never grants items; Phase 7 adds a
 * strictly gated CatchCommitter, which calls the EMF reward bridge only after a
 * server-authoritative bite/reel. This class never directly accesses Bukkit,
 * ItemStack, inventory, economy or the database.
 */
public final class FishAiDirector {
    public enum Phase { SEARCHING, INTERESTED, APPROACHING, BITE_READY, COMMITTING }
    public enum Outcome {
        NONE, SUCCESS_PREVIEW_ONLY, SUCCESS_COMMITTED, COMMIT_REJECTED, COMMIT_FAILED,
        EARLY_REEL, MISSED_WINDOW, NO_FISH, LOST_FISH, ABORTED
    }

    public record Tuning(int maxActive, int maxCandidates, int maxPathNodes, int searchIntervalTicks,
                         int interestTicks, int reactionWindowTicks, int graceTicks, int maxRetargets,
                         int castCooldownTicks, int maxWaitTicks, double attractionRadius,
                         double swimSpeed, double biteRadius) {
        public Tuning {
            if (maxActive<1 || maxCandidates<1 || maxPathNodes<1 || searchIntervalTicks<1
                || interestTicks<1 || reactionWindowTicks<1 || graceTicks<0 || maxRetargets<0
                || castCooldownTicks<0 || maxWaitTicks<=reactionWindowTicks ||
                !(attractionRadius>0) || !(swimSpeed>0) || !(biteRadius>0)) {
                throw new IllegalArgumentException("Invalid fish AI tuning");
            }
        }
    }

    public interface WaterNavigation {
        boolean valid(WaterPoint cell);
        /** Allow the runtime to enforce zone and protection boundaries along the route. */
        default boolean traversable(WaterPoint cell,String zoneId){return valid(cell);}
        List<WaterPoint> adjacent(WaterPoint cell);
    }

    public interface Listener {
        void phase(UUID playerId, UUID hookId, Phase phase, FishKey fishKey);
        void finished(UUID playerId, UUID hookId, Outcome outcome, FishKey fishKey);
    }

    public record View(UUID playerId, UUID hookId, String zoneId, UUID worldId, Phase phase,
                       UUID fishId, FishKey fishKey, long biteTick, WaterPoint goal) {}

    /**
     * Explicit two-stage, synchronous reward contract. prepare() may raise an EMF
     * cancellable event but must NEVER hand over an item, take bait, or record stats.
     * The agent is consumed only after preparation and ready() both succeed.
     * deliver() runs once after that irreversible claim. All calls are on the
     * single Paper owner thread; this is deliberately NOT a distributed transaction.
     */
    public interface CatchCommitter {
        PreparedCatch prepare(View view, FishAgent fish);
    }
    public interface PreparedCatch {
        boolean ready();
        boolean deliver();
    }

    private static final class Cast {
        final UUID playerId, hookId, worldId;
        final String zoneId;
        final long startTick;
        WaterPoint goal;
        Phase phase=Phase.SEARCHING;
        UUID fishId;
        FishKey fishKey;
        ArrayDeque<WaterPoint> route=new ArrayDeque<>();
        long phaseTick,lastSearchTick=Long.MIN_VALUE;
        long biteTick=-1;
        int retargets;
        Cast(UUID playerId,UUID hookId,String zoneId,UUID worldId,long tick) {
            this.playerId=playerId;this.hookId=hookId;this.zoneId=zoneId;
            this.worldId=worldId;this.startTick=tick;phaseTick=tick;
        }
        View view(){return new View(playerId,hookId,zoneId,worldId,phase,fishId,fishKey,biteTick,goal);}
    }

    private final FishPopulationService population;
    private final WaterNavigation water;
    private final Tuning tuning;
    private final Map<UUID,Cast> casts=new HashMap<>();
    private final Map<UUID,Long> nextCastTick=new HashMap<>();
    private long successes, failures, misses;

    public FishAiDirector(FishPopulationService population,WaterNavigation water,Tuning tuning){
        this.population=Objects.requireNonNull(population);
        this.water=Objects.requireNonNull(water);
        this.tuning=Objects.requireNonNull(tuning);
    }
    public int active(){return casts.size();}
    public long successes(){return successes;}
    public long failures(){return failures;}
    public long misses(){return misses;}
    public View view(UUID playerId){Cast cast=casts.get(playerId);return cast==null?null:cast.view();}

    public boolean canBegin(UUID playerId,long tick){
        return casts.size()<tuning.maxActive() && !casts.containsKey(playerId)
            && tick>=nextCastTick.getOrDefault(playerId,Long.MIN_VALUE);
    }

    /** Hook UUID is also the reservation token, bound for the whole cast. */
    public boolean begin(UUID playerId,UUID hookId,String zoneId,UUID worldId,long tick){
        Objects.requireNonNull(playerId);Objects.requireNonNull(hookId);
        Objects.requireNonNull(zoneId);Objects.requireNonNull(worldId);
        if(!canBegin(playerId,tick))return false;
        if(nextCastTick.size()>512)nextCastTick.entrySet().removeIf(e->e.getValue()<=tick);
        casts.put(playerId,new Cast(playerId,hookId,zoneId,worldId,tick));
        return true;
    }

    /** Indexed-water target can appear a few ticks after the real hook has settled. */
    public boolean setGoal(UUID playerId,UUID hookId,WaterPoint nearest){
        Cast cast=casts.get(playerId);
        if(cast==null || !cast.hookId.equals(hookId) || cast.goal!=null || nearest==null
            || !cast.worldId.equals(nearest.worldId()) || !water.valid(nearest))return false;
        if(!water.traversable(nearest,cast.zoneId))return false;
        cast.goal=nearest;return true;
    }

    /** Called on one authoritative server tick. score <= 0 excludes a fish entirely. */
    public void tick(UUID playerId,long tick,ToDoubleFunction<FishAgent> attraction,Listener listener){
        Cast cast=casts.get(playerId);
        if(cast==null || cast.phase==Phase.COMMITTING)return;
        if(cast.phase!=Phase.BITE_READY && tick-cast.startTick>=tuning.maxWaitTicks()){
            finish(cast,Outcome.NO_FISH,tick,listener);return;
        }
        if(cast.phase==Phase.SEARCHING){
            if(cast.goal==null || (cast.lastSearchTick!=Long.MIN_VALUE &&
                tick-cast.lastSearchTick<tuning.searchIntervalTicks()))return;
            cast.lastSearchTick=tick;
            selectCandidate(cast,tick,attraction,listener);
            return;
        }
        FishAgent fish=population.get(cast.fishId);
        if(fish==null || fish.state()==FishAgent.State.DESPAWNED || fish.state()==FishAgent.State.CAUGHT ||
            !water.traversable(fish.cell(),cast.zoneId)){
            lostFish(cast,tick,listener);return;
        }
        if(cast.phase==Phase.INTERESTED){
            if(tick-cast.phaseTick<tuning.interestTicks())return;
            if(!fish.approaching(cast.hookId)){lostFish(cast,tick,listener);return;}
            cast.phase=Phase.APPROACHING;cast.phaseTick=tick;
            listener.phase(cast.playerId,cast.hookId,cast.phase,cast.fishKey);
        }
        if(cast.phase==Phase.APPROACHING){
            if(fish.nextCell()!=null && !water.traversable(fish.nextCell(),cast.zoneId)){
                lostFish(cast,tick,listener);return;
            }
            if(fish.nextCell()==null && !cast.route.isEmpty()){
                WaterPoint next=cast.route.removeFirst();
                if(!water.traversable(next,cast.zoneId) || !fish.approachTo(cast.hookId,next)){
                    lostFish(cast,tick,listener);return;
                }
            }
            if(fish.nextCell()==null && cast.route.isEmpty())fish.recenterAtGoal(cast.hookId,cast.goal);
            fish.move(tick,tuning.swimSpeed());
            if(fish.nextCell()==null && cast.route.isEmpty() &&
                fish.position().distanceSquared(cast.goal.swimmingPosition())<=tuning.biteRadius()*tuning.biteRadius()){
                if(!fish.readyToBite(cast.hookId)){lostFish(cast,tick,listener);return;}
                cast.phase=Phase.BITE_READY;cast.biteTick=tick;cast.phaseTick=tick;
                listener.phase(cast.playerId,cast.hookId,cast.phase,cast.fishKey);
            }
        } else if(cast.phase==Phase.BITE_READY && tick>cast.biteTick+tuning.reactionWindowTicks()+tuning.graceTicks()) {
            finish(cast,Outcome.MISSED_WINDOW,tick,listener);
        }
    }

    /** Reel is consumed exactly once; no client packet/metadata can call this method directly. */
    public Outcome reel(UUID playerId,UUID hookId,long tick,Listener listener){
        Cast cast=casts.get(playerId);
        if(cast==null || !cast.hookId.equals(hookId))return Outcome.NONE;
        if(cast.phase==Phase.COMMITTING)return Outcome.NONE;
        if(cast.phase!=Phase.BITE_READY){finish(cast,Outcome.EARLY_REEL,tick,listener);return Outcome.EARLY_REEL;}
        if(tick>cast.biteTick+tuning.reactionWindowTicks()+tuning.graceTicks()){
            finish(cast,Outcome.MISSED_WINDOW,tick,listener);return Outcome.MISSED_WINDOW;
        }
        if(population.consume(cast.fishId,cast.hookId)){
            // FishKey is retained in cast only for diagnostics. No EMF reward is generated.
            finish(cast,Outcome.SUCCESS_PREVIEW_ONLY,tick,listener);return Outcome.SUCCESS_PREVIEW_ONLY;
        }
        finish(cast,Outcome.LOST_FISH,tick,listener);return Outcome.LOST_FISH;
    }


    /**
     * Phase 7: one server-authoritative commit, bound to the existing agent identity.
     * A cancelled EMF event returns null from prepare(), so the fish is RELEASED,
     * not consumed. Once committed, an unsuccessful delivery fails closed (no
     * second award/retry) and can be inspected in diagnostics.
     */
    public Outcome reelWithCommit(UUID playerId, UUID hookId, long tick,
                                  Listener listener, CatchCommitter committer) {
        Objects.requireNonNull(committer, "committer");
        Cast cast=casts.get(playerId);
        if(cast==null || !cast.hookId.equals(hookId))return Outcome.NONE;
        if(cast.phase==Phase.COMMITTING)return Outcome.NONE;
        if(cast.phase!=Phase.BITE_READY){finish(cast,Outcome.EARLY_REEL,tick,listener);return Outcome.EARLY_REEL;}
        if(tick>cast.biteTick+tuning.reactionWindowTicks()+tuning.graceTicks()){
            finish(cast,Outcome.MISSED_WINDOW,tick,listener);return Outcome.MISSED_WINDOW;
        }
        FishAgent fish=population.get(cast.fishId);
        if(fish==null || fish.state()!=FishAgent.State.BITE_READY ||
            !fish.reservedFor(hookId) || !fish.fishKey().equals(cast.fishKey) ||
            !fish.zoneId().equals(cast.zoneId) || !fish.worldId().equals(cast.worldId)){
            finish(cast,Outcome.LOST_FISH,tick,listener);return Outcome.LOST_FISH;
        }
        View frozen=cast.view();
        cast.phase=Phase.COMMITTING;
        PreparedCatch plan;
        try { plan=committer.prepare(frozen,fish); }
        catch (RuntimeException exception) {
            finish(cast,Outcome.COMMIT_FAILED,tick,listener);return Outcome.COMMIT_FAILED;
        }
        // A synchronous EMF event handler is allowed to cancel the session/reload
        // the plugin. Never deliver anything after such a reentrant invalidation.
        if(casts.get(playerId)!=cast)return Outcome.NONE;
        try {
            if(plan==null || !plan.ready()){
                finish(cast,Outcome.COMMIT_REJECTED,tick,listener);return Outcome.COMMIT_REJECTED;
            }
            if(!population.consume(fish.id(),hookId)){
                finish(cast,Outcome.LOST_FISH,tick,listener);return Outcome.LOST_FISH;
            }
            // The consumed agent is no longer targetable by any other player.
            boolean delivered=plan.deliver();
            Outcome result=delivered ? Outcome.SUCCESS_COMMITTED : Outcome.COMMIT_FAILED;
            finish(cast,result,tick,listener);
            return result;
        } catch (RuntimeException exception) {
            finish(cast,Outcome.COMMIT_FAILED,tick,listener);return Outcome.COMMIT_FAILED;
        }
    }

    /** Cleans reservation on quit/reload/zone-exit; no callback or reward side-effects. */
    public boolean abort(UUID playerId){
        Cast cast=casts.remove(playerId);
        if(cast==null)return false;
        release(cast,false);return true;
    }
    public void clear(){for(UUID playerId:List.copyOf(casts.keySet()))abort(playerId);nextCastTick.clear();}

    private void selectCandidate(Cast cast,long tick,ToDoubleFunction<FishAgent> attraction,Listener listener){
        Vec3 goal=cast.goal.swimmingPosition();
        double maxDistanceSquared=tuning.attractionRadius()*tuning.attractionRadius();
        List<FishAgent> nearby=population.agents().stream()
            .filter(a->a.state()==FishAgent.State.WANDERING && a.zoneId().equals(cast.zoneId)
                && a.worldId().equals(cast.worldId) && a.visibleTo(cast.playerId) && a.cell()!=null
                && a.position().distanceSquared(goal)<=maxDistanceSquared)
            .sorted(Comparator.comparingDouble((FishAgent a)->a.position().distanceSquared(goal))
                .thenComparing(a->a.id().toString()))
            .limit(tuning.maxCandidates()).toList();
        FishAgent winner=null;List<WaterPoint> route=null;double best=Double.MAX_VALUE;
        for(FishAgent fish:nearby){
            if(!water.traversable(fish.cell(),cast.zoneId))continue;
            double score=attraction.applyAsDouble(fish);
            if(!Double.isFinite(score) || score<=0)continue;
            List<WaterPoint> candidateRoute=findPath(fish.cell(),cast.goal,cast.zoneId);
            if(candidateRoute==null)continue;
            double rank=fish.position().distanceSquared(goal)/Math.min(score,16.0);
            if(rank<best){winner=fish;route=candidateRoute;best=rank;}
        }
        if(winner==null || !population.reserve(winner.id(),cast.hookId,cast.playerId))return;
        if(!winner.interested(cast.hookId)){
            population.release(winner.id(),cast.hookId);return;
        }
        cast.fishId=winner.id();cast.fishKey=winner.fishKey();cast.route=new ArrayDeque<>(route);
        cast.phase=Phase.INTERESTED;cast.phaseTick=tick;
        listener.phase(cast.playerId,cast.hookId,cast.phase,cast.fishKey);
    }

    /** BFS has a strict node budget; no path through unloaded/unindexed water or land. */
    private List<WaterPoint> findPath(WaterPoint start,WaterPoint goal,String zoneId){
        if(!water.traversable(start,zoneId) || !water.traversable(goal,zoneId) ||
            !start.worldId().equals(goal.worldId()))return null;
        if(start.equals(goal))return List.of();
        Queue<WaterPoint> todo=new ArrayDeque<>();Set<WaterPoint> seen=new HashSet<>();
        Map<WaterPoint,WaterPoint> previous=new HashMap<>();
        todo.add(start);seen.add(start);
        while(!todo.isEmpty() && seen.size()<=tuning.maxPathNodes()){
            WaterPoint current=todo.remove();
            for(WaterPoint next:water.adjacent(current)){
                if(!water.traversable(next,zoneId) || !sameStep(current,next) || !seen.add(next))continue;
                if(seen.size()>tuning.maxPathNodes())return null;
                previous.put(next,current);
                if(next.equals(goal)){
                    List<WaterPoint> path=new ArrayList<>();
                    for(WaterPoint cursor=goal;!cursor.equals(start);cursor=previous.get(cursor))path.add(cursor);
                    java.util.Collections.reverse(path);return path;
                }
                todo.add(next);
            }
        }
        return null;
    }
    private static boolean sameStep(WaterPoint a,WaterPoint b){
        return a.worldId().equals(b.worldId()) &&
            Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())==1 && Math.abs(a.y()-b.y())<=1;
    }
    private void lostFish(Cast cast,long tick,Listener listener){
        release(cast,false);cast.fishId=null;cast.fishKey=null;cast.route.clear();
        if(++cast.retargets>tuning.maxRetargets())finish(cast,Outcome.LOST_FISH,tick,listener);
        else {cast.phase=Phase.SEARCHING;cast.lastSearchTick=Long.MIN_VALUE;cast.phaseTick=tick;
            listener.phase(cast.playerId,cast.hookId,cast.phase,null);}
    }
    private void finish(Cast cast,Outcome outcome,long tick,Listener listener){
        if(casts.remove(cast.playerId)!=cast)return; // late/repeated terminal event
        if(outcome!=Outcome.SUCCESS_PREVIEW_ONLY)release(cast,true);
        nextCastTick.put(cast.playerId,tick+tuning.castCooldownTicks());
        if(outcome==Outcome.SUCCESS_PREVIEW_ONLY || outcome==Outcome.SUCCESS_COMMITTED)successes++;
        else if(outcome==Outcome.MISSED_WINDOW)misses++;
        else if(outcome!=Outcome.ABORTED)failures++;
        listener.finished(cast.playerId,cast.hookId,outcome,cast.fishKey);
    }
    private void release(Cast cast,boolean flee){
        if(cast.fishId==null)return;
        FishAgent agent=population.get(cast.fishId);
        if(agent!=null && population.release(agent.id(),cast.hookId) && flee && agent.cell()!=null){
            for(WaterPoint next:water.adjacent(agent.cell()))if(water.traversable(next,cast.zoneId) && agent.fleeTo(next))break;
        }
    }
}
