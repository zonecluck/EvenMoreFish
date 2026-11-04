package com.oheers.fish.fishing.simulator.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Cache of verified pack-id acknowledgements; caller MUST invoke only on Paper owner thread. */
public final class FishPackAudience {
    private final Set<UUID> ready=new HashSet<>();
    private UUID expectedPack;
    private long accepts, rejects;
    public void configure(UUID pack) {
        if (!java.util.Objects.equals(expectedPack,pack)) {ready.clear();expectedPack=pack;}
    }
    public void status(UUID player,UUID pack,boolean loaded) {
        if (player==null || !java.util.Objects.equals(expectedPack,pack))return;
        if(loaded) {ready.add(player);accepts++;}
        else {ready.remove(player);rejects++;}
    }
    public boolean ready(UUID player) {return expectedPack!=null && ready.contains(player);}
    public void disconnected(UUID player) {ready.remove(player);}
    public int readyCount(){return ready.size();}
    public long accepted(){return accepts;}
    public long rejected(){return rejects;}
}
