package com.oheers.fish.fishing.simulator.school;

/** Immutable, validated steering limits. Schooling never creates additional fish or catch outcomes. */
public record SchoolSettings(boolean enabled, int maxSchools, int maxSchoolsPerZone, int maxMembers,
                             int decisionsPerMove, int neighborChecksPerMove, int recruitPerMove,
                             int decisionIntervalTicks, int scatterTicks, int reformTicks,
                             double joinRadius, double roamRadius, double formationGap,
                             double leaderSpeed, double followerSpeed) {
    public SchoolSettings {
        if (maxSchools < 1 || maxSchools > 64 || maxSchoolsPerZone < 1 || maxSchoolsPerZone > maxSchools
            || maxMembers < 2 || maxMembers > 8 || decisionsPerMove < 1 || decisionsPerMove > 160
            || neighborChecksPerMove < 4 || neighborChecksPerMove > 640
            || recruitPerMove < 0 || recruitPerMove > 32 || decisionIntervalTicks < 1 || decisionIntervalTicks > 100
            || scatterTicks < 1 || scatterTicks > 200 || reformTicks < 1 || reformTicks > 200
            || !inRange(joinRadius, 1, 12) || !inRange(roamRadius, 2, 24)
            || !inRange(formationGap, .5, 4) || !inRange(leaderSpeed, .02, .4)
            || !inRange(followerSpeed, .02, .5)) throw new IllegalArgumentException("Invalid schooling limits");
    }
    private static boolean inRange(double value,double min,double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
    public static SchoolSettings disabled() {
        return new SchoolSettings(false,32,6,6,36,144,8,6,32,28,6.0,12.0,1.3,.12,.14);
    }
}
