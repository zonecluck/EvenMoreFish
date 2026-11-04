package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.population.Vec3;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure deterministic animation timeline. No Paper API, world access, inventory or rewards.
 * Every stage boundary shares an endpoint with the next stage (no positional snap).
 */
public final class PremiumCinematicTimeline {
    public enum Stage { ANTICIPATION, VORTEX, SURFACE_BREAK, HERO_REVEAL, DELIVERY, FADE }
    public enum Style {
        STANDARD, EPIC, LEGENDARY;

        public static Style forRarity(String rarity) {
            String id = rarity.toLowerCase(java.util.Locale.ROOT);
            if (id.contains("legend") || id.contains("myth") || id.contains("anomal") || id.contains("ky_di")) return LEGENDARY;
            return id.contains("epic") ? EPIC : STANDARD;
        }
    }
    public record Pose(double scale, double yaw, double roll) {}

    public record Durations(int anticipation, int vortex, int surface, int reveal, int delivery, int fade) {
        public Durations {
            if (anticipation < 0 || vortex < 0 || surface < 2 || reveal < 0 || delivery < 2 || fade < 1)
                throw new IllegalArgumentException("Invalid cinematic stage duration");
            int total = anticipation + vortex + surface + reveal + delivery + fade;
            if (total < 8 || total > 90) throw new IllegalArgumentException("Cinematic total duration must be 8..90 ticks");
        }
        public int total() { return anticipation + vortex + surface + reveal + delivery + fade; }
        public Map<Stage,Integer> stages() {
            var stages = new EnumMap<Stage,Integer>(Stage.class);
            stages.put(Stage.ANTICIPATION, anticipation);
            stages.put(Stage.VORTEX, vortex);
            stages.put(Stage.SURFACE_BREAK, surface);
            stages.put(Stage.HERO_REVEAL, reveal);
            stages.put(Stage.DELIVERY, delivery);
            stages.put(Stage.FADE, fade);
            return Map.copyOf(stages);
        }
    }

    public record Frame(Stage stage, Vec3 position, boolean visible, double progress) {}

    private final Durations durations;
    private final Vec3 water;
    private final Vec3 target;
    private final Vec3 surface;
    private final Vec3 hero;
    private final double arcHeight;
    private final double orbit;
    private final Style style;

    public PremiumCinematicTimeline(Durations durations, Vec3 water, Vec3 target, double arcHeight, double orbit) {
        this(durations, water, target, arcHeight, orbit, Style.STANDARD);
    }

    public PremiumCinematicTimeline(Durations durations, Vec3 water, Vec3 target, double arcHeight, double orbit,
                                    Style style) {
        this.durations = Objects.requireNonNull(durations);
        this.style = Objects.requireNonNull(style);
        this.water = Objects.requireNonNull(water);
        this.target = Objects.requireNonNull(target);
        if (!Double.isFinite(arcHeight) || arcHeight < 0 || arcHeight > 3 ||
            !Double.isFinite(orbit) || orbit < 0 || orbit > 1.5)
            throw new IllegalArgumentException("Invalid cinematic motion bounds");
        this.arcHeight = arcHeight;
        this.orbit = orbit;
        this.surface = new Vec3(water.x(), water.y() + Math.max(.3, arcHeight), water.z());
        // Pose slightly towards the viewer, above the surface, without touching camera transform.
        this.hero = durations.reveal() == 0 ? surface : new Vec3(water.x() + (target.x() - water.x()) * .32,
            water.y() + Math.max(.25, arcHeight * .8),
            water.z() + (target.z() - water.z()) * .32);
    }

    public int duration() { return durations.total(); }
    public Style style() { return style; }

    /** ItemDisplay pose; packet fish share the position curve without requiring display metadata. */
    public Pose pose(double elapsed) {
        Frame frame = sample(elapsed);
        double t = frame.progress();
        double scale = frame.visible() ? (style == Style.LEGENDARY ? 1.05 : style == Style.EPIC ? .9 : .7) : 0;
        if (frame.stage() == Stage.SURFACE_BREAK) scale *= smooth(t);
        if (frame.stage() == Stage.FADE) scale *= 1 - smooth(t);
        if (style == Style.STANDARD) return new Pose(scale, 0, Math.sin(Math.PI * t) * .12);
        double envelope = Math.pow(Math.sin(Math.PI * t), 2);
        double yaw = switch (frame.stage()) {
            case HERO_REVEAL -> 2 * Math.PI * smooth(t);
            case DELIVERY, FADE -> 2 * Math.PI;
            default -> 0;
        };
        if (frame.stage() == Stage.HERO_REVEAL) scale *= 1 + .12 * envelope;
        double roll = frame.stage() == Stage.FADE ? 0 : .2 * envelope * Math.sin(2 * Math.PI * t);
        return new Pose(scale, yaw, roll);
    }

