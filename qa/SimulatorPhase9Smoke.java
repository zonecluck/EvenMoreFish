import com.oheers.fish.fishing.simulator.population.*;
import com.oheers.fish.fishing.simulator.visual.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Phase 9: headless fault injection and load simulation against real production
 * visibility/population classes. It is NOT a Paper server MSPT benchmark.
 */
public final class SimulatorPhase9Smoke {
    private static final String ZONE = "lake";
    private static final FishKey KEY = new FishKey("Rare", "Koi");
    private static final UUID WORLD = UUID.nameUUIDFromBytes("test-world".getBytes(StandardCharsets.UTF_8));

    private static final class Transport implements PacketTransport {
        final Map<UUID, Set<Integer>> perViewer = new HashMap<>();
        final List<String> packets = new ArrayList<>();
        boolean failDestroy;
        boolean throwDestroy;
        boolean partialSpawnFailure;
        boolean throwPartialSpawn;
        boolean throwTeleport;
        int destroyAttempts;
        int spawnAttempts;
        @Override public boolean available() { return true; }
        @Override public boolean spawn(UUID player, FishAgent fish) {
            spawnAttempts++;
            perViewer.computeIfAbsent(player, ignored -> new HashSet<>()).add(fish.entityId());
            packets.add("SPAWN:" + player + ":" + fish.entityId());
            if (throwPartialSpawn) throw new IllegalStateException("metadata packet failed after spawn");
            if (partialSpawnFailure) return false;
            return true;
        }
        @Override public boolean teleport(UUID player, FishAgent fish) {
            if (throwTeleport) throw new IllegalStateException("injected teleport backend error");
            check(perViewer.getOrDefault(player, Set.of()).contains(fish.entityId()), "teleport before spawn");
            packets.add("MOVE:" + player + ":" + fish.entityId());
            return true;
        }
        @Override public boolean destroy(UUID player, int entityId) {
            destroyAttempts++;
            if (throwDestroy) throw new IllegalStateException("injected packet backend error");
            if (failDestroy) return false;
            perViewer.computeIfAbsent(player, ignored -> new HashSet<>()).remove(entityId);
            packets.add("DESTROY:" + player + ":" + entityId);
            return true;
        }
        int visibleIds() { return perViewer.values().stream().mapToInt(Set::size).sum(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static UUID id(String name) { return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)); }
    private static FishAgent fish(int number, UUID privateOwner) {
        Vec3 at = new Vec3((number % 35) + .5, 63.4, (number / 35) + .5);
        return new FishAgent(id("fish-" + number), 1_500_000_000 + number, KEY,
            FishVisualModel.COD, ZONE, WORLD, privateOwner, 100000, at);
    }
    private static PacketVisibilityService.Viewer viewer(int number) {
        return new PacketVisibilityService.Viewer(id("viewer-" + number), WORLD, ZONE, new Vec3(17, 63.4, 12));
    }

    private static void destructionBeforeSpawn() {
        Transport transport = new Transport();
        var service = new PacketVisibilityService(transport, 2, 4, 120);
        List<PacketVisibilityService.Viewer> viewers = List.of(viewer(1), viewer(2));
        List<FishAgent> old = List.of(fish(1, null), fish(2, null));
        // Warm both viewers across a few budget-limited ticks.
        for (int tick = 1; tick <= 6; tick++) {
            service.beginTick(); service.sync(viewers, old, tick, false);
        }
        check(service.totalVisible() == 4, "warm-up visibility");
        transport.packets.clear();
        service.beginTick(); service.sync(viewers, List.of(fish(3, null)), 7, true);
        // Every packet must be DESTROY, despite new candidates being available.
        check(transport.packets.stream().allMatch(s -> s.startsWith("DESTROY")), "destroy was not prioritised");
        check(service.usedThisTick() <= 4, "soft packet budget exceeded");
        check(service.totalVisible() <= 4, "viewer membership grew during cleanup");
        for (int tick = 8; tick <= 13; tick++) {
            service.beginTick(); service.sync(viewers, List.of(fish(3, null)), tick, false);
        }
        check(service.pendingDestroys() == 0, "stale fish not retired");
        check(transport.visibleIds() == 2, "replacement fish mismatch");
        service.clearBestEffort();
        check(transport.visibleIds() == 0, "clearBestEffort left ghost fish");
    }

    private static void fairCleanupAndFailureRetry() {
        Transport transport = new Transport();
        var service = new PacketVisibilityService(transport, 1, 2, 120);
        List<PacketVisibilityService.Viewer> viewers = new ArrayList<>();
        FishAgent shared = fish(70, null);
        for (int i = 0; i < 5; i++) viewers.add(viewer(i));
        for (int tick = 1; tick <= 10; tick++) {
            service.beginTick();service.sync(viewers, List.of(shared), tick, false);
        }
        check(service.totalVisible() == 5, "five viewers not warmed");
        transport.failDestroy = true;
        int attempts = transport.destroyAttempts;
        service.beginTick();service.sync(viewers, List.of(), 11, false);
        check(transport.destroyAttempts - attempts <= 2, "failed destroy spun inside same tick");
        check(service.destroyFailures() > 0 && service.pendingDestroys() > 0, "failure diagnostics missing");
        transport.failDestroy = false;
        transport.throwDestroy = true;
        // A thrown backend exception must be counted without escaping the owner tick.
        service.beginTick();service.sync(viewers, List.of(), 12, false);
        check(service.destroyFailures() > 1 && service.pendingDestroys() > 0,
            "exception-based packet backend error was not isolated");
        transport.throwDestroy = false;
        for (int tick = 13; tick <= 17; tick++) {
            service.beginTick();service.sync(viewers, List.of(), tick, false);
        }
        check(service.totalVisible() == 0 && transport.visibleIds() == 0, "starved viewer remains after retry");
        check(service.peakPacketUsage() <= 2, "round-robin exceeded budget");
    }

