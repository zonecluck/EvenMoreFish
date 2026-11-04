package com.oheers.fish.fishing.simulator.visual;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owner-thread, bounded and FAIR retry ledger for virtual packet entities.
 *
 * A failed spawn may already be visible on the client. Every unknown result
 * must therefore be recorded for cleanup before another entity is spawned.
 * Failed IDs rotate to the tail, so one permanently broken viewer cannot
 * starve another viewer when the per-tick cleanup budget is small.
 *
 * Do not call from an async thread. No data survives server shutdown; the
 * client resets virtual entities on disconnect/reconnect.
 */
public final class PacketCleanupQueue {
    public record RetryResult(int attempts, int cleared, int failed) {}
    private record Pending(UUID viewer, int entityId) {}

    private final int maximum;
    private final Deque<Pending> queue = new ArrayDeque<>();
    private final Set<Pending> membership = new HashSet<>();
    private final Map<UUID, Integer> perViewer = new HashMap<>();

    public PacketCleanupQueue(int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        this.maximum = maximum;
    }

    public int pending() { return membership.size(); }
    public boolean pendingFor(UUID viewer) { return perViewer.containsKey(viewer); }
    public boolean hasCapacity() { return membership.size() < maximum; }

    /** Idempotent record. Never evict another unresolved entity to make room. */
    public boolean record(UUID viewer, int entityId) {
        Objects.requireNonNull(viewer, "viewer");
        if (entityId < 0) throw new IllegalArgumentException("entityId must be nonnegative");
        Pending key = new Pending(viewer, entityId);
        if (membership.contains(key)) return true;
        if (!hasCapacity()) return false;
        membership.add(key);
        queue.addLast(key);
        perViewer.merge(viewer, 1, Integer::sum);
        return true;
    }

    /**
     * At most one attempt per pending ID per call, regardless of failures.
     * Complexity O(min(maxAttempts, pending)), including when the backend fails.
     */
    public RetryResult retry(PacketTransport transport, int maxAttempts) {
        Objects.requireNonNull(transport, "transport");
        if (maxAttempts <= 0 || queue.isEmpty()) return new RetryResult(0, 0, 0);
        int attempts = 0, cleared = 0, failed = 0;
        int budget = Math.min(maxAttempts, queue.size());
        for (int i = 0; i < budget; i++) {
            Pending key = queue.removeFirst();
            attempts++;
            boolean destroyed;
            try { destroyed = transport.destroy(key.viewer(), key.entityId()); }
            catch (RuntimeException | LinkageError error) { destroyed = false; }
            if (destroyed) {
                membership.remove(key);
                perViewer.computeIfPresent(key.viewer(), (ignored, count) -> count == 1 ? null : count - 1);
                cleared++;
            } else {
                queue.addLast(key);
                failed++;
            }
        }
        return new RetryResult(attempts, cleared, failed);
    }

    /** Call only on confirmed disconnect (client entity table is gone). */
    public void forgetDisconnected(UUID viewer) {
        if (perViewer.remove(viewer) == null) return;
        queue.removeIf(key -> {
            if (!key.viewer().equals(viewer)) return false;
            membership.remove(key);
            return true;
        });
    }
}
