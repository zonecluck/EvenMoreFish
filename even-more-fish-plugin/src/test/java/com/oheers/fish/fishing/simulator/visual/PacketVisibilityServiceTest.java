package com.oheers.fish.fishing.simulator.visual;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.population.FishKey;
import com.oheers.fish.fishing.simulator.population.FishVisualModel;
import com.oheers.fish.fishing.simulator.population.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the actual packet-budget and visibility domain without a Minecraft server. */
class PacketVisibilityServiceTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final String ZONE = "lake";

    private static final class PacketStub implements PacketTransport {
        final Map<UUID, Set<Integer>> visible = new HashMap<>();
        final List<String> operations = new ArrayList<>();
        boolean failDestroy;
        boolean throwDestroy;
        boolean partialSpawn;
        int destroyCalls;
        int spawnCalls;

        @Override public boolean available() { return true; }
        @Override public boolean spawn(UUID viewer, FishAgent fish) {
            spawnCalls++;
            visible.computeIfAbsent(viewer, ignored -> new HashSet<>()).add(fish.entityId());
            operations.add("spawn");
            return !partialSpawn; // model: spawn reached client, metadata failed
        }
        @Override public boolean teleport(UUID viewer, FishAgent fish) {
            assertTrue(visible.getOrDefault(viewer, Set.of()).contains(fish.entityId()));
            operations.add("move");
            return true;
        }
        @Override public boolean destroy(UUID viewer, int entityId) {
            destroyCalls++;
            if (throwDestroy) throw new IllegalStateException("test backend exception");
            if (failDestroy) return false;
            visible.computeIfAbsent(viewer, ignored -> new HashSet<>()).remove(entityId);
            operations.add("destroy");
            return true;
        }
    }

    @Test void partialSpawnRequiresConfirmedDestroyBeforeRetry() {
        PacketStub transport = new PacketStub();
        PacketVisibilityService service = new PacketVisibilityService(transport, 2, 4, 30);
        var viewer = viewer();
        var fish = fish(1, null);
        transport.partialSpawn = true;
        transport.failDestroy = true;
        service.beginTick();service.sync(List.of(viewer), List.of(fish), 1, false);
        assertEquals(1, service.unconfirmedSpawns());
        assertEquals(0, service.totalVisible());
        assertEquals(1, transport.visible.get(viewer.id()).size());
        int spawnCalls = transport.spawnCalls;
        service.beginTick();service.sync(List.of(viewer), List.of(fish), 2, false);
        assertEquals(spawnCalls, transport.spawnCalls);
        assertEquals(1, service.pendingDestroys());

        transport.partialSpawn = false;
        transport.failDestroy = false;
        service.beginTick();service.sync(List.of(viewer), List.of(fish), 3, false);
        assertEquals(0, service.unconfirmedSpawns());
        assertEquals(1, service.totalVisible());
        assertEquals(1, transport.visible.get(viewer.id()).size());
        service.clearBestEffort();
        assertTrue(transport.visible.get(viewer.id()).isEmpty());
    }

    private static PacketVisibilityService.Viewer viewer() {
        return new PacketVisibilityService.Viewer(UUID.randomUUID(), WORLD, ZONE, new Vec3(0, 64, 0));
    }
    private static FishAgent fish(int id, UUID privateOwner) {
        return new FishAgent(UUID.randomUUID(), 1_500_000_000 + id,
            new FishKey("Rare", "Koi"), FishVisualModel.COD, ZONE, WORLD,
            privateOwner, 2000, new Vec3(id * .1, 64, 0));
    }

    @Test void destroyPacketsTakePriorityOverNewSpawns() {
        PacketStub mock = new PacketStub();
        PacketVisibilityService service = new PacketVisibilityService(mock, 4, 4, 30);
        var viewer = viewer();
        var old = List.of(fish(1, null), fish(2, null));
        service.beginTick();service.sync(List.of(viewer), old, 1, false);
        assertEquals(2, service.totalVisible());
        mock.operations.clear();
        service.beginTick();service.sync(List.of(viewer), List.of(fish(3, null)), 2, false);
        assertFalse(mock.operations.isEmpty());
        assertEquals(List.of("destroy", "destroy", "spawn"), mock.operations);
        assertEquals(4, service.usedThisTick());
        assertEquals(0, service.pendingDestroys());
    }

    @Test void failedDestroyIsRetriedOnNextTickNotLooped() {
        PacketStub mock = new PacketStub();
        PacketVisibilityService service = new PacketVisibilityService(mock, 2, 3, 30);
        var viewer = viewer();
        service.beginTick();service.sync(List.of(viewer), List.of(fish(1, null)), 1, false);
        mock.failDestroy = true;
        service.beginTick();service.sync(List.of(viewer), List.of(), 2, false);
        assertEquals(1, mock.destroyCalls);
        assertEquals(1, service.pendingDestroys());
        assertEquals(1, service.destroyFailures());
        mock.failDestroy = false;
        mock.throwDestroy = true;
        service.beginTick();service.sync(List.of(viewer), List.of(), 3, false);
        assertEquals(2, service.destroyFailures());
        assertEquals(1, service.pendingDestroys());
        mock.throwDestroy = false;
        service.beginTick();service.sync(List.of(viewer), List.of(), 4, false);
        assertEquals(0, service.pendingDestroys());
        assertEquals(0, service.totalVisible());
    }

    @Test void privateFishCannotLeakAcrossViewers() {
        PacketStub mock = new PacketStub();
        PacketVisibilityService service = new PacketVisibilityService(mock, 2, 8, 40);
        var a = viewer();var b = viewer();
        service.beginTick();
        service.sync(List.of(a, b), List.of(fish(1, a.id()), fish(2, null)), 1, false);
        assertEquals(2, mock.visible.get(a.id()).size());
        assertEquals(1, mock.visible.get(b.id()).size());
        service.beginTick();service.sync(List.of(), List.of(), 2, false);
        assertEquals(0, service.totalVisible());
        assertEquals(0, service.viewers());
    }

    @Test void manyViewerSoakNeverExceedsBudgetOrLeaks() {
        PacketStub mock = new PacketStub();
        PacketVisibilityService service = new PacketVisibilityService(mock, 8, 40, 40);
        List<PacketVisibilityService.Viewer> viewers = new ArrayList<>();
        for (int i = 0; i < 30; i++) viewers.add(viewer());
        List<FishAgent> agents = new ArrayList<>();
        for (int i = 0; i < 100; i++) agents.add(fish(i, null));
        for (int tick = 1; tick <= 35; tick++) {
            service.beginTick();service.sync(viewers, agents, tick, tick % 2 == 0);
            assertTrue(service.usedThisTick() <= 40);
            assertTrue(service.totalVisible() <= 30 * 8);
        }
        for (int tick = 36; tick <= 75; tick++) {
            service.beginTick();service.sync(viewers, List.of(), tick, false);
            if (service.totalVisible() == 0) break;
        }
        assertEquals(0, service.totalVisible());
        assertEquals(0, service.pendingDestroys());
        assertEquals(0, service.viewers());
    }
}
