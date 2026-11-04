package com.oheers.fish.fishing.simulator.visual;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.model.FishRenderSpec;
import com.oheers.fish.fishing.simulator.model.FishRenderPolicy;
import com.oheers.fish.fishing.simulator.population.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded per-viewer visibility for Paper's single owner thread. No Bukkit access.
 *
 * Phase 9: reconcile all stale entities BEFORE spending packets on spawns or
 * movement. Destruction is round-robin across viewers to prevent starvation
 * when a crowded lake saturates the packet budget. Failed destroys retain the
 * exact entity ID for retry on the next tick; they are not retried in a tight
 * loop during the same tick.
 */
public final class PacketVisibilityService {
    public record Viewer(UUID id, UUID worldId, String zoneId, Vec3 position) {}

    private static final class Seen {
        final int entityId;
        Vec3 sentPosition;
        long lastSentTick;
        final FishRenderSpec render;
        Seen(int entityId, Vec3 position, long tick, FishRenderSpec render) {
            this.entityId = entityId;
            this.sentPosition = position;
            this.lastSentTick = tick;
            this.render = render;
        }
    }

    private record Candidate(FishAgent agent, double distanceSquared) {}
    private record Desired(Viewer viewer, Map<UUID, Seen> seen,
                           List<FishAgent> candidates, ArrayDeque<UUID> stale) {}

    private static final Comparator<Candidate> NEAREST = Comparator
        .comparingDouble(Candidate::distanceSquared)
        .thenComparing(candidate -> candidate.agent().id());

    private final PacketTransport transport;
    private final FishRenderPolicy modelPolicy;
    private long modelReplacements;
    // An unsuccessful spawn may still have created a client-side entity.
    private final PacketCleanupQueue unconfirmedSpawns = new PacketCleanupQueue(4096);
    private final Map<UUID, Map<UUID, Seen>> visible = new HashMap<>();
    private final int maxVisible, packetsPerTick;
    private final double maxDistanceSquared;
    private int usedPackets;
    private int peakPacketUsage;
    private int pendingDestroys;
    private long totalSent;
    private long deferred;
    private long transportFailures;
    private long destroyFailures;
    private long reconciliationTicks;
    private long rotation;

    public PacketVisibilityService(PacketTransport transport, int maxVisible, int packetsPerTick, double viewDistance) {
        this(transport,maxVisible,packetsPerTick,viewDistance,null);
    }
    public PacketVisibilityService(PacketTransport transport, int maxVisible, int packetsPerTick, double viewDistance,
                                   FishRenderPolicy modelPolicy) {
        if (transport == null || maxVisible < 1 || packetsPerTick < 2 ||
            !Double.isFinite(viewDistance) || viewDistance <= 0) {
            throw new IllegalArgumentException("invalid visibility limits");
        }
        this.transport = transport;
        this.modelPolicy = modelPolicy;
        this.maxVisible = maxVisible;
        this.packetsPerTick = packetsPerTick;
        this.maxDistanceSquared = viewDistance * viewDistance;
    }

    public void beginTick() { usedPackets = 0; }
    public int totalVisible() { return visible.values().stream().mapToInt(Map::size).sum(); }
    public int viewers() { return visible.size(); }
    /** Only show passive fish FX once its entity has actually been spawned to this viewer. */
    public boolean shownTo(UUID viewerId,UUID fishId) {
        Map<UUID,Seen> seen=visible.get(viewerId);
        return seen!=null && seen.containsKey(fishId);
    }
    public long packetsSent() { return totalSent; }
    public long packetsDeferred() { return deferred; }
    public long modelReplacements(){ return modelReplacements; }
    public long failures() { return transportFailures; }
    public long destroyFailures() { return destroyFailures; }
    public int pendingDestroys() { return pendingDestroys; }
    public int unconfirmedSpawns() { return unconfirmedSpawns.pending(); }
    public int usedThisTick() { return usedPackets; }
    public int peakPacketUsage() { return peakPacketUsage; }
    public long reconciliationTicks() { return reconciliationTicks; }

    private boolean reservePackets(int count) {
        if (usedPackets > packetsPerTick - count) {
            deferred++;
            return false;
        }
        usedPackets += count;
        peakPacketUsage = Math.max(peakPacketUsage, usedPackets);
        return true;
    }

    /**
     * Select the nearest N agents in O(agentCount * log N) time and O(N) extra
     * memory, instead of sorting/allocating every fish for every viewer.
     */
    private List<FishAgent> nearest(Viewer viewer, List<FishAgent> agents) {
        PriorityQueue<Candidate> heap = new PriorityQueue<>(Math.min(maxVisible + 1, 64), NEAREST.reversed());
        for (FishAgent agent : agents) {
            if (agent.state() == FishAgent.State.DESPAWNED || !agent.visibleTo(viewer.id()) ||
                !agent.worldId().equals(viewer.worldId()) || !agent.zoneId().equals(viewer.zoneId())) continue;
            double distance = agent.position().distanceSquared(viewer.position());
            if (!Double.isFinite(distance) || distance > maxDistanceSquared) continue;
            Candidate candidate = new Candidate(agent, distance);
            if (heap.size() < maxVisible) heap.add(candidate);
            else if (NEAREST.compare(candidate, heap.peek()) < 0) {
                heap.poll();
                heap.add(candidate);
            }
        }
        return heap.stream().sorted(NEAREST).map(Candidate::agent).toList();
    }

