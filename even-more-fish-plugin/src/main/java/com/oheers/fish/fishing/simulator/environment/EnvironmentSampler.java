package com.oheers.fish.fishing.simulator.environment;

import com.oheers.fish.fishing.simulator.population.WaterPoint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owner-thread sampler: at most N world reads per tick; bounded coarse chunk/depth cache.
 * Source must check isChunkLoaded BEFORE reading blocks/biome. No forced chunk access.
 * A missing observation is non-authoritative and never changes catch odds.
 */
public final class EnvironmentSampler {
    @FunctionalInterface public interface Source {
        EnvironmentSnapshot readLoaded(WaterPoint point, long tick);
    }
    public record Limits(int perTick, int cacheEntries, int ttlTicks) {
        public Limits {
            if(perTick<1||perTick>32||cacheEntries<16||cacheEntries>2048||ttlTicks<20||ttlTicks>2400)
                throw new IllegalArgumentException("Invalid environment sample limits");
        }
    }
    private record Key(UUID worldId, int cx, int cz, int yBand) { }
    private final Limits limits;
    private final Source source;
    private final LinkedHashMap<Key,EnvironmentSnapshot> cache = new LinkedHashMap<>(32,.75f,true);
    private long activeTick = Long.MIN_VALUE;
    private int samplesThisTick;
    private long sourceReads, budgetDrops, unloadedOrInvalid, failedReads;

    public EnvironmentSampler(Limits limits, Source source) {
        this.limits=Objects.requireNonNull(limits);this.source=Objects.requireNonNull(source);
    }
    public EnvironmentSnapshot observe(WaterPoint cell,long tick) {
        if(cell==null)return null;
        if(activeTick!=tick) { activeTick=tick;samplesThisTick=0; }
        Key key=new Key(cell.worldId(),cell.chunkX(),cell.chunkZ(),cell.y()>>2);
        EnvironmentSnapshot found=cache.get(key);
        if(found!=null && tick>=found.sampledAtTick() && tick-found.sampledAtTick()<limits.ttlTicks())return found;
        if(samplesThisTick>=limits.perTick()) {budgetDrops++;return null;}
        samplesThisTick++;sourceReads++;
        EnvironmentSnapshot updated;
        try { updated=source.readLoaded(cell,tick); }
        catch(RuntimeException error) {
            // Environment is a cosmetic input: a biome/provider failure must NEVER cancel a catch.
            cache.remove(key); failedReads++; return null;
        }
        // A stale snapshot is intentionally NOT returned after chunk unload or a failed read.
        if(updated==null) {cache.remove(key);unloadedOrInvalid++;return null;}
        cache.put(key,updated);
        if(cache.size()>limits.cacheEntries())cache.remove(cache.keySet().iterator().next());
        return updated;
    }
    public void invalidate(UUID world,int cx,int cz) {
        cache.keySet().removeIf(key->key.worldId().equals(world) && key.cx()==cx && key.cz()==cz);
    }
    public void invalidateNear(UUID world,int cx,int cz) {
        cache.keySet().removeIf(key->key.worldId().equals(world) && Math.abs(key.cx()-cx)<=1
            && Math.abs(key.cz()-cz)<=1);
    }
    public void clear() {cache.clear();activeTick=Long.MIN_VALUE;samplesThisTick=0;}
    public int cached(){return cache.size();}
    public long reads(){return sourceReads;}
    public long budgetDrops(){return budgetDrops;}
    public long invalidSamples(){return unloadedOrInvalid;}
    public long failedReads(){return failedReads;}
}
