package com.oheers.fish.competition.buff;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class CompetitionBuff {

    private final int rank;
    private final double luckBonus;
    private final double lureBonus;
    private final double doubleCatchChance;
    private final double expMultiplier;
    private final Instant grantedTime;

    public CompetitionBuff(int rank, double luckBonus, double lureBonus, double doubleCatchChance, double expMultiplier) {
        this.rank = rank;
        this.luckBonus = luckBonus;
        this.lureBonus = lureBonus;
        this.doubleCatchChance = doubleCatchChance;
        this.expMultiplier = expMultiplier;
        this.grantedTime = Instant.now();
    }

    public int getRank() {
        return rank;
    }

    public double getLuckBonus() {
        return luckBonus;
    }

    public double getLureBonus() {
        return lureBonus;
    }

    public double getDoubleCatchChance() {
        return doubleCatchChance;
    }

    public double getExpMultiplier() {
        return expMultiplier;
    }

    public Instant getGrantedTime() {
        return grantedTime;
    }

    @Override
    public String toString() {
        return "CompetitionBuff{" +
                "rank=" + rank +
                ", luckBonus=" + luckBonus +
                ", lureBonus=" + lureBonus +
                ", doubleCatchChance=" + doubleCatchChance +
                ", expMultiplier=" + expMultiplier +
                ", grantedTime=" + grantedTime +
                '}';
    }
}