    /** Visibility is authoritative only for visuals; it never authorizes a catch. */
    public void sync(List<Viewer> viewers, List<FishAgent> agents, long tick, boolean move) {
        // Compensate partial spawns before any new spawn/movement. These IDs
        // are not in `visible`, so the ordinary stale pass cannot destroy them.
        var cleanup = unconfirmedSpawns.retry(transport, packetsPerTick - usedPackets);
        usedPackets += cleanup.attempts();
        peakPacketUsage = Math.max(peakPacketUsage, usedPackets);
        totalSent += cleanup.cleared();
        transportFailures += cleanup.failed();
        destroyFailures += cleanup.failed();
        Set<UUID> active = new HashSet<>();
        for (Viewer viewer : viewers) active.add(viewer.id());
        // Permit callers to use sync() alone; inactive viewers must be cleaned
        // before active viewers can spend packets on new fish.
        hideOtherViewers(active);

        if (unconfirmedSpawns.pending() > 0) {
            pendingDestroys = unconfirmedSpawns.pending();
            return; // fail closed while a client may still hold an orphan ID
        }

        List<Desired> desired = new ArrayList<>(viewers.size());
        Set<UUID> unique = new HashSet<>();
        for (Viewer viewer : viewers) {
            if (!unique.add(viewer.id())) continue;
            Map<UUID, Seen> seen = visible.computeIfAbsent(viewer.id(), ignored -> new HashMap<>());
            List<FishAgent> candidates = nearest(viewer, agents);
            Set<UUID> wanted = new HashSet<>();
            for (FishAgent fish : candidates) wanted.add(fish.id());
            ArrayDeque<UUID> stale = new ArrayDeque<>();
            for (UUID id : seen.keySet()) if (!wanted.contains(id)) stale.addLast(id);
            // A pack status change MUST destroy the previous entity type first.
            for (FishAgent candidate : candidates) {
                Seen old=seen.get(candidate.id());
                if(old!=null && !old.render.equals(render(viewer.id(),candidate))) {
                    stale.addLast(candidate.id());
                }
            }
            desired.add(new Desired(viewer, seen, candidates, stale));
        }

        // Destroy-first globally, round-robin one stale ID per viewer per pass.
        // Retain failures and budget-deferred IDs for the following tick.
        if (!desired.isEmpty()) {
            int offset = (int) Math.floorMod(rotation++, desired.size());
            boolean more;
            do {
                more = false;
                for (int i = 0; i < desired.size(); i++) {
                    Desired row = desired.get((i + offset) % desired.size());
                    UUID id = row.stale().pollFirst();
                    if (id == null) continue;
                    more = true;
                    if (!reservePackets(1)) {
                        // Remaining rows are kept in the tracked visible map.
                        countPendingDestroys(desired, active);
                        return;
                    }
                    Seen old = row.seen().get(id);
                    if (old != null) {
                        if (destroySafely(row.viewer().id(), old.entityId)) {
                            // Count a replacement only after the obsolete visual is actually removed.
                            for (FishAgent candidate : row.candidates()) {
                                if (candidate.id().equals(id) &&
                                    !old.render.equals(render(row.viewer().id(), candidate))) {
                                    modelReplacements++;
                                    break;
                                }
                            }
                            row.seen().remove(id);
                        }
                    }
                }
            } while (more);
        }

        countPendingDestroys(desired, active);
        if (pendingDestroys > 0) return; // don't spend the remainder on new spawn/move until old fish are gone

        // Rotate spawn/move priority each tick so early UUIDs cannot monopolise
        // a small packet budget under a burst of simultaneous new viewers.
        int start = desired.isEmpty() ? 0 : (int) Math.floorMod(tick, desired.size());
        for (int i = 0; i < desired.size(); i++) {
            Desired row = desired.get((i + start) % desired.size());
            for (FishAgent agent : row.candidates()) {
                Seen present = row.seen().get(agent.id());
                if (present == null) {
                    if (row.seen().size() >= maxVisible || !unconfirmedSpawns.hasCapacity() ||
                        unconfirmedSpawns.pendingFor(row.viewer().id()) || !reservePackets(2)) continue;
                    boolean spawned;
                    FishRenderSpec visual=render(row.viewer().id(),agent);
                    try { spawned = transport.spawn(row.viewer().id(), agent, visual); }
                    catch (RuntimeException | LinkageError error) { spawned = false; }
                    if (spawned) {
                        row.seen().put(agent.id(), new Seen(agent.entityId(), agent.position(), tick, visual));
                        totalSent += 2; // spawn + metadata packet
                    } else {
                        transportFailures++;
                        // Destroy is harmless if spawn never reached the client.
                        // If it DID, this ID must not be forgotten.
                        if (!unconfirmedSpawns.record(row.viewer().id(), agent.entityId()))
                            throw new IllegalStateException("Unconfirmed fish spawn cannot be tracked");
                        break;
                    }
                } else if (move) {
                    double delta = present.sentPosition.distanceSquared(agent.position());
                    double distance = agent.position().distanceSquared(row.viewer().position());
                    int lod = distance <= 16 * 16 ? 2 : (distance <= 32 * 32 ? 5 : 12);
                    if (delta >= .0025 && tick - present.lastSentTick >= lod && reservePackets(1)) {
                        boolean moved;
                        try { moved = transport.teleport(row.viewer().id(), agent); }
                        catch (RuntimeException | LinkageError error) { moved = false; }
                        if (moved) {
                            present.sentPosition = agent.position();
                            present.lastSentTick = tick;
                            totalSent++;
                        } else transportFailures++;
                    }
                }
            }
            if (row.seen().isEmpty()) visible.remove(row.viewer().id());
        }
        reconciliationTicks++;
    }

