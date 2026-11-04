package com.oheers.fish.fishing.simulator.presentation;

/** Immutable, validated UI defaults. All quantities are bounded by the YAML loader. */
public record SimulatorPresentationSettings(
    boolean enabled, int minimumIntervalTicks, int biteTtlTicks,
    int resultTtlTicks, boolean interestedHint, UiProfile defaultProfile
) {
    public static SimulatorPresentationSettings disabled() {
        return new SimulatorPresentationSettings(false, 5, 8, 35, false, UiProfile.BALANCED);
    }
}
