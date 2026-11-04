package com.oheers.fish.fishing.simulator.effects;

/** One owner-thread reservation ledger; a failed send never returns its slot. */
final class CinematicParticleBudget {
    private final int limit;
    private long tick = Long.MIN_VALUE;
    private int consumed;

    CinematicParticleBudget(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Particle limit must be positive");
        this.limit = limit;
    }

    int reserve(long currentTick, int wanted) {
        if (tick != currentTick) { tick = currentTick; consumed = 0; }
        int allowed = Math.min(Math.max(0, wanted), limit - consumed);
        consumed += allowed;
        return allowed;
    }
}
