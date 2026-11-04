package com.oheers.fish.fishing.pool;

import com.oheers.fish.api.fishing.FishingLakeProvider.Bonus;
import com.oheers.fish.fishing.simulator.SimulatorModule;
import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import com.oheers.fish.fishing.simulator.visual.PacketTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HookedFishPacketServiceTest extends FishingLakeFixture {
    PacketTransport transport;
    HookedFishPacketService visuals;
    @BeforeEach void prepareVisuals() {
        transport = mock(PacketTransport.class);
        when(transport.available()).thenReturn(true);
        doReturn(true).when(transport).spawn(any(), any());
        when(transport.teleport(any(), any())).thenReturn(true);
        when(transport.destroy(any(), anyInt())).thenReturn(true);
        when(provider.resolve(eq(player), any())).thenReturn(new Bonus("island:lake", 25, 2, true));
        visuals = new HookedFishPacketService(plugin, transport);
    }

    @Test void noIdlePacketsUntrackedCastRejectedAndDuplicateBiteNeverCreatesSecondFish() {
        verifyNoInteractions(transport);
        assertFalse(visuals.bite(player, hook));
        pools.track(hook);
        assertTrue(visuals.bite(player, hook));
        assertFalse(visuals.bite(player, hook));
        var fish = ArgumentCaptor.forClass(FishAgent.class);
        verify(transport).spawn(eq(player.getUniqueId()), fish.capture());
        assertTrue(fish.getValue().entityId() >= 1_400_000_000);
        assertEquals(player.getUniqueId(), fish.getValue().privateOwner());
        assertFalse(fish.getValue().visibleTo(UUID.randomUUID()));
        for (int i = 0; i < HookedFishPacketService.DURATION_TICKS / 2 + 2; i++) visuals.tick();
        assertEquals(0, visuals.activeScenes());
        assertFalse(visuals.bite(player, hook)); // Timeout cannot replay the same hook.
        verify(transport).destroy(player.getUniqueId(), fish.getValue().entityId());
    }

    @Test void failedSpawnCompensatesAndUnresolvedDestroyBlocksNewSpawnsUntilBoundedRetry() {
        pools.track(hook);
        when(transport.spawn(any(), any())).thenThrow(new LinkageError("partial spawn"));
        when(transport.destroy(any(), anyInt())).thenReturn(false);
        assertFalse(visuals.bite(player, hook));
        assertEquals(0, visuals.activeScenes());
        assertEquals(1, visuals.pendingCleanup());
        visuals.stop(player.getUniqueId());
        assertFalse(visuals.bite(player, hook));
        visuals.clear();
        assertEquals(1, visuals.pendingCleanup()); // reload retains exact pending ID
        when(transport.destroy(any(), anyInt())).thenReturn(true);
        visuals.tick();
        assertEquals(0, visuals.pendingCleanup());
        doReturn(true).when(transport).spawn(any(), any());
        assertTrue(visuals.bite(player, hook));
    }

    @Test void terminalRecastWorldReloadAndDisconnectCleanThePrivateEntity() {
        pools.track(hook);
        assertTrue(visuals.bite(player, hook));
        visuals.stop(player.getUniqueId()); // terminal or recast
        assertEquals(0, visuals.activeScenes());
        assertTrue(visuals.bite(player, hook));
        when(player.getWorld()).thenReturn(mock(org.bukkit.World.class));
        visuals.tick();
        assertEquals(0, visuals.activeScenes());
        when(player.getWorld()).thenReturn(world);
        visuals.stop(player.getUniqueId());
        assertTrue(visuals.bite(player, hook));
        visuals.clear();
        assertEquals(0, visuals.activeScenes());
        assertTrue(visuals.bite(player, hook));
        when(transport.destroy(any(), anyInt())).thenReturn(false);
        visuals.quit(player.getUniqueId());
        assertEquals(0, visuals.activeScenes());
        assertEquals(0, visuals.pendingCleanup()); // confirmed client disconnect clears entity table
        visuals.shutdown();
    }

    @Test void hudOffMinimalAndReducedMotionAndEffectsOptOutRejectPackets() {
        pools.track(hook);
        SimulatorModule module = mock(SimulatorModule.class);
        when(plugin.getSimulatorModule()).thenReturn(module);
        when(module.ownEffectsEnabled(player.getUniqueId())).thenReturn(true);
        for (UiProfile profile : new UiProfile[]{UiProfile.OFF, UiProfile.MINIMAL, UiProfile.REDUCED_MOTION}) {
            when(module.ownHudProfile(player.getUniqueId())).thenReturn(profile);
            assertFalse(visuals.bite(player, hook));
        }
        when(module.ownHudProfile(player.getUniqueId())).thenReturn(UiProfile.CINEMATIC);
        when(module.ownEffectsEnabled(player.getUniqueId())).thenReturn(false);
        assertFalse(visuals.bite(player, hook));
        verifyNoInteractions(transport);
        when(module.ownEffectsEnabled(player.getUniqueId())).thenReturn(true);
        assertTrue(visuals.bite(player, hook));
        when(module.ownHudProfile(player.getUniqueId())).thenReturn(UiProfile.REDUCED_MOTION);
        visuals.tick();
        assertEquals(0, visuals.activeScenes());
    }

    @Test void missingTransportOrLakeVisualAndProviderWithdrawalLeaveLegacyUnaffected() {
        pools.track(hook);
        when(transport.available()).thenReturn(false);
        assertFalse(visuals.bite(player, hook));
        when(transport.available()).thenReturn(true);
        when(provider.resolve(any(), any())).thenReturn(new Bonus("island:lake", 25, 2, false));
        assertFalse(visuals.bite(player, hook));
        when(provider.resolve(any(), any())).thenReturn(new Bonus("island:lake", 25, 2, true));
        assertTrue(visuals.bite(player, hook));
        when(providerPlugin.isEnabled()).thenReturn(false);
        visuals.tick();
        assertEquals(0, visuals.activeScenes()); // fallback static pool has no hooked visual
    }

    @Test void concurrentScenesAreGloballyBoundedAndDisableCleansEveryId() {
        when(provider.resolve(any(), any())).thenReturn(new Bonus("island:lake", 25, 2, true));
        for (int i = 0; i <= HookedFishPacketService.MAX_SCENES; i++) {
            var viewer = mock(org.bukkit.entity.Player.class);
            when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
            when(viewer.getWorld()).thenReturn(world);
            when(viewer.getLocation()).thenReturn(water);
            when(viewer.isOnline()).thenReturn(true);
            var cast = mock(org.bukkit.entity.FishHook.class);
            when(cast.getUniqueId()).thenReturn(UUID.randomUUID());
            when(cast.getShooter()).thenReturn(viewer);
            when(cast.getWorld()).thenReturn(world);
            when(cast.getLocation()).thenReturn(water);
            when(cast.isValid()).thenReturn(true);
            pools.track(cast);
            assertEquals(i < HookedFishPacketService.MAX_SCENES, visuals.bite(viewer, cast));
        }
        assertEquals(HookedFishPacketService.MAX_SCENES, visuals.activeScenes());
        verify(transport, times(HookedFishPacketService.MAX_SCENES)).spawn(any(), any());
        visuals.shutdown();
        assertEquals(0, visuals.activeScenes());
        verify(transport, times(HookedFishPacketService.MAX_SCENES)).destroy(any(), anyInt());
    }
}
