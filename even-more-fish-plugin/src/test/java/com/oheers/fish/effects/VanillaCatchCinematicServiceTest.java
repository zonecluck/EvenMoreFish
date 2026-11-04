package com.oheers.fish.effects;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class VanillaCatchCinematicServiceTest {
    @Test void commonNeverOpensACinematicEvenWithBroadRaritySettings() {
        var service = new VanillaCatchCinematicService(mock(EvenMoreFish.class));
        Fish fish = mock(Fish.class);
        Rarity rarity = mock(Rarity.class);
        when(fish.getRarity()).thenReturn(rarity);
        when(rarity.getId()).thenReturn("cOmMoN");
        try (var main = mockStatic(MainConfig.class)) {
            MainConfig config = mock(MainConfig.class, RETURNS_DEEP_STUBS);
            main.when(MainConfig::getInstance).thenReturn(config);
            when(config.getConfig().getBoolean("animations.rare-catch.cinematic.all-rarities", true)).thenReturn(true);
            assertTrue(service.eligible(fish));
        }
    }
}
