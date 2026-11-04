package com.oheers.fish.autofish;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AutoFishingServiceTest {
    @Test void onlyAcceptedFishCatchesAdvanceAutoStatisticsAndFocus() throws Exception {
        try (var stores = mockConstruction(AutoFishDataStore.class)) {
            var service = new AutoFishingService(mock(EvenMoreFish.class));
            UUID id = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            var profile = AutoFishProfile.createDefault(id, 300);
            when(stores.constructed().getFirst().getOrCreate(id)).thenReturn(profile);
            var sessionsField = AutoFishingService.class.getDeclaredField("activeSessions");
            sessionsField.setAccessible(true);
            @SuppressWarnings("unchecked") var sessions = (Map<UUID, AutoFishingService.ActiveSession>) sessionsField.get(service);
            var session = new AutoFishingService.ActiveSession(id, 0);
            sessions.put(id, session);
            FishHook hook = mock(FishHook.class);
            var cancelled = new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH);
            cancelled.setCancelled(true);
            service.onPlayerFish(cancelled);
            service.onPlayerFish(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.REEL_IN));
            service.onPlayerFish(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.CAUGHT_ENTITY));
            assertEquals(0, session.getSessionCatches());
            assertEquals(0, session.getFocusStreak());
            service.onPlayerFish(new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH));
            assertEquals(1, session.getSessionCatches());
            assertEquals(1, session.getFocusStreak());
        }
    }
}
