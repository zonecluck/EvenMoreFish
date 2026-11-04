package com.oheers.fish.fishing.simulator.population;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Single-threaded, capacity-bounded population and reservation index. */
public final class FishPopulationService {
    private final int maxGlobal, maxPerZone, maxPrivateGlobal, maxPrivatePerViewer;
    private final Map<UUID, FishAgent> agents = new LinkedHashMap<>();
    private final Map<String, Integer> zoneCounts = new HashMap<>();
    private final Map<UUID, Integer> privateCounts = new HashMap<>();
    private int privateTotal;
    public FishPopulationService(int maxGlobal, int maxPerZone, int maxPrivateGlobal, int maxPrivatePerViewer) {
        if (maxGlobal<1 || maxPerZone<1 || maxPrivateGlobal<0 || maxPrivatePerViewer<0) throw new IllegalArgumentException("invalid population caps");
        this.maxGlobal=maxGlobal; this.maxPerZone=maxPerZone;
        this.maxPrivateGlobal=maxPrivateGlobal;this.maxPrivatePerViewer=maxPrivatePerViewer;
    }
    public boolean add(FishAgent agent) {
        if (agents.containsKey(agent.id()) || agents.size()>=maxGlobal || agent.state()!=FishAgent.State.WANDERING
            || zoneCounts.getOrDefault(agent.zoneId(),0)>=maxPerZone) return false;
        UUID owner=agent.privateOwner();
        if (owner != null && (privateTotal>=maxPrivateGlobal || privateCounts.getOrDefault(owner,0)>=maxPrivatePerViewer)) return false;
        agents.put(agent.id(),agent);
        zoneCounts.merge(agent.zoneId(),1,Integer::sum);
        if (owner!=null) {privateTotal++;privateCounts.merge(owner,1,Integer::sum);}
        return true;
    }
    public FishAgent get(UUID id){return agents.get(id);}
    public int size(){return agents.size();}
    public int zoneCount(String id){return zoneCounts.getOrDefault(id,0);}
    public int privateCount(){return privateTotal;}
    public int privateCount(UUID owner){return privateCounts.getOrDefault(owner,0);}
    public Collection<FishAgent> agents(){return List.copyOf(agents.values());}
    public boolean remove(UUID id) {
        FishAgent agent=agents.remove(id);
        if(agent==null)return false;
        agent.despawn();
        decrement(zoneCounts,agent.zoneId());
        if(agent.privateOwner()!=null){ privateTotal--;decrement(privateCounts,agent.privateOwner()); }
        return true;
    }
    private static <K> void decrement(Map<K,Integer> map,K key) {
        int value=map.getOrDefault(key,0)-1;
        if(value<=0)map.remove(key); else map.put(key,value);
    }
    public boolean reserve(UUID fishId,UUID session,UUID viewer) {
        FishAgent agent=get(fishId); return agent != null && agent.reserve(session,viewer);
    }
    public boolean release(UUID fishId,UUID session) {
        FishAgent agent=get(fishId);return agent!=null && agent.release(session);
    }
    public boolean consume(UUID fishId,UUID session) {
        FishAgent agent=get(fishId);
        if(agent==null || !agent.consume(session))return false;
        return remove(fishId);
    }
    public int expire(long tick) {
        List<UUID> expired=new ArrayList<>();
        for(FishAgent a: agents.values()) if(a.isExpired(tick) && !a.reserved())expired.add(a.id());
        expired.forEach(this::remove);return expired.size();
    }
    public int removeOwned(UUID owner) {
        List<UUID> ids=new ArrayList<>();
        for(FishAgent agent:agents.values())if(owner.equals(agent.privateOwner()))ids.add(agent.id());
        ids.forEach(this::remove);return ids.size();
    }
    public void clear(){for(UUID id:List.copyOf(agents.keySet()))remove(id);}
}
