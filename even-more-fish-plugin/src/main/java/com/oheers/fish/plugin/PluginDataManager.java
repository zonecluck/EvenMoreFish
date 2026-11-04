package com.oheers.fish.plugin;


import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.DatabaseUtil;
import com.oheers.fish.database.data.CatchStatsSnapshot;
import com.oheers.fish.database.execute.DbExecutor;
import com.oheers.fish.database.model.user.EmptyUserReport;
import com.oheers.fish.database.data.FishLogKey;
import com.oheers.fish.database.data.FishRarityKey;
import com.oheers.fish.database.data.UserFishRarityKey;
import com.oheers.fish.database.data.manager.DataManager;
import com.oheers.fish.database.data.manager.UserManager;
import com.oheers.fish.database.data.strategy.impl.CompetitionSavingStrategy;
import com.oheers.fish.database.data.strategy.impl.FishLogSavingStrategy;
import com.oheers.fish.database.data.strategy.impl.FishStatsSavingStrategy;
import com.oheers.fish.database.data.strategy.impl.UserFishStatsSavingStrategy;
import com.oheers.fish.database.data.strategy.impl.UserReportsSavingStrategy;
import com.oheers.fish.database.model.CompetitionReport;
import com.oheers.fish.database.model.fish.FishLog;
import com.oheers.fish.database.model.fish.FishStats;
import com.oheers.fish.database.model.user.UserFishStats;
import com.oheers.fish.database.model.user.UserReport;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.FishManager;
import com.oheers.fish.fishing.items.Rarity;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.concurrent.RejectedExecutionException;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class PluginDataManager {
    private final EvenMoreFish plugin;
    private Database database;
    private UserManager userManager;
    private DbExecutor dbExecutor;

    // Data Managers
    private DataManager<Collection<FishLog>> fishLogDataManager;
    private DataManager<FishStats> fishStatsDataManager;
    private DataManager<UserFishStats> userFishStatsDataManager;
    private DataManager<UserReport> userReportDataManager;
    private DataManager<CompetitionReport> competitionDataManager;

    public PluginDataManager(EvenMoreFish plugin) {
        this.plugin = plugin;

        init();
    }

    public void init() {
        if (!MainConfig.getInstance().databaseEnabled()) {
            plugin.getLogger().info("Database is disabled in config");
            return;
        }

        this.database = new Database();
        this.dbExecutor = new DbExecutor(1, "emf-catch-db", 8192);
        initDataManagers();
    }

    public void initDataManagers() {
        this.userManager = new UserManager(database, dbExecutor);
        this.fishLogDataManager = new DataManager<>(new FishLogSavingStrategy(), key -> {
            FishLogKey logKey = FishLogKey.from(key);
            return Collections.singleton(database.getFishLog(logKey.getUserId(), logKey.getFishName(), logKey.getFishRarity(), logKey.getDateTime()));
        });
        this.fishStatsDataManager = new DataManager<>(new FishStatsSavingStrategy(), key -> {
            final FishRarityKey fishRarityKey = FishRarityKey.from(key);
            return database.getFishStats(fishRarityKey.fishName(),fishRarityKey.fishRarity());
        });

        this.userFishStatsDataManager = new DataManager<UserFishStats>(
            new UserFishStatsSavingStrategy(),
            key -> {
                final UserFishRarityKey userFishRarityKey = UserFishRarityKey.from(key);
                return database.getUserFishStats(userFishRarityKey.userId(), userFishRarityKey.fishName(), userFishRarityKey.fishRarity());
            },
            Long.valueOf(MainConfig.getInstance().getUserFishStatsSaveInterval()),
            TimeUnit.valueOf(MainConfig.getInstance().getSaveIntervalUnit())
        );

        this.userReportDataManager = new DataManager<>(new UserReportsSavingStrategy(), uuid -> database.getUserReport(UUID.fromString(uuid)));
        this.competitionDataManager = new DataManager<CompetitionReport>(
            new CompetitionSavingStrategy(),
            key -> database.getCompetitionReport(Integer.parseInt(key)),
            Long.valueOf(MainConfig.getInstance().getCompetitionSaveInterval()),
            TimeUnit.valueOf(MainConfig.getInstance().getSaveIntervalUnit())
        );
    }

    /** Bounded backpressure guard called on the event thread before the catch event. */
    public boolean hasCatchWriteCapacity() {
        return dbExecutor == null || dbExecutor.hasCapacity();
    }

    /** Schedule immutable catch snapshots; no Player, Fish, World or Bukkit API in DB thread. */
    public boolean enqueueCatch(CatchStatsSnapshot snapshot) {
        if (dbExecutor == null) {
            return false;
        }
        try {
            dbExecutor.runAsync(() -> persistCatch(snapshot)).whenComplete((ignored, error) -> {
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not persist EMF catch " + snapshot.playerId(), error);
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            plugin.getLogger().log(Level.SEVERE, "EMF catch DB queue saturated; report not persisted for " + snapshot.playerId(), rejected);
            return false;
        }
    }

    private void persistCatch(CatchStatsSnapshot snapshot) {
        int userId = userManager.resolveUserId(snapshot.playerId());
        if (userId <= 0) {
            throw new IllegalStateException("Unable to resolve DB user ID " + snapshot.playerId());
        }
        String key = snapshot.fishKey().toString();
        String userFishKey = UserFishRarityKey.of(userId, snapshot.fishName(), snapshot.rarityId()).toString();

        FishLog log = new FishLog(userId, snapshot.fishName(), snapshot.rarityId(),
            snapshot.caughtAt(), snapshot.length(), snapshot.competitionId());
        fishLogDataManager.update(userFishKey, Collections.singletonList(log));

        UserFishStats userStats = userFishStatsDataManager.peek(userFishKey);
        if (userStats == null) {
            var result = database.loadUserFishStats(userId, snapshot.fishName(), snapshot.rarityId());
            if (result.isUnreadable()) throw new IllegalStateException("Unreadable user fish stats " + userFishKey);
            userStats = result.isFound() ? result.getValue() :
                new UserFishStats(userId, snapshot.fishName(), snapshot.rarityId(),
                    snapshot.caughtAt(), snapshot.length(), snapshot.length(), 0);
        }
        boolean firstDiscovery = userStats.getQuantity() == 0;
        userStats.setShortestLength(Math.min(userStats.getShortestLength(), snapshot.length()));
        userStats.setLongestLength(Math.max(userStats.getLongestLength(), snapshot.length()));
        userStats.incrementQuantity();
        userFishStatsDataManager.update(userFishKey, userStats);
        if (firstDiscovery) {
            var cinematics = plugin.getCatchCinematics();
            if (cinematics != null) {
                cinematics.markFirstTimeCatch(snapshot.playerId());
            }
            notifyFirstFishDiscovery(snapshot);
        }

        FishStats fishStats = fishStatsDataManager.peek(key);
        if (fishStats == null) {
            var result = database.loadFishStats(snapshot.fishName(), snapshot.rarityId());
            if (result.isUnreadable()) throw new IllegalStateException("Unreadable global fish stats " + key);
            fishStats = result.isFound() ? result.getValue() : FishStats.firstCatch(
                snapshot.fishName(), snapshot.rarityId(), snapshot.caughtAt(),
                snapshot.playerId(), snapshot.playerName(), snapshot.length());
        }
        if (fishStats.getShortestLength() > snapshot.length()) {
            fishStats.setShortestLength(snapshot.length());
            fishStats.setShortestFisher(snapshot.playerId(), snapshot.playerName());
        }
        if (fishStats.getLongestLength() < snapshot.length()) {
            fishStats.setLongestLength(snapshot.length());
            fishStats.setLongestFisher(snapshot.playerId(), snapshot.playerName());
        }
        fishStats.incrementQuantity();
        fishStatsDataManager.update(key, fishStats);

        String userKey = snapshot.playerId().toString();
        UserReport report = userReportDataManager.get(userKey, id -> database.getUserReport(snapshot.playerId()));
        if (report == null) report = new EmptyUserReport(snapshot.playerId());
        if (report.getShortestLength() == -1 || report.getShortestLength() > snapshot.length()) {
            report.setShortestLength(snapshot.length());
            report.setShortestFish(snapshot.fishKey());
        }
        if (report.getLargestLength() < snapshot.length()) {
            report.setLargestLength(snapshot.length());
            report.setLargestFish(snapshot.fishKey());
        }
        if (report.getFirstFish().toString().equals(".")) {
            report.setFirstFish(snapshot.fishKey());
        }
        report.setRecentFish(snapshot.fishKey());
        report.incrementFishCaught(1);
        report.incrementTotalLength(snapshot.length());
        userReportDataManager.update(userKey, report);
    }

    private void notifyFirstFishDiscovery(CatchStatsSnapshot snapshot) {
        try {
            var scheduler = EvenMoreFish.getScheduler();
            if (scheduler == null) {
                return;
            }
            scheduler.runTask(() -> {
                Runnable announceDiscovery = () -> {
                    Player player = Bukkit.getPlayer(snapshot.playerId());
                    if (player == null || !player.isOnline()) {
                        return;
                    }
                    Rarity rarity = FishManager.getInstance().getRarity(snapshot.rarityId());
                    Fish fish = rarity != null ? rarity.getFish(snapshot.fishName()) : null;
                    MiniMessage mm = MiniMessage.miniMessage();
                    String rarityDisplay = rarity != null ? mm.serialize(rarity.getDisplayName().getComponentMessage()) : snapshot.rarityId();
                    String colour = "<aqua>";
                    String fishDisplay = fish != null ? mm.serialize(fish.getDisplayName().getComponentMessage()) : colour + snapshot.fishName();
                    player.sendMessage(mm.deserialize(
                        "<green><bold>✦ MỞ KHÓA INDEX! ✦</bold></green> <white>Bạn vừa mở khóa cá mới: "
                            + fishDisplay + " <gray>(" + rarityDisplay + "<gray>)</gray> trong Bộ Sưu Tập! "
                            + "<click:run_command:'/emf index'><hover:show_text:'<green>Nhấn để mở Bộ Sưu Tập Cá (/index)</green>'><aqua><underlined>[Mở /index]</underlined></aqua></hover></click></white>"
                    ));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.85F, 1.25F);
                };
                var cinematics = plugin.getCatchCinematics();
                if (cinematics != null && cinematics.isRollingOrShowcasing(snapshot.playerId())) {
                    cinematics.enqueueRevealCallback(snapshot.playerId(), announceDiscovery);
                } else {
                    announceDiscovery.run();
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    public void shutdown() {
        if (database == null) {
            return;
        }

        try {
            // Drain ordered catch writes before closing DB; do not interrupt active writes.
            if (dbExecutor != null && !dbExecutor.awaitShutdown(15, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("EMF DB worker did not drain within 15 seconds. DB will stay open to avoid mid-write closure.");
                return;
            }
            if (!DatabaseUtil.isDatabaseOnline()) {
                database.shutdown();
                return;
            }
            // Flush all pending data
            fishLogDataManager.shutdown();
            userReportDataManager.shutdown();
            userFishStatsDataManager.shutdown();
            fishStatsDataManager.shutdown();
            competitionDataManager.shutdown();

            // Close database connection
            database.shutdown();
            plugin.getLogger().info("Database connections closed successfully");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error while shutting down database", e);
        }
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public DataManager<Collection<FishLog>> getFishLogDataManager() {
        return fishLogDataManager;
    }

    public DataManager<FishStats> getFishStatsDataManager() {
        return fishStatsDataManager;
    }

    public DataManager<UserFishStats> getUserFishStatsDataManager() {
        return userFishStatsDataManager;
    }

    public DataManager<UserReport> getUserReportDataManager() {
        return userReportDataManager;
    }

    public DataManager<CompetitionReport> getCompetitionDataManager() {
        return competitionDataManager;
    }

    public Database getDatabase() {
        return database;
    }


}
