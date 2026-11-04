package com.oheers.fish;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.oheers.fish.fishing.simulator.population.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual 1.21 adapter compiled and executed against the installed PacketEvents 2.13 binary. */
public final class PacketTransportRecoveryCheck {
    public static void main(String[] args) {
        EvenMoreFish plugin = mock(EvenMoreFish.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Player viewer = mock(Player.class);
        when(viewer.isOnline()).thenReturn(true);
        World world = mock(World.class);
        UUID worldId = UUID.randomUUID(), viewerId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
        when(viewer.getWorld()).thenReturn(world);
        PluginManager plugins = mock(PluginManager.class);
        Plugin installed = mock(Plugin.class);
        when(installed.isEnabled()).thenReturn(true);
        when(plugins.getPlugin("packetevents")).thenReturn(installed);
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        PlayerManager players = mock(PlayerManager.class);
        when(api.getPlayerManager()).thenReturn(players);
        ServerManager server = mock(ServerManager.class);
        when(api.getServerManager()).thenReturn(server);
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_1);
        try (var bukkit = mockStatic(Bukkit.class); var packetEvents = mockStatic(PacketEvents.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            bukkit.when(() -> Bukkit.getPlayer(viewerId)).thenReturn(viewer);
            packetEvents.when(PacketEvents::getAPI).thenReturn(api);
            var transport = new Paper121PacketEventsTransport(plugin);
            var fish = new FishAgent(UUID.randomUUID(), 1_500_000_123, new FishKey("cosmetic", "native-hook"),
                FishVisualModel.COD, "lake", worldId, viewerId, 40, new Vec3(0, 64, 0));
            if (!transport.available()) throw new AssertionError("Backend should initially be ready");
            doThrow(new IllegalStateException("simulated packet send failure")).when(players)
                .sendPacket(eq(viewer), isA(WrapperPlayServerEntityTeleport.class));
            if (transport.teleport(viewerId, fish)) throw new AssertionError("Movement must fail closed");
            if (transport.available()) throw new AssertionError("Visual breaker must remain tripped");
            if (!transport.destroy(viewerId, fish.entityId())) throw new AssertionError("Cleanup must bypass visual breaker");
            verify(players).sendPacket(eq(viewer), isA(WrapperPlayServerDestroyEntities.class));
            when(installed.isEnabled()).thenReturn(false);
            if (transport.destroy(viewerId, fish.entityId())) throw new AssertionError("Disabled backend must remain guarded");
            System.out.println("PASS: PacketEvents 2.13 linkage and destroy recovery after visual breaker");
        }
    }
}
