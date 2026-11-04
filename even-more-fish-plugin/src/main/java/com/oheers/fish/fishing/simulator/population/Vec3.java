package com.oheers.fish.fishing.simulator.population;

/** Lightweight immutable coordinates for one owner-thread simulation. */
public record Vec3(double x, double y, double z) {
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("non-finite position");
        }
    }
    public double distanceSquared(Vec3 p) {
        double dx=x-p.x, dy=y-p.y, dz=z-p.z;
        return dx*dx + dy*dy + dz*dz;
    }
    public Vec3 stepTowards(Vec3 target, double max) {
        double distance = Math.sqrt(distanceSquared(target));
        if (distance <= max || distance < 1.0e-8) return target;
        double k=max/distance;
        return new Vec3(x+(target.x-x)*k, y+(target.y-y)*k, z+(target.z-z)*k);
    }
}
