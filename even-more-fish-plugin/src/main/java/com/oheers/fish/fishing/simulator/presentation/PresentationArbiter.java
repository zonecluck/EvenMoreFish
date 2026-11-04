package com.oheers.fish.fishing.simulator.presentation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper-owner-thread presentation arbitration. No Bukkit calls, no timers and no player references.
 * One pending intent per player/channel; old sessions cannot post into replacement sessions.
 */
public final class PresentationArbiter {
    public enum Channel { ACTION_BAR }
    public enum Priority {
        AMBIENT(10), APPROACHING(20), ENCOUNTER(30), FAILED_REEL(70), BITE_READY(90), CATCH_RESULT(100);
        private final int weight;
        Priority(int weight) { this.weight = weight; }
    }
    public record Delivery(UUID playerId, Channel channel, String messageKey, Map<String, String> variables) {
        public Delivery {
            variables = Map.copyOf(variables);
        }
    }
    private record Payload(String messageKey, Map<String,String> variables) {
        private Payload { variables = Map.copyOf(variables); }
    }
    private static final class Slot {
        Priority priority;
        Payload payload;
        long expiresAt;
        long lastSentTick = Long.MIN_VALUE;
        int lastSentPriority = -1;
        Payload lastSentPayload;
    }
    private static final class Session {
        final UUID sessionId;
        final EnumMap<Channel, Slot> slots = new EnumMap<>(Channel.class);
        boolean closed;
        Session(UUID sessionId) { this.sessionId = sessionId; }
    }

    private final Map<UUID, Session> active = new HashMap<>();
    private long blockedLowerPriority;
    private long blockedStaleSession;
    private long duplicateDrops;
    private long emissions;

    public void begin(UUID playerId, UUID sessionId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        active.put(playerId, new Session(sessionId));
    }

    public boolean offer(UUID playerId, UUID sessionId, Channel channel, Priority priority,
                         String messageKey, Map<String,String> variables, long now, int ttlTicks) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(messageKey, "messageKey");
        Objects.requireNonNull(variables, "variables");
        if(ttlTicks < 1 || ttlTicks > 200 || messageKey.isBlank()) throw new IllegalArgumentException("Invalid UI intent");
        Session session = active.get(playerId);
        if(session == null || session.closed || !session.sessionId.equals(sessionId)) {
            blockedStaleSession++;
            return false;
        }
        Slot slot = session.slots.get(channel);
        if(slot == null) {
            slot = new Slot();
            session.slots.put(channel, slot);
        }
        if(slot.payload != null && now < slot.expiresAt && priority.weight < slot.priority.weight) {
            blockedLowerPriority++;
            return false;
        }
        Payload payload = new Payload(messageKey, variables);
        // Never reset the dedupe state on every countdown update; throttle is per channel.
        slot.priority = priority;
        slot.payload = payload;
        slot.expiresAt = now + ttlTicks;
        return true;
    }

    /** Returns only changed or preempting UI intents and never more than one per channel. */
    public List<Delivery> drain(long now, int minimumIntervalTicks) {
        if(minimumIntervalTicks < 1 || minimumIntervalTicks > 40) throw new IllegalArgumentException("minimumIntervalTicks");
        List<Delivery> result = new ArrayList<>();
        for(Map.Entry<UUID,Session> player : active.entrySet()) {
            Session session = player.getValue();
            Iterator<Map.Entry<Channel,Slot>> slots = session.slots.entrySet().iterator();
            while(slots.hasNext()) {
                Map.Entry<Channel,Slot> entry = slots.next();
                Slot slot = entry.getValue();
                if(now >= slot.expiresAt) { slots.remove(); continue; }
                if(slot.payload.equals(slot.lastSentPayload)) {
                    duplicateDrops++;
                    continue;
                }
                // Emergency bite and catch preempt lower-priority text immediately.
                if(slot.lastSentPriority >= slot.priority.weight && slot.lastSentTick != Long.MIN_VALUE
                    && now - slot.lastSentTick < minimumIntervalTicks) continue;
                result.add(new Delivery(player.getKey(), entry.getKey(), slot.payload.messageKey,
                    slot.payload.variables));
                slot.lastSentTick = now;
                slot.lastSentPriority = slot.priority.weight;
                slot.lastSentPayload = slot.payload;
                emissions++;
            }
        }
        return result;
    }
    /** Close a fishing session. Keep a terminal catch message until its TTL expires. */
    public void close(UUID playerId, UUID sessionId) {
        Session session=active.get(playerId);
        if(session==null || !session.sessionId.equals(sessionId))return;
        session.closed=true;
        session.slots.values().removeIf(slot->slot.priority != Priority.CATCH_RESULT);
        if(session.slots.isEmpty())active.remove(playerId);
    }
    public void forget(UUID playerId) { active.remove(playerId); }
    public void clear() { active.clear(); }
    public int sessions() { return active.size(); }
    public long blockedLowerPriority() { return blockedLowerPriority; }
    public long blockedStaleSession() { return blockedStaleSession; }
    public long duplicateDrops() { return duplicateDrops; }
    public long emissions() { return emissions; }
}
