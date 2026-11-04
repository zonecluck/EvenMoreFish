package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Style;
import com.oheers.fish.fishing.simulator.population.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CinematicParticlePatternTest {
    @Test void allStagesStayBoundedAndRespectRemainingAllowance() {
        var water = new Vec3(100, 63, -50);
        var fish = new Vec3(101, 65, -49);
        for (Style style : new Style[]{Style.EPIC, Style.LEGENDARY}) {
            for (Stage stage : Stage.values()) {
                for (int allowance = 0; allowance < 12; allowance++) {
                    var points = CinematicParticlePattern.points(style, stage, water, fish, 45, .5, allowance);
                    assertTrue(points.size() <= Math.min(8, allowance));
                    for (Vec3 point : points)
                        assertTrue(Math.min(point.distanceSquared(water), point.distanceSquared(fish)) < 4);
                }
            }
        }
    }

    @Test void standardScenesDoNotGainExtraParticles() {
        for (Stage stage : Stage.values())
            assertTrue(CinematicParticlePattern.points(Style.STANDARD, stage,
                new Vec3(0, 0, 0), new Vec3(0, 1, 0), 10, .5, 8).isEmpty());
    }

    @Test void hiddenVortexRotatesAndLegendaryGetsTheLargerHalo() {
        var center = new Vec3(0, 63, 0);
        var first = CinematicParticlePattern.points(Style.EPIC, Stage.VORTEX, center, center, 10, .5, 6);
        var next = CinematicParticlePattern.points(Style.EPIC, Stage.VORTEX, center, center, 12, .5, 6);
        assertNotEquals(first, next);
        var epic = CinematicParticlePattern.points(Style.EPIC, Stage.HERO_REVEAL, center, center, 10, .5, 8);
        var legendary = CinematicParticlePattern.points(Style.LEGENDARY, Stage.HERO_REVEAL, center, center, 10, .5, 8);
        assertTrue(legendary.getFirst().distanceSquared(center) > epic.getFirst().distanceSquared(center));
    }

    @Test void bothRenderersShareTheReservationAndFailureDoesNotFreeSlots() {
        var budget = new CinematicParticleBudget(24);
        assertEquals(8, budget.reserve(100, 8)); // Native scene.
        assertEquals(12, budget.reserve(100, 12)); // Packet scene in the same Paper tick.
        assertEquals(4, budget.reserve(100, 8));
        assertEquals(0, budget.reserve(100, 1)); // Reservations remain after a failed send.
        assertEquals(0, budget.reserve(101, -1));
        assertEquals(24, budget.reserve(101, Integer.MAX_VALUE));
        assertEquals(0, budget.reserve(101, 1));
        assertEquals(6, budget.reserve(102, 6));
    }
}
