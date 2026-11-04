package com.oheers.fish.fishing.simulator.population;

import java.util.Objects;

/** Identity of an EMF catalog entry. Never store a mutable Fish template in an ambient agent. */
public record FishKey(String rarityId, String fishId) {
    public FishKey {
        Objects.requireNonNull(rarityId, "rarityId");
        Objects.requireNonNull(fishId, "fishId");
        if (rarityId.isBlank() || fishId.isBlank()) throw new IllegalArgumentException("blank fish key");
    }
}
