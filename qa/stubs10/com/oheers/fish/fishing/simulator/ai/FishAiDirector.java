package com.oheers.fish.fishing.simulator.ai;
public final class FishAiDirector {
 public enum Phase { SEARCHING, INTERESTED, APPROACHING, BITE_READY, FLEEING }
 public enum Outcome { SUCCESS_PREVIEW_ONLY, SUCCESS_COMMITTED, COMMIT_REJECTED,
 COMMIT_FAILED, EARLY_REEL, MISSED_WINDOW, NO_FISH, LOST_FISH, ABORTED }
}
