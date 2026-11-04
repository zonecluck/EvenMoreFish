package com.oheers.fish.fishing;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.events.EMFFishCaughtEvent;
import com.oheers.fish.api.events.EMFFishHuntEvent;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.database.DatabaseUtil;
import com.oheers.fish.database.data.CatchStatsSnapshot;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.simulator.commit.SimulatorCatchEventGate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.LocalDateTime;

/** Never query database or touch Bukkit entities on the asynchronous DB worker. */
public class EMFFishListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEMFFishCatch(EMFFishCaughtEvent event) {
        if (event.getFish() instanceof Fish fish && !SimulatorCatchEventGate.deferInternalStats(event)) {
            record(event.getPlayer(), fish, event.getCatchTime());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEMFFishHunt(EMFFishHuntEvent event) {
        if (event.getFish() instanceof Fish fish) {
            record(event.getPlayer(), fish, event.getHuntTime());
        }
    }

    /** Only after Simulator delivers its fish. Never called for canceled/failed commits. */
    public static void recordDeliveredSimulatorCatch(Player player,Fish fish,LocalDateTime caughtAt) {
        record(player,fish,caughtAt);
    }

    private static void record(Player player, Fish fish, LocalDateTime caughtAt) {
        if (!DatabaseUtil.isDatabaseOnline()) {
            return;
        }
        Competition active = Competition.getCurrentlyActive();
        // All Bukkit, plugin config and mutable Fish reads remain on the event thread.
        CatchStatsSnapshot snapshot = new CatchStatsSnapshot(
            player.getUniqueId(), player.getName(), fish.getName(), fish.getRarity().getId(),
            fish.getLength(), caughtAt, active == null ? null : active.getCompetitionName()
        );
        if(!EvenMoreFish.getInstance().getPluginDataManager().enqueueCatch(snapshot)){
            EvenMoreFish.getInstance().getLogger().warning("[Simulator/DB] Catch history enqueue rejected; check DB worker health.");
        }
    }
}
