package com.oheers.fish.fishing.simulator.zones;

import com.oheers.fish.config.simulator.SimulatorSettings;

import java.util.Comparator;
import java.util.List;

/** Deterministic, Bukkit-independent priority policy for already-matched zones. */
public final class ZoneSelection {
    private ZoneSelection() {}

    public record Result(SimulatorSettings.Zone zone, boolean denied) {
        public boolean matched() { return zone != null; }
    }

    public static Result choose(List<SimulatorSettings.Zone> candidates) {
        Comparator<SimulatorSettings.Zone> order = Comparator
            .comparingInt(SimulatorSettings.Zone::priority).reversed()
            .thenComparing(SimulatorSettings.Zone::id);
        List<SimulatorSettings.Zone> enabled = candidates.stream()
            .filter(SimulatorSettings.Zone::enabled).toList();
        SimulatorSettings.Zone denial = enabled.stream()
            .filter(SimulatorSettings.Zone::denySimulator).min(order).orElse(null);
        if (denial != null) return new Result(denial, true);
        return new Result(enabled.stream().min(order).orElse(null), false);
    }
}
