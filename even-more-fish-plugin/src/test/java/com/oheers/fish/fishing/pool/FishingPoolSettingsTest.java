package com.oheers.fish.fishing.pool;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FishingPoolSettingsTest {
    private YamlConfiguration config(String value) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(value);
        return yaml;
    }

    @Test void shippedDefaultsEnableOnlyConfiguredRarityIdsWithEmptyPools() throws Exception {
        try (var stream = getClass().getResourceAsStream("/fishing-pools.yml")) {
            assertNotNull(stream);
            FishingPoolSettings settings = FishingPoolSettings.load(config(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            assertTrue(settings.enabled()); assertTrue(settings.pools().isEmpty());
            assertTrue(settings.boosts("uNcOmMoN")); assertTrue(settings.boosts("MYTHICAL"));
            assertFalse(settings.boosts("Common")); assertFalse(settings.boosts("Rare-ish"));
        }
    }

    @Test void yamlRoundTripRetainsGeometryAndBonusesAndRemoval() throws Exception {
        YamlConfiguration yaml = config("""
            fishing-pools:
              enabled: true
              pools:
                lake:
                  world: world
                  x: 10.5
                  y: 64
                  z: -20.5
                  radius: 12
                  speed-percent: 40
                  rare-multiplier: 2.5
            """);
        FishingPool original = FishingPoolSettings.load(yaml).pools().get("lake");
        assertEquals(original, FishingPoolSettings.load(config(yaml.saveToString())).pools().get("lake"));
        yaml.set("fishing-pools.pools.lake", null);
        assertTrue(FishingPoolSettings.load(config(yaml.saveToString())).pools().isEmpty());
    }

    @Test void explicitCommonNeverBoostsAndFiniteBonusesClamp() throws Exception {
        FishingPoolSettings settings = FishingPoolSettings.load(config("""
            fishing-pools:
              rare-rarity-ids: [COMMON, rare, CustomLegend]
              default-speed-percent: 200
              default-rare-multiplier: -20
            """));
        assertFalse(settings.boosts("common")); assertTrue(settings.boosts("customlegend"));
        assertEquals(85, settings.defaultSpeed()); assertEquals(1, settings.defaultMultiplier());
    }

    @Test void malformedTypesNonfiniteValuesAndDuplicateIdsRejectEntireCandidate() throws Exception {
        for (String body : new String[] { "enabled: 'true'", "default-speed-percent: .NaN", "rare-rarity-ids: Rare", "pools: [lake]" }) {
            YamlConfiguration yaml = config("fishing-pools:\n  " + body + "\n");
            assertThrows(IllegalArgumentException.class, () -> FishingPoolSettings.load(yaml));
        }
        YamlConfiguration duplicate = config("""
            fishing-pools:
              pools:
                lake: {world: world, x: 0, y: 64, z: 0, radius: 5}
                LAKE: {world: world, x: 0, y: 64, z: 0, radius: 5}
            """);
        assertThrows(IllegalArgumentException.class, () -> FishingPoolSettings.load(duplicate));
    }
}
