package com.oheers.fish.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatchRollTimelineTest {

    @Test
    void standardTimelineAdvancesThroughAllPhasesIncludingByebyeAscend() {
        CatchRollTimeline.Durations d = CatchRollTimeline.Durations.forTier(CatchRollTimeline.TierStyle.STANDARD);
        assertEquals(CatchRollTimeline.Phase.SLIDE_IN, CatchRollTimeline.sample(d, 0).phase());
        assertEquals(CatchRollTimeline.Phase.ROLLING, CatchRollTimeline.sample(d, d.slideInTicks()).phase());

        int revealTick = d.slideInTicks() + d.rollingTicks();
        assertEquals(CatchRollTimeline.Phase.REVEAL, CatchRollTimeline.sample(d, revealTick).phase());

        int showcaseTick = revealTick + d.revealTicks();
        assertEquals(CatchRollTimeline.Phase.SHOWCASE, CatchRollTimeline.sample(d, showcaseTick).phase());

        int holdTick = showcaseTick + d.showcaseTicks();
        assertEquals(CatchRollTimeline.Phase.HOLD, CatchRollTimeline.sample(d, holdTick).phase());

        int byebyeTick = holdTick + d.holdTicks();
        assertEquals(CatchRollTimeline.Phase.BYEBYE_ASCEND, CatchRollTimeline.sample(d, byebyeTick).phase());

        int completeTick = d.totalTicks();
        CatchRollTimeline.Frame complete = CatchRollTimeline.sample(d, completeTick);
        assertEquals(CatchRollTimeline.Phase.COMPLETE, complete.phase());
        assertTrue(complete.revealResult());
    }

    @Test
    void rouletteStepMonotonicallyIncreasesAndDecelerates() {
        CatchRollTimeline.Durations d = CatchRollTimeline.Durations.forTier(CatchRollTimeline.TierStyle.MYTHICAL);
        int previousStep = -1;
        for (int tick = d.slideInTicks(); tick < d.slideInTicks() + d.rollingTicks(); tick++) {
            CatchRollTimeline.Frame sample = CatchRollTimeline.sample(d, tick);
            assertEquals(CatchRollTimeline.Phase.ROLLING, sample.phase());
            assertFalse(sample.revealResult());
            assertTrue(sample.rouletteStep() >= previousStep, "Roulette step must not decrease");
            previousStep = sample.rouletteStep();
        }
        assertTrue(previousStep >= 8, "Mythical roll should cycle through multiple decoys");
    }

    @Test
    void byebyeAscendFliesSkywardWavesSideToSideAndFadesOut() {
        CatchRollTimeline.Durations d = CatchRollTimeline.Durations.forTier(CatchRollTimeline.TierStyle.STANDARD);
        int byebyeStart = d.slideInTicks()
            + d.rollingTicks()
            + d.revealTicks()
            + d.showcaseTicks()
            + d.holdTicks();
        int byebyeEnd = byebyeStart + d.byebyeAscendTicks() - 1;

        CatchRollTimeline.Frame first = CatchRollTimeline.sample(d, byebyeStart);
        CatchRollTimeline.Frame last = CatchRollTimeline.sample(d, byebyeEnd);

        assertEquals(CatchRollTimeline.Phase.BYEBYE_ASCEND, first.phase());
        assertEquals(CatchRollTimeline.Phase.BYEBYE_ASCEND, last.phase());
        assertTrue(last.offsetY() > first.offsetY() + 1.5, "Display must ascend into the sky during BYEBYE_ASCEND");
        assertTrue(last.scaleMultiplier() < first.scaleMultiplier(), "Item scale must shrink as it fades out into the sky");
        assertTrue(last.opacity() < first.opacity(), "Opacity must fade out during BYEBYE_ASCEND");

        boolean sawPositiveWave = false;
        boolean sawNegativeWave = false;
        for (int tick = byebyeStart; tick <= byebyeEnd; tick++) {
            double roll = CatchRollTimeline.sample(d, tick).rollRadians();
            if (roll > 0.05) {
                sawPositiveWave = true;
            }
            if (roll < -0.05) {
                sawNegativeWave = true;
            }
        }
        assertTrue(sawPositiveWave && sawNegativeWave, "Byebye animation must wave left and right");
    }

    @Test
    void multiLineTextVerticalTranslationCompensatesForExtraLines() {
        float zeroExtra = CatchRollTimeline.textVerticalTranslation(0, 1.0F);
        float threeExtra = CatchRollTimeline.textVerticalTranslation(3, 1.0F);
        assertTrue(threeExtra < zeroExtra, "4-line TextDisplay (3 extra lines) should shift downward to keep visual spacing stable");
    }

    @Test
    void stageOneMaterialWheelSpinsThroughSevenSlotsAndConvergesWinningMaterial() {
        int wheelTicks = CatchRollTimeline.WHEEL_DURATION_TICKS;
        CatchRollTimeline.WheelFrame start = CatchRollTimeline.sampleWheel(
                CatchRollTimeline.TierStyle.MYTHICAL, 0, wheelTicks, 0.40f
        );
        assertEquals(CatchRollTimeline.WHEEL_SLOT_COUNT, start.slots().length);
        assertFalse(start.converging());

        CatchRollTimeline.WheelFrame end = CatchRollTimeline.sampleWheel(
                CatchRollTimeline.TierStyle.MYTHICAL, wheelTicks - 1, wheelTicks, 0.40f
        );
        assertTrue(end.converging());
        assertEquals(6, end.highlightedIndex(), "Mythical wheel must lock on slot 6 (Elytra)");
        assertEquals(0.0, end.slots()[6].offsetRight(), 1.0e-4, "Winning slot must converge to center X");
        assertEquals(0.0, end.slots()[6].offsetY(), 1.0e-4, "Winning slot must converge to center Y");
        assertFalse(end.slots()[0].visible(), "Non-winning slots must hide during convergence");
    }

    @Test
    void epicAndAboveTriggerHeartbeatAndRevealSilenceWindow() {
        CatchRollTimeline.Durations d = CatchRollTimeline.Durations.forTier(CatchRollTimeline.TierStyle.LEGENDARY);
        int beatsObserved = 0;
        for (int tick = d.slideInTicks(); tick < d.slideInTicks() + d.rollingTicks(); tick++) {
            if (CatchRollTimeline.isHeartbeatTick(d, CatchRollTimeline.TierStyle.LEGENDARY, tick)) {
                beatsObserved++;
            }
        }
        assertEquals(3, beatsObserved, "Legendary roll should trigger 3 accelerating Warden heartbeats");

        int revealStart = d.slideInTicks() + d.rollingTicks();
        assertTrue(CatchRollTimeline.isInRevealSilenceWindow(d, CatchRollTimeline.TierStyle.LEGENDARY, revealStart - 1));
        assertFalse(CatchRollTimeline.isInRevealSilenceWindow(d, CatchRollTimeline.TierStyle.STANDARD, revealStart - 1));
    }
}
