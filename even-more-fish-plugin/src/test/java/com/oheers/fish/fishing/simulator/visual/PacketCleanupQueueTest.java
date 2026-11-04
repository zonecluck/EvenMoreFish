package com.oheers.fish.fishing.simulator.visual;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Repro: repeated head failure with a one-packet budget previously starved all other IDs. */
class PacketCleanupQueueTest {
    @Test void aBrokenViewerCannotStarveHealthyViewers() {
        PacketCleanupQueue queue = new PacketCleanupQueue(3);
        UUID stuck = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(queue.record(stuck, 1));
        assertTrue(queue.record(other, 2));
        assertTrue(queue.record(other, 3));
        List<Integer> attempts = new ArrayList<>();
        PacketTransport transport = new PacketTransport() {
            public boolean available() { return true; }
            public boolean spawn(UUID viewer, FishAgent fish) { return false; }
            public boolean teleport(UUID viewer, FishAgent fish) { return false; }
            public boolean destroy(UUID viewer, int entityId) {
                attempts.add(entityId);
                if (entityId == 1) throw new IllegalStateException("backend failure");
                return true;
            }
        };
        for (int i = 0; i < 3; i++) assertEquals(1, queue.retry(transport, 1).attempts());
        assertEquals(List.of(1, 2, 3), attempts);
        assertEquals(1, queue.pending());
        assertTrue(queue.pendingFor(stuck));
        assertFalse(queue.pendingFor(other));
        assertTrue(queue.record(stuck, 1));
        assertEquals(1, queue.pending());
        queue.forgetDisconnected(stuck);
        assertEquals(0, queue.pending());
    }

    @Test void idempotencyAndCapacityStayBoundedUnderFailures() {
        PacketCleanupQueue queue = new PacketCleanupQueue(2);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(queue.record(a, 12));
        assertTrue(queue.record(a, 12));
        assertTrue(queue.record(b, 13));
        assertFalse(queue.record(a, 14));
        PacketTransport broken = new PacketTransport() {
            public boolean available() { return true; }
            public boolean spawn(UUID id, FishAgent fish) { return false; }
            public boolean teleport(UUID id, FishAgent fish) { return false; }
            public boolean destroy(UUID id, int entityId) { return false; }
        };
        assertEquals(2, queue.retry(broken, 999).attempts());
        assertEquals(2, queue.pending());
        queue.forgetDisconnected(a);
        assertEquals(1, queue.pending());
        assertFalse(queue.pendingFor(a));
        assertTrue(queue.pendingFor(b));
    }
}
