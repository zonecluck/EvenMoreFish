package com.oheers.fish.fishing.simulator.effects;

import com.oheers.fish.fishing.simulator.population.Vec3;
import java.util.Objects;

/** Pure animation math: the client receives virtual-fish teleports, never a reward. */
public record CatchArc(Vec3 start, Vec3 end, double height) {
    public CatchArc {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!Double.isFinite(height) || height < 0 || height > 3) {
            throw new IllegalArgumentException("arc height must be finite and within 0..3");
        }
    }

    public Vec3 sample(double fraction) {
        double t = Math.max(0, Math.min(1, fraction));
        double ease = t * t * (3.0 - 2.0 * t);
        return new Vec3(
            start.x() + (end.x() - start.x()) * ease,
            start.y() + (end.y() - start.y()) * ease + 4.0 * height * t * (1.0 - t),
            start.z() + (end.z() - start.z()) * ease);
    }
}
