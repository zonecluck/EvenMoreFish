import com.oheers.fish.fishing.simulator.visual.PacketCleanupQueue;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import com.oheers.fish.fishing.simulator.population.Vec3;
import com.oheers.fish.fishing.simulator.visual.PacketVisibilityService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Phase 16 real Java domain regression; no Minecraft server or fake TPS claims. */
public final class SimulatorPhase16Smoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class Backend implements PacketTransport {
        final List<Integer> attempted = new ArrayList<>();
        int permanentlyBroken = -1;
        boolean throwOnFailure;
        public boolean available() { return true; }
        public boolean spawn(UUID viewer, FishAgent fish) { return false; }
        public boolean teleport(UUID viewer, FishAgent fish) { return false; }
        public boolean destroy(UUID viewer, int id) {
            attempted.add(id);
            if (id == permanentlyBroken) {
                if (throwOnFailure) throw new IllegalStateException("deliberate packet exception");
                return false;
            }
            return true;
        }
    }

    private static void permanentFailureMustNotStarveOtherViewers() {
        PacketCleanupQueue queue = new PacketCleanupQueue(6);
        Backend backend = new Backend();
        backend.permanentlyBroken = 1;
        backend.throwOnFailure = true;
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        check(queue.record(a, 1) && queue.record(b, 2) && queue.record(c, 3), "initial records");
        for (int tick = 0; tick < 3; tick++) {
            PacketCleanupQueue.RetryResult result = queue.retry(backend, 1);
            check(result.attempts() == 1, "one destroy attempt per tick");
        }
        check(backend.attempted.equals(List.of(1, 2, 3)), "cleanup queue must rotate across viewers");
        check(queue.pending() == 1 && queue.pendingFor(a) && !queue.pendingFor(b)
            && !queue.pendingFor(c), "only permanently broken viewer should remain");
        check(queue.record(a, 1) && queue.pending() == 1, "retry record is idempotent");
        backend.permanentlyBroken = -1;
        check(queue.retry(backend, 1).cleared() == 1 && queue.pending() == 0, "recovered cleanup");
    }

    private static void sameViewerFailuresAlsoRotate() {
        PacketCleanupQueue queue = new PacketCleanupQueue(200);
        Backend backend = new Backend();
        backend.permanentlyBroken = 0;
        UUID viewer = UUID.randomUUID();
        for (int i = 0; i < 100; i++) check(queue.record(viewer, i), "queue capacity");
        int largest = -1;
        for (int tick = 0; tick < 20; tick++) {
            var result = queue.retry(backend, 5);
            check(result.attempts() <= 5, "budget exceeded");
            for (int attempted : backend.attempted) largest = Math.max(largest, attempted);
        }
        check(largest == 99 && queue.pending() == 1, "all healthy IDs must drain behind stuck ID");
        check(queue.pendingFor(viewer), "failed ID must remain tracked");
        queue.forgetDisconnected(viewer);
        check(queue.pending() == 0 && !queue.pendingFor(viewer), "disconnect clears viewer ledger");
    }

    private static void capacityAndIsolation() {
        PacketCleanupQueue queue = new PacketCleanupQueue(2);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        check(queue.record(a, 10) && queue.record(a, 10), "idempotent insert");
        check(queue.record(b, 20) && !queue.record(a, 30), "never evict at capacity");
        queue.forgetDisconnected(a);
        check(queue.pending() == 1 && queue.pendingFor(b), "viewer cleanup affected another viewer");
        check(queue.record(a, 30), "reclaimed capacity");
        Backend backend = new Backend();
        check(queue.retry(backend, Integer.MAX_VALUE).attempts() == 2,
            "initial queue length bounds attempts even with huge budget");
        check(queue.pending() == 0, "healthy cleanup must converge");
    }

    private static void shutdownQueueOverflowNeverLosesEntityIds() {
        final int count = 4200; // deliberately exceeds PacketVisibilityService's 4096-ID retry queue
        UUID viewer = UUID.randomUUID(), world = UUID.randomUUID();
        java.util.Set<Integer> clientIds = new java.util.HashSet<>();
        final boolean[] unavailable = {true};
        PacketTransport transport = new PacketTransport() {
            public boolean available() { return true; }
            public boolean spawn(UUID owner, FishAgent fish) {
                clientIds.add(fish.entityId()); return true;
            }
            public boolean teleport(UUID owner, FishAgent fish) { return true; }
            public boolean destroy(UUID owner, int entityId) {
                if (unavailable[0]) return false;
                clientIds.remove(entityId); return true;
            }
        };
        PacketVisibilityService visibility = new PacketVisibilityService(transport, count, 9000, 100);
        var watcher = new PacketVisibilityService.Viewer(viewer, world, "lake", new Vec3(0, 63, 0));
        List<FishAgent> agents = new ArrayList<>();
        FishKey key = new FishKey("Rare", "Koi");
        for (int i = 0; i < count; i++) {
            agents.add(new FishAgent(UUID.randomUUID(), 1_400_000_000 + i,
                key, FishVisualModel.COD, "lake", world, null, 99999,
                new Vec3((i % 35) + .1, 63.0, (i / 35) * .3)));
        }
        visibility.beginTick();
        visibility.sync(List.of(watcher), agents, 1, false);
        check(visibility.totalVisible() == count && clientIds.size() == count,
            "warmup must track all virtual entity IDs");
        visibility.clearBestEffort();
        check(visibility.pendingDestroys() == count,
            "full cleanup queue must not discard overflow IDs");
        check(visibility.totalVisible() == count - 4096,
            "overflow IDs must remain in the tracked visibility map");
        unavailable[0] = false;
        visibility.clearBestEffort();
        check(visibility.pendingDestroys() == 0 && visibility.totalVisible() == 0 && clientIds.isEmpty(),
            "recovered packet backend must drain all IDs, including queue overflow");
    }

    private static void retiredViewerDisconnectReleasesBlocker() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), world = UUID.randomUUID();
        PacketTransport unavailable = new PacketTransport() {
            public boolean available() { return true; }
            public boolean spawn(UUID id, FishAgent fish) { return true; }
            public boolean teleport(UUID id, FishAgent fish) { return true; }
            public boolean destroy(UUID id, int entityId) { return false; }
        };
        PacketVisibilityService visibility = new PacketVisibilityService(unavailable, 1, 5, 20);
        FishAgent fish = new FishAgent(UUID.randomUUID(), 900001,
            new FishKey("Rare", "Koi"), FishVisualModel.COD, "lake", world, null,
            1000, new Vec3(1, 63, 1));
        visibility.beginTick();
        visibility.sync(List.of(
            new PacketVisibilityService.Viewer(a,world,"lake",new Vec3(0,63,0)),
            new PacketVisibilityService.Viewer(b,world,"lake",new Vec3(0,63,0))),
            List.of(fish),1,false);
        visibility.clearBestEffort();
        check(visibility.pendingDestroys()==2,"retired view still has two packet IDs");
        visibility.dropDisconnected(a);
        check(visibility.pendingDestroys()==1,"other viewer's unresolved entity must remain tracked");
        visibility.dropDisconnected(b);
        check(visibility.pendingDestroys()==0 && visibility.unconfirmedSpawns()==0,
            "all disconnected clients have cleared their local virtual entity tables");
    }

    public static void main(String[] args) {
        permanentFailureMustNotStarveOtherViewers();
        sameViewerFailuresAlsoRotate();
        capacityAndIsolation();
        shutdownQueueOverflowNeverLosesEntityIds();
        retiredViewerDisconnectReleasesBlocker();
        System.out.println("PASS Phase16: FIFO retry fairness, per-tick bound, deduplication, outage recovery, disconnect isolation and >4096-entity overflow cleanup");
    }
}