    private static void privacyAndReconnect() {
        Transport transport = new Transport();
        var service = new PacketVisibilityService(transport, 4, 12, 100);
        FishAgent personal = fish(80, viewer(0).id());
        FishAgent shared = fish(81, null);
        service.beginTick();service.sync(List.of(viewer(0), viewer(1)), List.of(personal, shared), 1, false);
        check(transport.perViewer.get(viewer(0).id()).size() == 2, "owner missing private fish");
        check(transport.perViewer.get(viewer(1).id()).size() == 1, "private fish leaked to wrong viewer");
        service.beginTick();service.sync(List.of(viewer(1)), List.of(personal, shared), 2, false);
        check(transport.perViewer.get(viewer(0).id()).isEmpty(), "inactive viewer retained packet fish");
        service.dropDisconnected(viewer(1).id()); // client clears IDs on disconnect
        transport.perViewer.remove(viewer(1).id());
        service.beginTick();service.sync(List.of(viewer(1)), List.of(shared), 3, false);
        check(service.totalVisible() == 1, "reconnect did not rebuild viewer state");
    }

    private static void orphanSpawnRecovery() {
        Transport transport = new Transport();
        var service = new PacketVisibilityService(transport, 2, 4, 120);
        var players = List.of(viewer(5));
        var fish = List.of(fish(100, null));
        transport.throwPartialSpawn = true;
        transport.failDestroy = true;
        service.beginTick();service.sync(players, fish, 1, false);
        check(transport.visibleIds() == 1, "partial spawn should be visible to the mock client");
        check(service.totalVisible() == 0 && service.unconfirmedSpawns() == 1,
            "failed spawn was incorrectly treated as fully absent");
        int attempts = transport.spawnAttempts;
        service.beginTick();service.sync(players,fish,2,false);
        check(transport.spawnAttempts == attempts, "new spawn allowed while old ID still on client");
        check(service.pendingDestroys() > 0 && service.usedThisTick() <= 4,
            "orphan cleanup must be visible in diagnostics and respect budget");

        transport.throwPartialSpawn = false;
        transport.failDestroy = false;
        service.beginTick();service.sync(players,fish,3,false);
        check(service.unconfirmedSpawns() == 0 && service.totalVisible() == 1,
            "orphan was not destroyed before replacement spawn");
        check(transport.visibleIds() == 1, "client has duplicate or missing fish after recovery");

        transport.throwTeleport = true;
        fish.get(0).waypoint(new Vec3(20, 63.4, 0));
        fish.get(0).move(4, 100);
        service.beginTick();service.sync(players,fish,4,true);
        check(service.failures() > 0, "teleport failure did not increment diagnostics");
        transport.throwTeleport = false;
        service.clearBestEffort();
        check(transport.visibleIds() == 0, "cleanup failed after recovered orphan");
        // Logout has an explicit client-side entity reset; forget any old IDs.
        transport.partialSpawnFailure = true;
        transport.failDestroy = true;
        service.beginTick();service.sync(players,fish,5,false);
        service.dropDisconnected(players.get(0).id());
        check(service.unconfirmedSpawns() == 0, "disconnected viewer's orphan still tracked");
    }

    private static void simulateLoad(int anglers, int fishCount) {
        Transport transport = new Transport();
        var service = new PacketVisibilityService(transport, 20, 800, 48);
        List<PacketVisibilityService.Viewer> viewers = new ArrayList<>();
        for (int i = 0; i < anglers; i++) viewers.add(viewer(i));
        List<FishAgent> school = new ArrayList<>();
        for (int i = 0; i < fishCount; i++) school.add(fish(i + 1000, null));
        long started = System.nanoTime();
        long before = service.packetsSent();
        for (int tick = 1; tick <= 40; tick++) {
            service.beginTick();
            // Exercise join/quit and crossing world boundary without a real client.
            List<PacketVisibilityService.Viewer> active = tick % 9 == 0 ? viewers.subList(0, anglers / 2) : viewers;
            service.sync(active, school, tick, tick % 2 == 0);
            long emitted = service.packetsSent() - before;
            check(emitted <= 800, "packet budget exceeded @" + anglers + " players");
            before = service.packetsSent();
            check(service.totalVisible() <= anglers * 20, "unbounded viewer membership");
        }
        // Despawn all; low budget requires several ticks for large crowds.
        for (int tick = 41; tick <= 100; tick++) {
            service.beginTick();service.sync(viewers, List.of(), tick, false);
            if (service.totalVisible() == 0) break;
        }
        check(service.totalVisible() == 0, "ghost entity after load soak @" + anglers);
        check(transport.visibleIds() == 0, "mock client still has entities @" + anglers);
        check(service.pendingDestroys() == 0, "pending destroy backlog after soak");
        check(service.viewers() == 0, "empty viewer map leaked");
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        System.out.printf(Locale.ROOT,
            "synthetic anglers=%3d fish=%4d elapsed=%.1f ms packets=%d peak/tick=%d deferred=%d failures=%d%n",
            anglers, fishCount, millis, service.packetsSent(), service.peakPacketUsage(),
            service.packetsDeferred(), service.failures());
    }

    public static void main(String[] args) {
        destructionBeforeSpawn();
        fairCleanupAndFailureRetry();
        privacyAndReconnect();
        orphanSpawnRecovery();
        for (int n : new int[]{1, 10, 30, 50, 100}) simulateLoad(n, 500);
        System.out.println("PASS Phase 9: orphan/partial-spawn retry, throwing teleport, destroy-first, round-robin, shared/private, reconnect, 1/10/30/50/100 synthetic angler soak");
    }
}
