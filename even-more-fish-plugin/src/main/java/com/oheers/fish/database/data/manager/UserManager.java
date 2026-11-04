package com.oheers.fish.database.data.manager;


import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.model.user.EmptyUserReport;
import com.oheers.fish.database.execute.DbExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

public class UserManager implements Listener {
    private final Database database;
    private final Map<UUID, Integer> userCache;
    private final DbExecutor dbExecutor;
    private final java.util.Set<UUID> onlineUsers = ConcurrentHashMap.newKeySet();

    public UserManager(Database database, DbExecutor dbExecutor) {
        this.database = database;
        this.dbExecutor = dbExecutor;
        this.userCache = new ConcurrentHashMap<>();
    }

    @EventHandler
    public void onJoin(final @NotNull PlayerJoinEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        onlineUsers.add(uuid);
        // Join events run on the server thread. Never access JDBC here.
        try {
            dbExecutor.runAsync(() -> {
                resolveUserId(uuid);
                if (!onlineUsers.contains(uuid)) {
                    userCache.remove(uuid);
                }
            }).exceptionally(failure -> {
                EvenMoreFish.getInstance().getLogger().warning(
                    "Async EMF user preload failed for " + uuid + ": " + failure.getMessage());
                return null;
            });
        } catch (RejectedExecutionException rejected) {
            // Do not turn a crowded DB queue into a failed Bukkit join event.
            EvenMoreFish.getInstance().getLogger().warning(
                "EMF could not prewarm user " + uuid + ": DB queue is full");
        }
    }

    @EventHandler
    public void onLeave(final @NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        onlineUsers.remove(uuid);
        userCache.remove(uuid);
    }

    /** Called by the DB worker only; never from a Bukkit event thread. */
    public int resolveUserId(final UUID uuid) {
        Integer id = userCache.get(uuid);
        if (id != null) {
            return id;
        }

        id = database.getUserId(uuid);
        if (id == 0) {
            id = database.upsertUserReport(new EmptyUserReport(uuid));
        }
        if (id != 0 && onlineUsers.contains(uuid)) {
            userCache.put(uuid, id);
        }
        return id;
    }

    /** Legacy API: use only from a DB worker; the method may perform JDBC. */
    public int getUserId(final UUID uuid) {
        return resolveUserId(uuid);
    }
}
