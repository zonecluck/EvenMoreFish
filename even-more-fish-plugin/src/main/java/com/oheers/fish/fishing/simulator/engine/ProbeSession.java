package com.oheers.fish.fishing.simulator.engine;

import java.util.UUID;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Single-use event-ownership probe. It never computes/delivers any fish item. */
public final class ProbeSession {
    public enum State { WAITING, BITE_READY, FAILED, CLOSED }
    private final UUID playerId;
    private final UUID hookId;
    private final String zoneId;
    private final EquipmentSlot castHand;
    private final ItemStack rodFingerprint;
    private final int revision;
    private final long startTick;
    private State state = State.WAITING;
    private boolean closed;

    public ProbeSession(UUID playerId, UUID hookId, String zoneId, int revision, long startTick, EquipmentSlot castHand, ItemStack rodFingerprint) {
        this.playerId = playerId;
        this.hookId = hookId;
        this.zoneId = zoneId;
        this.castHand = castHand;
        this.rodFingerprint = rodFingerprint.clone();
        this.revision = revision;
        this.startTick = startTick;
    }
    public UUID playerId() { return playerId; }
    public UUID hookId() { return hookId; }
    public String zoneId() { return zoneId; }
    public int revision() { return revision; }
    public EquipmentSlot castHand() { return castHand; }
    public ItemStack rodFingerprint() { return rodFingerprint.clone(); }
    public long startTick() { return startTick; }
    public State state() { return state; }
    public boolean isClosed() { return closed; }
    public boolean markBiteReady() {
        if (state != State.WAITING || closed) return false;
        state = State.BITE_READY;
        return true;
    }
    public boolean close() {
        if (closed) return false;
        closed = true;
        state = State.CLOSED;
        return true;
    }
}