    private void countPendingDestroys(List<Desired> desired, Set<UUID> active) {
        int count = 0;
        for (Desired row : desired) {
            Set<UUID> wanted = new HashSet<>();
            for (FishAgent fish : row.candidates()) wanted.add(fish.id());
            for (var item : row.seen().entrySet()) {
                FishAgent fish=null;
                for(FishAgent candidate : row.candidates())if(candidate.id().equals(item.getKey())){fish=candidate;break;}
                if(!wanted.contains(item.getKey()) ||
                    (fish!=null && !item.getValue().render.equals(render(row.viewer().id(),fish))))count++;
            }
        }
        for (var entry : visible.entrySet()) if (!active.contains(entry.getKey())) count += entry.getValue().size();
        pendingDestroys = count + unconfirmedSpawns.pending();
    }

    private FishRenderSpec render(UUID viewer,FishAgent agent){
        return modelPolicy==null ? FishRenderSpec.vanilla(agent.model()) : modelPolicy.choose(viewer,agent);
    }
    /** Inactive viewers are evicted before rendering the remaining ones. */
    public void hideOtherViewers(Set<UUID> activeIds) {
        for (UUID viewer : List.copyOf(visible.keySet())) {
            if (activeIds.contains(viewer)) continue;
            Map<UUID, Seen> seen = visible.get(viewer);
            if (seen == null) continue;
            for (var iterator = seen.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                if (!reservePackets(1)) break;
                if (destroySafely(viewer, entry.getValue().entityId)) iterator.remove();
            }
            if (seen.isEmpty()) visible.remove(viewer);
        }
    }

    /** One broken viewer/backend must not prevent cleanup for every other viewer. */
    private boolean destroySafely(UUID viewer, int entityId) {
        boolean delivered;
        try { delivered = transport.destroy(viewer, entityId); }
        catch (RuntimeException | LinkageError error) { delivered = false; }
        if (delivered) totalSent++;
        else { transportFailures++; destroyFailures++; }
        return delivered;
    }

    /** A disconnected client discards its own entities; no packet needs sending. */
    public void dropDisconnected(UUID viewer) {
        visible.remove(viewer);
        unconfirmedSpawns.forgetDisconnected(viewer);
        // Do not announce zero outstanding destroys merely because ONE viewer
        // disconnected. Preserve a conservative bound for other viewers, while
        // allowing a retired visibility instance to clear as soon as ALL of its
        // viewers have disconnected (there is then no client entity state left).
        int stillTracked = totalVisible() + unconfirmedSpawns.pending();
        pendingDestroys = Math.min(stillTracked,
            Math.max(pendingDestroys, unconfirmedSpawns.pending()));
    }

    /**
     * Shutdown cleanup is exempt from the soft packet budget. A full retry
     * ledger must NEVER cause unresolved client IDs to be forgotten. IDs that
     * do not fit in the bounded retry queue stay in the tracked visible map,
     * where subsequent staging cleanup passes can retry them.
     */
    public void clearBestEffort() {
        for (var viewer : List.copyOf(visible.entrySet())) {
            Map<UUID, Seen> tracked = viewer.getValue();
            for (var iterator = tracked.entrySet().iterator(); iterator.hasNext();) {
                Seen item = iterator.next().getValue();
                if (destroySafely(viewer.getKey(), item.entityId) ||
                    unconfirmedSpawns.record(viewer.getKey(), item.entityId)) {
                    iterator.remove();
                }
                // On queue overflow retain the Seen record. Destroy may have
                // failed after packets reached the client, so losing the ID is unsafe.
            }
            if (tracked.isEmpty()) visible.remove(viewer.getKey());
        }
        // Try a bounded second pass; failures remain visible in diagnostics.
        var cleanup = unconfirmedSpawns.retry(transport, 4096);
        totalSent += cleanup.cleared();
        transportFailures += cleanup.failed();
        destroyFailures += cleanup.failed();
        pendingDestroys = unconfirmedSpawns.pending() + totalVisible();
    }
}
