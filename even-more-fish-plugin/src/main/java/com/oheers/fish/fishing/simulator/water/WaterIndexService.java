package com.oheers.fish.fishing.simulator.water;

import com.oheers.fish.fishing.simulator.population.WaterPoint;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Incremental surface-water index. On the Paper server thread ONLY.
 * Never calls World.getChunkAt/loadChunk, and checks isChunkLoaded before each block access.
 * Each job examines one 16x16 chunk in small slices; caches are bounded and invalidatable.
 */
public final class WaterIndexService {
    public record ChunkKey(UUID worldId,int x,int z) {}
    private static final class ScanJob {
        final ChunkKey key;
        final List<WaterPoint> cells=new ArrayList<>();
        int column;
        ScanJob(ChunkKey key){this.key=key;}
    }
    private final int maxCachedChunks, maxQueue, maxCells, minDepth;
    private final Map<ChunkKey,List<WaterPoint>> cached = new LinkedHashMap<>(128,.75f,true);
    private final ArrayDeque<ScanJob> queue = new ArrayDeque<>();
    private final Set<ChunkKey> enqueued = new HashSet<>();
    private int cellCount;
    private long scannedColumns;
    private long droppedJobs;

    public WaterIndexService(int maxCachedChunks, int maxQueue, int maxCells, int minDepth) {
        if(maxCachedChunks<1||maxQueue<1||maxCells<1||minDepth<1) throw new IllegalArgumentException("invalid water index capacity");
        this.maxCachedChunks=maxCachedChunks;this.maxQueue=maxQueue;this.maxCells=maxCells;this.minDepth=minDepth;
    }
    public boolean request(World world,int cx,int cz) {
        if(world==null || !world.isChunkLoaded(cx,cz)) return false;
        ChunkKey key=new ChunkKey(world.getUID(),cx,cz);
        if(cached.containsKey(key) || enqueued.contains(key))return false;
        if(queue.size()>=maxQueue){droppedJobs++;return false;}
        enqueued.add(key);queue.addLast(new ScanJob(key));return true;
    }
    public void requestAround(World world,int cx,int cz,int radius) {
        for(int dist=0;dist<=radius;dist++) for(int dx=-dist;dx<=dist;dx++)for(int dz=-dist;dz<=dist;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))==dist)request(world,cx+dx,cz+dz);
        }
    }
    /** Return the number of actual columns inspected. The time budget is checked between columns. */
    public int scan(int maxColumns,long maxNanos) {
        long deadline=System.nanoTime()+maxNanos;
        int done=0;
        while(done<maxColumns && System.nanoTime()<deadline && !queue.isEmpty()) {
            ScanJob job=queue.peekFirst();
            World world=Bukkit.getWorld(job.key.worldId());
            if(world==null || !world.isChunkLoaded(job.key.x(),job.key.z())) {
                queue.removeFirst();enqueued.remove(job.key);continue;
            }
            int column=job.column++;
            int x=(job.key.x()<<4)+(column & 15);
            int z=(job.key.z()<<4)+(column >> 4);
            int top=Math.min(world.getMaxHeight()-1,world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE));
            // Depending on the heightmap, water may be at top or directly below it.
            for(int y=top;y>=Math.max(world.getMinHeight(),top-2);y--) {
                Block block=world.getBlockAt(x,y,z);
                if(block.getType()!=Material.WATER)continue;
                boolean deep=true;
                for(int d=1;d<minDepth;d++) {
                    if(y-d<world.getMinHeight() || world.getBlockAt(x,y-d,z).getType()!=Material.WATER){deep=false;break;}
                }
                if(deep)job.cells.add(new WaterPoint(world.getUID(),x,y,z));
                break;
            }
            done++;scannedColumns++;
            if(job.column>=256) {
                queue.removeFirst();enqueued.remove(job.key);
                if(!job.cells.isEmpty())putCache(job.key,job.cells);
                else putCache(job.key,List.of()); // cache negative results so unsuitable chunks aren't rescanned constantly
            }
        }
        return done;
    }
    private void putCache(ChunkKey key,List<WaterPoint> cells){
        List<WaterPoint> previous=cached.put(key,List.copyOf(cells));
        if(previous!=null)cellCount-=previous.size();
        cellCount+=cells.size();
        Iterator<Map.Entry<ChunkKey,List<WaterPoint>>> entries=cached.entrySet().iterator();
        while((cached.size()>maxCachedChunks || cellCount>maxCells) && entries.hasNext()){
            var old=entries.next();cellCount-=old.getValue().size();entries.remove();
        }
    }
    public WaterPoint randomNear(UUID worldId,int chunkX,int chunkZ,int radius) {
        var rng=ThreadLocalRandom.current();
        for(int attempt=0;attempt<24;attempt++) {
            int dx=rng.nextInt(-radius,radius+1), dz=rng.nextInt(-radius,radius+1);
            List<WaterPoint> cells=cached.get(new ChunkKey(worldId,chunkX+dx,chunkZ+dz));
            if(cells!=null && !cells.isEmpty())return cells.get(rng.nextInt(cells.size()));
        }
        return null;
    }
    /** Closest cached surface cell to a settled hook, without loading/generating chunks. */
    public WaterPoint nearest(UUID worldId,double x,double y,double z,int radius,
                              java.util.function.Predicate<WaterPoint> allowed) {
        if(radius<0 || radius>4)return null;
        int cx=((int)Math.floor(x))>>4,cz=((int)Math.floor(z))>>4;
        World world=Bukkit.getWorld(worldId);
        if(world==null)return null;
        WaterPoint closest=null;double best=Double.POSITIVE_INFINITY;
        int chunks=1; // radius <= 4: at most the adjacent 3x3 chunks can intersect the hook
        for(int dx=-chunks;dx<=chunks;dx++)for(int dz=-chunks;dz<=chunks;dz++){
            if(!world.isChunkLoaded(cx+dx,cz+dz))continue;
            List<WaterPoint> cells=cached.get(new ChunkKey(worldId,cx+dx,cz+dz));
            if(cells==null)continue;
            for(WaterPoint cell:cells){
                double distX=cell.x()+.5-x,distZ=cell.z()+.5-z;
                double distY=cell.y()+.32-y;
                if(Math.abs(distX)>radius+0.5 || Math.abs(distZ)>radius+0.5 || Math.abs(distY)>2.5
                    || !allowed.test(cell))continue;
                double distance=distX*distX+distY*distY+distZ*distZ;
                if(distance<best){closest=cell;best=distance;}
            }
        }
        return closest;
    }
    public boolean isIndexedWater(WaterPoint point) {
        List<WaterPoint> cells=cached.get(new ChunkKey(point.worldId(),point.chunkX(),point.chunkZ()));
        return cells!=null && cells.contains(point);
    }
    public List<WaterPoint> adjacent(WaterPoint around) {
        List<WaterPoint> list=new ArrayList<>(4);
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            int x=around.x()+d[0],z=around.z()+d[1];
            List<WaterPoint> cells=cached.get(new ChunkKey(around.worldId(),x>>4,z>>4));
            if(cells==null)continue;
            for(WaterPoint cell:cells)if(cell.x()==x && cell.z()==z && Math.abs(cell.y()-around.y())<=1){list.add(cell);break;}
        }
        return list;
    }
    public void invalidate(UUID world,int cx,int cz) {
        ChunkKey key=new ChunkKey(world,cx,cz);
        List<WaterPoint> removed=cached.remove(key);if(removed!=null)cellCount-=removed.size();
        if(enqueued.remove(key))queue.removeIf(job->job.key.equals(key));
    }
    public void invalidateNear(UUID world,int cx,int cz) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)invalidate(world,cx+dx,cz+dz);
    }
    public void clear(){cached.clear();queue.clear();enqueued.clear();cellCount=0;}
    public int queued(){return queue.size();}
    public int cachedChunks(){return cached.size();}
    public int cachedCells(){return cellCount;}
    public long scannedColumns(){return scannedColumns;}
    public long droppedJobs(){return droppedJobs;}
}
