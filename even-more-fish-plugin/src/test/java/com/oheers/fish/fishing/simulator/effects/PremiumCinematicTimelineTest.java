package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.population.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class PremiumCinematicTimelineTest {
    @Test void enhancedStylesPreservePositionAndPoseContinuityAtEveryVisibleJoin() {
        var durations = new PremiumCinematicTimeline.Durations(8, 12, 16, 18, 18, 8);
        for (var style : new PremiumCinematicTimeline.Style[]{
                PremiumCinematicTimeline.Style.EPIC, PremiumCinematicTimeline.Style.LEGENDARY}) {
            var timeline = new PremiumCinematicTimeline(durations,
                new Vec3(0, 63, 0), new Vec3(12, 64, 3), 2.1, .8, style);
            for (int boundary : new int[]{20, 36, 54, 72}) {
                Vec3 at = timeline.sample(boundary).position();
                assertTrue(at.distanceSquared(timeline.sample(boundary - .001).position()) < 1e-9);
                assertTrue(at.distanceSquared(timeline.sample(boundary + .001).position()) < 1e-9);
                var left = timeline.pose(boundary - .001);
                var right = timeline.pose(boundary + .001);
                assertEquals(left.scale(), right.scale(), 1e-6);
                assertEquals(left.yaw(), right.yaw(), 1e-6);
                assertEquals(left.roll(), right.roll(), 1e-6);
            }
            assertEquals(0, timeline.pose(0).scale());
            assertEquals(0, timeline.pose(80).scale());
        }
    }

    @Test void enhancedRevealUsesBothHorizontalAxesAndHandlesVerticalTargets() {
        var durations = new PremiumCinematicTimeline.Durations(0, 0, 12, 18, 18, 6);
        var timeline = new PremiumCinematicTimeline(durations,
            new Vec3(0, 63, 0), new Vec3(4, 64, 0), 2, .8, PremiumCinematicTimeline.Style.LEGENDARY);
        assertNotEquals(0, timeline.sample(18).position().z());
        var vertical = new PremiumCinematicTimeline(durations,
            new Vec3(0, 63, 0), new Vec3(0, 64, 0), 2, .8, PremiumCinematicTimeline.Style.EPIC);
        for (int i = 0; i <= vertical.duration(); i++) assertNotNull(vertical.sample(i).position());
    }

    @Test void deliveryAndRevealJoinWithZeroVelocity() {
        var timeline = new PremiumCinematicTimeline(new PremiumCinematicTimeline.Durations(2, 2, 12, 8, 10, 6),
            new Vec3(0, 63, 0), new Vec3(12, 64, 3), 1.15, .24);
        double epsilon = .001;
        for (int boundary : new int[]{4, 16, 24, 34}) {
            Vec3 at = timeline.sample((double)boundary).position();
            assertTrue(at.distanceSquared(timeline.sample(boundary - epsilon).position()) < 1e-10,
                "motion snaps before boundary " + boundary);
            assertTrue(at.distanceSquared(timeline.sample(boundary + epsilon).position()) < 1e-10,
                "motion snaps after boundary " + boundary);
        }
    }

    @Test void skippingRevealDoesNotJumpFromSurfaceToAnUnvisitedHeroPose() {
        var timeline = new PremiumCinematicTimeline(new PremiumCinematicTimeline.Durations(0, 0, 8, 0, 6, 2),
            new Vec3(0, 63, 0), new Vec3(30, 64, 0), .5, 0);
        assertTrue(timeline.sample(7.999).position().distanceSquared(timeline.sample(8.001).position()) < 1e-10);
    }
    @Test void legendaryHasSixStagesWithoutTeleportJumps() {
        var durations=new PremiumCinematicTimeline.Durations(8,12,15,14,15,6);
        var target=new Vec3(4,65,1);
        var timeline=new PremiumCinematicTimeline(durations,new Vec3(0,63,0),target,2.35,.9);
        assertEquals(70,timeline.duration());
        var visited=EnumSet.noneOf(PremiumCinematicTimeline.Stage.class);
        for(int tick=0;tick<70;tick++) {
            var frame=timeline.sample(tick);
            visited.add(frame.stage());
            assertTrue(Double.isFinite(frame.position().x()));
            assertTrue(Double.isFinite(frame.position().y()));
            assertTrue(Double.isFinite(frame.position().z()));
            if(tick>0)assertTrue(timeline.sample(tick-1).position().distanceSquared(frame.position())<3,
                "teleport jump at tick " + tick);
        }
        assertEquals(EnumSet.allOf(PremiumCinematicTimeline.Stage.class),visited);
        assertEquals(target,timeline.sample(70).position());
    }

    @Test void skippedStagesRemainMonotonicAndBounded() {
        var durations=new PremiumCinematicTimeline.Durations(0,0,8,0,6,2);
        var start=new Vec3(0,62,0);
        var end=new Vec3(3,63,2);
        var timeline=new PremiumCinematicTimeline(durations,start,end,.5,0);
        assertEquals(PremiumCinematicTimeline.Stage.SURFACE_BREAK,timeline.sample(0).stage());
        assertEquals(PremiumCinematicTimeline.Stage.DELIVERY,timeline.sample(8).stage());
        assertEquals(PremiumCinematicTimeline.Stage.FADE,timeline.sample(14).stage());
        assertEquals(end,timeline.sample(100).position());
        assertFalse(timeline.sample(0).stage()==PremiumCinematicTimeline.Stage.VORTEX);
    }

    @Test void validationRejectsOversizedOrNonFiniteScenes() {
        assertThrows(IllegalArgumentException.class,()->new PremiumCinematicTimeline.Durations(30,30,20,20,20,5));
        assertThrows(IllegalArgumentException.class,()->new PremiumCinematicTimeline.Durations(0,0,1,0,2,1));
        var durations=new PremiumCinematicTimeline.Durations(0,0,8,0,6,2);
        assertThrows(IllegalArgumentException.class,()->new PremiumCinematicTimeline(
            durations,new Vec3(0,0,0),new Vec3(1,1,1),Double.NaN,0));
    }
}
