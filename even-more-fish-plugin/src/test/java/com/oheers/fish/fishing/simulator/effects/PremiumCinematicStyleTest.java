package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Style;
import com.oheers.fish.fishing.simulator.population.FishKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PremiumCinematicStyleTest {
    private static PremiumCinematicSettings.Profile profile() {
        return new PremiumCinematicSettings.Profile(new PremiumCinematicTimeline.Durations(0, 0, 8, 6, 6, 2),
            1, .5, 4, null, Map.of());
    }

    @Test void resolvedPresetSelectsStyleEvenForSyntheticPreviewAndEqualMotionValues() {
        var common = profile(); var rare = profile(); var epic = profile(); var legendary = profile();
        var settings = new PremiumCinematicSettings(true, 12,
            Map.of("common", common, "rare", rare, "epic", epic, "legendary", legendary),
            Map.of("custom", "rare"), Map.of("custom:special", "legendary"));
        assertEquals(Style.STANDARD, settings.style(settings.preview("common")));
        assertEquals(Style.STANDARD, settings.style(settings.preview("rare")));
        assertEquals(Style.EPIC, settings.style(settings.preview("EPIC")));
        assertEquals(Style.LEGENDARY, settings.style(settings.preview("legendary")));
        assertEquals(Style.STANDARD, settings.style(settings.profile(new FishKey("Custom", "Normal"))));
        assertEquals(Style.LEGENDARY, settings.style(settings.profile(new FishKey("Custom", "Special"))));
        assertEquals(Style.STANDARD, settings.style(null));
    }

    @Test void oldConstructorRetainsStandardMotionAndKnownCatchIdsSelectTheirStyle() {
        var timeline = new PremiumCinematicTimeline(new PremiumCinematicTimeline.Durations(0, 0, 8, 0, 6, 2),
            new com.oheers.fish.fishing.simulator.population.Vec3(0, 0, 0),
            new com.oheers.fish.fishing.simulator.population.Vec3(1, 1, 1), 2, .8);
        assertEquals(Style.STANDARD, timeline.style());
        assertEquals(Style.EPIC, Style.forRarity("ePiC"));
        assertEquals(Style.LEGENDARY, Style.forRarity("Legendary"));
        assertEquals(Style.LEGENDARY, Style.forRarity("Mythic"));
        assertEquals(Style.STANDARD, Style.forRarity("Rare"));
    }
}
