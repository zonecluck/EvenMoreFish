package com.oheers.fish.fishing.pool;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FishingPoolStoreTest {
    @TempDir Path directory;

    @Test void actualAtomicSaveLoadsOnRestartAndPersistsRemoval() throws Exception {
        Path file = directory.resolve("fishing-pools.yml");
        FishingPoolStore.write(file, """
            fishing-pools:
              enabled: true
              pools:
                Lake: {world: world, x: 10.5, y: 64, z: -20.5, radius: 8, speed-percent: 40, rare-multiplier: 2.5}
            """);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file.toFile());
        FishingPool pool = FishingPoolSettings.load(loaded).pools().get("lake");
        assertEquals(8, pool.radius()); assertEquals(40, pool.speedPercent()); assertEquals(2.5, pool.rareMultiplier());
        FishingPoolSettings.removeDefinition(loaded, pool.id());
        FishingPoolStore.write(file, loaded.saveToString());
        YamlConfiguration restarted = new YamlConfiguration();
        restarted.load(file.toFile());
        assertTrue(FishingPoolSettings.load(restarted).pools().isEmpty());
        try (var files = Files.list(directory)) { assertEquals(1, files.count(), "No abandoned temporary file"); }
    }

    @Test void failedAtomicReplacementPreservesTargetAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("fishing-pools.yml");
        Files.createDirectory(target);
        Files.writeString(target.resolve("existing-marker"), "keep");
        assertThrows(java.io.IOException.class, () -> FishingPoolStore.write(target, "candidate"));
        assertEquals("keep", Files.readString(target.resolve("existing-marker")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