    public Frame sample(long elapsed) {
        return sample((double) elapsed);
    }

    public Frame sample(double elapsed) {
        double time = Math.max(0, Math.min(elapsed, durations.total()));
        int offset = 0;
        for (Stage stage : Stage.values()) {
            int length = switch (stage) {
                case ANTICIPATION -> durations.anticipation();
                case VORTEX -> durations.vortex();
                case SURFACE_BREAK -> durations.surface();
                case HERO_REVEAL -> durations.reveal();
                case DELIVERY -> durations.delivery();
                case FADE -> durations.fade();
            };
            if (length == 0) continue;
            if (time < offset + length || stage == Stage.FADE) {
                double progress = Math.max(0, Math.min(1, (double)(time - offset) / length));
                return frame(stage, progress);
            }
            offset += length;
        }
        return frame(Stage.FADE, 1);
    }

    private Frame frame(Stage stage, double t) {
        double s = smooth(t);
        return switch (stage) {
            case ANTICIPATION, VORTEX -> new Frame(stage, water, false, t);
            case SURFACE_BREAK -> {
                Vec3 base = lerp(water, surface, s);
                if (style == Style.STANDARD) yield new Frame(stage, base, true, t);
                double envelope = 16 * t * t * (1-t) * (1-t);
                double theta = 2 * Math.PI * s;
                yield new Frame(stage, lateral(base, orbit * .3 * envelope * Math.sin(theta),
                    orbit * .15 * envelope * (Math.cos(theta) - 1), arcHeight * .08 * envelope), true, t);
            }
            case HERO_REVEAL -> {
                Vec3 center = lerp(surface, hero, s);
                // An orbit that fades in/out at the stage boundaries, preventing jumps.
                double theta = 2 * Math.PI * s;
                double lateral = orbit * Math.pow(Math.sin(Math.PI * t), 2);
                if (style != Style.STANDARD) {
                    yield new Frame(stage, lateral(center, lateral * Math.sin(theta),
                        lateral * .55 * (Math.cos(theta) - 1), .12 * lateral * Math.sin(theta)), true, t);
                }
                double dx = target.x() - water.x(), dz = target.z() - water.z();
                double len = Math.max(.001, Math.sqrt(dx * dx + dz * dz));
                yield new Frame(stage, new Vec3(center.x() - dz / len * lateral * Math.sin(theta),
                    center.y() + .14 * lateral * Math.sin(theta),
                    center.z() + dx / len * lateral * Math.sin(theta)), true, t);
            }
            case DELIVERY -> {
                Vec3 base = lerp(hero, target, s);
                // Squared envelope has zero endpoint velocity, matching the adjoining stages.
                double envelope = 16 * t * t * (1-t) * (1-t);
                if (style != Style.STANDARD) {
                    yield new Frame(stage, lateral(base, orbit * .25 * envelope * Math.sin(Math.PI * t),
                        0, arcHeight * .5 * envelope), true, t);
                }
                yield new Frame(stage, new Vec3(base.x(), base.y() + arcHeight * .65 * envelope, base.z()), true, t);
            }
            case FADE -> new Frame(stage, target, true, t);
        };
    }
    private Vec3 lateral(Vec3 base, double sideways, double forwards, double height) {
        double dx = target.x() - water.x(), dz = target.z() - water.z();
        double length = Math.hypot(dx, dz);
        double fx = length < .001 ? 1 : dx / length, fz = length < .001 ? 0 : dz / length;
        return new Vec3(base.x() - fz * sideways + fx * forwards, base.y() + height,
            base.z() + fx * sideways + fz * forwards);
    }
    private static double smooth(double t) { return t * t * (3 - 2*t); }
    private static Vec3 lerp(Vec3 start, Vec3 end, double t) {
        return new Vec3(start.x() + (end.x()-start.x())*t,
            start.y()+(end.y()-start.y())*t, start.z()+(end.z()-start.z())*t);
    }
}
