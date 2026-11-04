package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Stage;
import com.oheers.fish.fishing.simulator.effects.PremiumCinematicTimeline.Style;
import com.oheers.fish.fishing.simulator.population.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Bounded, deterministic geometry shared by the native and packet-only scenes. */
public final class CinematicParticlePattern {
    private CinematicParticlePattern() {}

    public static List<Vec3> points(Style style, Stage stage, Vec3 water, Vec3 fish,
                                    long elapsed, double progress, int allowance) {
        if (style == Style.STANDARD || allowance <= 0) return List.of();
        int count = Math.min(allowance, style == Style.LEGENDARY ? 8 : 6);
        double t = Math.max(0, Math.min(1, progress));
        double size = style == Style.LEGENDARY ? .75 : .5;
        var points = new ArrayList<Vec3>(count);
        for (int i = 0; i < count; i++) {
            double angle = elapsed * .22 + 2 * Math.PI * i / count;
            double radius;
            Vec3 center;
            double height;
            switch (stage) {
                case ANTICIPATION -> {
                    center = water;
                    radius = size * (.35 + .35 * t);
                    height = .03;
                }
                case VORTEX -> {
                    center = water;
                    radius = size * (.8 - .45 * t);
                    height = .08 + .55 * t + .18 * Math.sin(angle);
                }
                case SURFACE_BREAK -> {
                    center = i % 2 == 0 ? water : fish;
                    radius = i % 2 == 0 ? size * (1 + t) : .12;
                    height = .04;
                }
                case HERO_REVEAL -> {
                    center = fish;
                    radius = size * (.7 + .15 * Math.sin(Math.PI * t));
                    height = .25 * Math.sin(angle);
                    if (style == Style.LEGENDARY && i % 2 == 0) height += .28;
                }
                case DELIVERY -> {
                    center = fish;
                    radius = size * .18 * (1 - t);
                    height = -.12 * (1 - t);
                }
                case FADE -> {
                    center = fish;
                    radius = size * (.15 + .5 * t);
                    height = .12 * Math.sin(angle);
                }
                default -> throw new IllegalStateException("Unsupported cinematic stage");
            }
            points.add(new Vec3(center.x() + Math.cos(angle) * radius, center.y() + height,
                center.z() + Math.sin(angle) * radius));
        }
        return List.copyOf(points);
    }
}
