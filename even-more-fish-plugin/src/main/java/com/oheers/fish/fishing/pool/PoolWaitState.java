package com.oheers.fish.fishing.pool;

/** One reduction per cast; remembers the removed ticks without compounding every update. */
final class PoolWaitState {
    private int removedTicks;
    private boolean used;

    int reduce(int currentWait, double speedPercent) {
        if (used || currentWait <= 0 || speedPercent <= 0 || !Double.isFinite(speedPercent)) return currentWait;
        used = true;
        int reduced = Math.max(1, (int) Math.round(currentWait * (1 - Math.min(85, speedPercent) / 100)));
        removedTicks = currentWait - reduced;
        return reduced;
    }

    int restore(int currentWait) {
        int restored = currentWait > 0 ? (int) Math.min(Integer.MAX_VALUE, (long) currentWait + removedTicks) : currentWait;
        removedTicks = 0;
        return restored;
    }

    boolean used() { return used; }
}
