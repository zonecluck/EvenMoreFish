package com.oheers.fish.fishing.pool;

import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.api.fishing.FishingLakeProvider;
import com.oheers.fish.api.fishing.FishingLakeProvider.Bonus;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class FishingLakeFixture {
    EvenMoreFish plugin;
    FishingPoolService pools;
    Player player;
    FishHook hook;
    World world;
    Block block;
    ServicesManager services;
    Plugin providerPlugin;
    FishingLakeProvider provider;
    TaskScheduler scheduler;
    MockedStatic<EvenMoreFish> emf;
    Location water;

    @BeforeEach void setup() throws Exception {
        plugin = mock(EvenMoreFish.class);
        when(plugin.getDataFolder()).thenReturn(new File("qa/test-lake-unused"));
        Server server = mock(Server.class);
        services = mock(ServicesManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(services);
        providerPlugin = mock(Plugin.class);
        when(providerPlugin.isEnabled()).thenReturn(true);
        provider = mock(FishingLakeProvider.class);
        when(services.getRegistration(FishingLakeProvider.class)).thenReturn(
            new RegisteredServiceProvider<>(FishingLakeProvider.class, provider, ServicePriority.Normal, providerPlugin));
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        block = mock(Block.class);
        when(block.getType()).thenReturn(Material.WATER);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        water = new Location(world, 2, 64, 3);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(water.clone().add(2, 1, 0));
        when(player.isOnline()).thenReturn(true);
        hook = mock(FishHook.class);
        when(hook.getUniqueId()).thenReturn(UUID.randomUUID());
        when(hook.getShooter()).thenReturn(player);
        when(hook.getWorld()).thenReturn(world);
        when(hook.getLocation()).thenAnswer(ignored -> water.clone());
        when(hook.isValid()).thenReturn(true);
        scheduler = mock(TaskScheduler.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(MyScheduledTask.class));
        emf = mockStatic(EvenMoreFish.class);
        emf.when(EvenMoreFish::getScheduler).thenReturn(scheduler);
        emf.when(EvenMoreFish::getInstance).thenReturn(plugin);
        pools = new FishingPoolService(plugin);
        var field = FishingPoolService.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(pools, new FishingPoolSettings(true, 2, 25, 1.5, false, 32, 48,
            Set.of("rare"), Map.of("static", new FishingPool("static", "world", 2, 64, 3, 8, 35, 3))));
        when(plugin.getFishingPools()).thenReturn(pools);
    }
    @AfterEach void close() { emf.close(); }
}

class FishingLakeServiceTest extends FishingLakeFixture {
    @Test void aquaticPlantsAndBubbleColumnsRetainTheActualLakeBonus() {
        when(provider.resolve(eq(player), any())).thenReturn(new Bonus("island:lake", 25, 2, true));
        pools.track(hook);
        for (Material aquatic : new Material[]{Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.KELP, Material.KELP_PLANT, Material.BUBBLE_COLUMN}) {
            when(block.getType()).thenReturn(aquatic);
            assertEquals(2, pools.multiplier(player, water, "Rare"), aquatic.name());
        }
    }

    @Test void waterloggedBlocksRequireActualWaterAndLavaNeverCounts() {
        Waterlogged data = mock(Waterlogged.class);
        when(block.getType()).thenReturn(Material.OAK_TRAPDOOR);
        when(block.getBlockData()).thenReturn(data);
        when(data.isWaterlogged()).thenReturn(true);
        assertTrue(FishingPoolService.isWater(water));
        when(data.isWaterlogged()).thenReturn(false);
        assertFalse(FishingPoolService.isWater(water));
        when(block.getType()).thenReturn(Material.LAVA);
        when(block.getBlockData()).thenReturn(mock(Levelled.class));
        assertFalse(FishingPoolService.isWater(water));
    }

    @Test void unloadedAndOutOfHeightWaterChecksNeverReadBlocks() {
        clearInvocations(world);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertFalse(FishingPoolService.isWater(water));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        assertFalse(FishingPoolService.isWater(new Location(world, 2, 321, 3)));
        assertFalse(FishingPoolService.isWater(new Location(world, 2, -65, 3)));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test void dynamicLakeWinsWithoutStackingAndUsesActualCasterAndHook() {
        when(provider.resolve(eq(player), any(Location.class))).thenReturn(new Bonus("island:lake", 25, 2, true));
        assertNull(pools.bonus(player, hook));
        assertEquals(1, pools.multiplier(player, water, "Rare"));
        pools.track(hook);
        assertEquals(2, pools.multiplier(player, water, "Rare"));
        assertEquals(1, pools.multiplier(player, water, "Common"));
        assertEquals(1, pools.multiplier(player, water, "Legendary"));
        assertEquals(1, pools.multiplier(player, water.clone().add(5, 0, 0), "Rare"));
        assertEquals(3, pools.multiplier(water, "Rare")); // old location overload stays static and compatible
        verify(provider).resolve(player, water);
        pools.untrack(hook.getUniqueId());
        assertEquals(1, pools.multiplier(player, water, "Rare"));
    }

    @Test void missingDisabledAndThrowingProvidersUseOneStaticPool() {
        pools.track(hook);
        when(provider.resolve(any(), any())).thenThrow(new IllegalStateException("provider failed"));
        assertEquals(3, pools.multiplier(player, water, "Rare"));
        when(providerPlugin.isEnabled()).thenReturn(false);
        clearInvocations(provider);
        assertEquals(3, pools.multiplier(player, water, "Rare"));
        verifyNoInteractions(provider);
        when(services.getRegistration(FishingLakeProvider.class)).thenReturn(null);
        assertEquals(3, pools.multiplier(player, water, "Rare"));
    }

    @Test void dryUnloadedAndWrongWorldHooksCannotResolveProvider() {
        pools.track(hook);
        when(block.getType()).thenReturn(Material.STONE);
        assertNull(pools.bonus(player, hook));
        when(block.getType()).thenReturn(Material.WATER);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertNull(pools.bonus(player, hook));
        when(player.getWorld()).thenReturn(mock(World.class));
        assertNull(pools.bonus(player, hook));
        verifyNoInteractions(provider);
    }

    @Test void recastReplacesOldHookAndUntrackOldDoesNotRemoveNewCast() {
        pools.track(hook);
        FishHook second = mock(FishHook.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getShooter()).thenReturn(player);
        when(second.isValid()).thenReturn(true);
        when(second.getWorld()).thenReturn(world);
        when(second.getLocation()).thenReturn(water);
        pools.track(second);
        assertNull(pools.bonus(player, hook));
        pools.untrack(hook.getUniqueId());
        assertNotNull(pools.bonus(player, second));
        pools.quit(player.getUniqueId());
        assertNull(pools.bonus(player, second));
        verify(scheduler, atLeastOnce()).runTaskTimer(eq(plugin), any(Runnable.class), eq(2L), eq(2L));
    }

    @Test void lakeBonusRejectsNonfiniteAndOutOfContractValues() {
        assertThrows(IllegalArgumentException.class, () -> new Bonus(" ", 25, 2, true));
        assertThrows(IllegalArgumentException.class, () -> new Bonus("lake", Double.NaN, 2, true));
        assertThrows(IllegalArgumentException.class, () -> new Bonus("lake", 86, 2, true));
        assertThrows(IllegalArgumentException.class, () -> new Bonus("lake", 25, Double.POSITIVE_INFINITY, true));
        assertThrows(IllegalArgumentException.class, () -> new Bonus("lake", 25, .5, true));
        assertDoesNotThrow(() -> new Bonus("lake", 85, 10, true));
    }

    @Test void dynamicSpeedReducesNativeRemainingWaitOnceAndLeavingRestoresElapsedCountdown() throws Exception {
        when(provider.resolve(eq(player), any())).thenReturn(new Bonus("island:lake", 25, 2, true));
        when(hook.getState()).thenReturn(FishHook.HookState.BOBBING);
        java.util.concurrent.atomic.AtomicInteger wait = new java.util.concurrent.atomic.AtomicInteger(100);
        when(hook.getWaitTime()).thenAnswer(ignored -> wait.get());
        doAnswer(call -> { wait.set(call.getArgument(0)); return null; }).when(hook).setWaitTime(anyInt());
        pools.track(hook);
        var tick = FishingPoolService.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            tick.invoke(pools);
            assertEquals(75, wait.get());
            wait.set(71); // four native ticks elapsed
            tick.invoke(pools);
            assertEquals(71, wait.get());
            when(block.getType()).thenReturn(Material.STONE);
            tick.invoke(pools);
            assertEquals(96, wait.get());
            when(block.getType()).thenReturn(Material.WATER);
            tick.invoke(pools);
            assertEquals(96, wait.get()); // no repeated speed reduction on re-entry
        }
    }

    @Test void noPoolsAndNoHooksNeedNoTickerAndTerminalStopsTheCastTicker() throws Exception {
        var field = FishingPoolService.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(pools, new FishingPoolSettings(true, 2, 25, 1.5, false, 32, 48, Set.of("rare"), Map.of()));
        verifyNoInteractions(scheduler);
        pools.track(hook);
        var scheduled = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), scheduled.capture(), eq(2L), eq(2L));
        pools.untrack(hook.getUniqueId());
        assertNull(pools.bonus(player, hook));
        var task = FishingPoolService.class.getDeclaredField("task");
        task.setAccessible(true);
        assertNull(task.get(pools));
    }
}
