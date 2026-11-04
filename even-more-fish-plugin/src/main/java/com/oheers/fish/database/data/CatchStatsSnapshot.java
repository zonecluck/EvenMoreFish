package com.oheers.fish.database.data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable event snapshot. Safe to hand to EMF's single-threaded DB executor. */
public record CatchStatsSnapshot(UUID playerId, String playerName,
                                 String fishName, String rarityId,
                                 float length, LocalDateTime caughtAt,
                                 String competitionId) {
    public FishRarityKey fishKey() {
        return FishRarityKey.of(fishName, rarityId);
    }
}
