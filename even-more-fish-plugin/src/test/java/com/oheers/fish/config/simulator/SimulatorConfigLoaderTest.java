package com.oheers.fish.config.simulator;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.fishing.simulator.zones.SimulatorZoneResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulatorConfigLoaderTest {
    @Test void validZoneRetainsItsWorldNameBeforeTheWorldLoadsWithoutEnablingSimulatorFeatures() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var settings = SimulatorConfigLoader.parse(zoneConfig("  spawn  "), 7);
            assertFalse(settings.enabled());
            assertEquals(7, settings.revision());
            assertEquals(1, settings.zones().size());
            assertEquals("spawn", settings.zones().getFirst().world());
            assertTrue(settings.zones().getFirst().enabled());
            assertFalse(settings.probe().enabled());
            assertFalse(settings.ambient().enabled());
            assertFalse(settings.aiPreview().enabled());
            assertFalse(settings.rewardBridge().enabled());
            bukkit.verifyNoInteractions();
        }
    }

    @Test void enabledZoneStillRejectsBlankOrNonStringWorldNames() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            for (Object world : List.of("", "   ", 123)) {
                var config = zoneConfig("spawn");
                config.set("zones.central_lake_1.world", world);
                var failure = assertThrows(IllegalArgumentException.class, () -> SimulatorConfigLoader.parse(config, 1));
                assertTrue(failure.getMessage().contains("zones.central_lake_1.world"));
            }
            var missing = zoneConfig("spawn");
            missing.set("zones.central_lake_1.world", null);
            assertThrows(IllegalArgumentException.class, () -> SimulatorConfigLoader.parse(missing, 1));
        }
    }

    @Test void deferredWorldLookupDoesNotRelaxCuboidOrProviderValidation() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("spawn")).thenReturn(mock(World.class));
            var incomplete = zoneConfig("spawn");
            incomplete.set("zones.central_lake_1.max.z", null);
            assertThrows(IllegalArgumentException.class, () -> SimulatorConfigLoader.parse(incomplete, 1));
            var invalidRegion = zoneConfig("spawn");
            invalidRegion.set("zones.central_lake_1.type", "WORLDGUARD_REGION");
            assertThrows(IllegalArgumentException.class, () -> SimulatorConfigLoader.parse(invalidRegion, 1));
        }
    }

    @Test void validZoneIsDormantOutsideItsWorldAndUsesTheSameSnapshotAfterWorldLoad() {
        World world = mock(World.class), lobby = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("spawn");
        when(lobby.getUID()).thenReturn(UUID.randomUUID());
        when(lobby.getName()).thenReturn("lobby");
        var resolver = new SimulatorZoneResolver(mock(EvenMoreFish.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            var settings = SimulatorConfigLoader.parse(zoneConfig("spawn"), 1);
            assertFalse(resolver.resolve(new Location(null, 1, 64, 1), settings).found());
            assertFalse(resolver.resolve(new Location(lobby, 1, 64, 1), settings).found(), "Loaded lobby is not the configured spawn world");
            bukkit.when(() -> Bukkit.getWorld("spawn")).thenReturn(world);
            Location actualSpawn = new Location(Bukkit.getWorld("spawn"), 1, 64, 1);
            assertEquals("central_lake_1", resolver.resolve(actualSpawn, settings).zone().id());
            assertEquals(1, settings.revision(), "World load requires no config rewrite/reload");
        }
        verify(world, never()).loadChunk(anyInt(), anyInt());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test void loadedWorldKeepsExistingCuboidBoundariesAndOtherWorldIsolation() {
        World world = mock(World.class), another = mock(World.class);
        UUID id = UUID.randomUUID(), anotherId = UUID.randomUUID();
        when(world.getUID()).thenReturn(id);
        when(world.getName()).thenReturn("spawn");
        when(another.getUID()).thenReturn(anotherId);
        when(another.getName()).thenReturn("other");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(id)).thenReturn(world);
            bukkit.when(() -> Bukkit.getWorld(anotherId)).thenReturn(another);
            bukkit.when(() -> Bukkit.getWorld("spawn")).thenReturn(world);
            var settings = SimulatorConfigLoader.parse(zoneConfig("spawn"), 1);
            var resolver = new SimulatorZoneResolver(mock(EvenMoreFish.class));
            assertTrue(resolver.resolve(new Location(world, 10.9, 64, 10.9), settings).found());
            assertFalse(resolver.resolve(new Location(world, 11, 64, 1), settings).found());
            assertFalse(resolver.resolve(new Location(another, 1, 64, 1), settings).found());
        }
    }

    private static YamlConfiguration zoneConfig(String world) {
        var config = new YamlConfiguration();
        config.set("config-version", 1);
        config.set("simulator.enabled", false);
        config.set("zones.central_lake_1.enabled", true);
        config.set("zones.central_lake_1.type", "CUBOID");
        config.set("zones.central_lake_1.world", world);
        config.set("zones.central_lake_1.min.x", 0);
        config.set("zones.central_lake_1.min.y", 60);
        config.set("zones.central_lake_1.min.z", 0);
        config.set("zones.central_lake_1.max.x", 10);
        config.set("zones.central_lake_1.max.y", 70);
        config.set("zones.central_lake_1.max.z", 10);
        return config;
    }

}
