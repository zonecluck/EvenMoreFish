package com.oheers.fish.api.competition;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface ICompetitionBuffManager {

    /**
     * Checks if the player currently has an active competition buff.
     *
     * @param uuid The player's UUID.
     * @return true if the player has an active buff.
     */
    boolean hasBuff(@NotNull UUID uuid);

    /**
     * Checks if the player currently has an active competition buff.
     *
     * @param player The player.
     * @return true if the player has an active buff.
     */
    default boolean hasBuff(@NotNull Player player) {
        return hasBuff(player.getUniqueId());
    }

    /**
     * Gets the luck modifier bonus for a player from competition buffs.
     *
     * @param uuid The player's UUID.
     * @return Luck bonus (e.g. 0.5 for +50% luck).
     */
    double getLuckBonus(@NotNull UUID uuid);

    /**
     * Gets the lure wait time reduction ratio for a player from competition buffs.
     *
     * @param uuid The player's UUID.
     * @return Lure reduction (e.g. 0.3 for 30% faster bite time).
     */
    double getLureBonus(@NotNull UUID uuid);

    /**
     * Gets the double catch chance (0.0 - 1.0) from competition buffs.
     *
     * @param uuid The player's UUID.
     * @return Double catch chance.
     */
    double getDoubleCatchChance(@NotNull UUID uuid);

    /**
     * Gets the exp multiplier from competition buffs.
     *
     * @param uuid The player's UUID.
     * @return EXP multiplier.
     */
    double getExpMultiplier(@NotNull UUID uuid);

    /**
     * Clears all active competition buffs for all players.
     * This is automatically invoked when a new competition begins.
     */
    void clearAllBuffs();

    /**
     * Clears the active competition buff for a specific player.
     *
     * @param uuid The player's UUID.
     */
    void clearBuff(@NotNull UUID uuid);

}
