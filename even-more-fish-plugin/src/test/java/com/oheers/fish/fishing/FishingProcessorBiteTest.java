package com.oheers.fish.fishing;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.config.MainConfig;
import com.oheers.fish.fishing.pool.HookedFishPacketService;
import com.oheers.fish.fishing.pool.FishingPoolService;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FishingProcessorBiteTest {
    EvenMoreFish plugin;
    MainConfig config;
    Player player;
    FishHook hook;
    PlayerInventory inventory;
    HookedFishPacketService packets;
    FishingProcessor processor;
    MockedStatic<EvenMoreFish> emf;
    MockedStatic<MainConfig> main;
    PlainRod rod;

    @BeforeEach void setup() throws Exception {
        plugin = mock(EvenMoreFish.class);
        packets = mock(HookedFishPacketService.class);
        when(plugin.getHookedFishPackets()).thenReturn(packets);
        emf = mockStatic(EvenMoreFish.class);
        emf.when(EvenMoreFish::getInstance).thenReturn(plugin);
        config = mock(MainConfig.class);
        main = mockStatic(MainConfig.class);
        main.when(MainConfig::getInstance).thenReturn(config);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        hook = mock(FishHook.class);
        when(hook.getUniqueId()).thenReturn(UUID.randomUUID());
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        rod = new PlainRod();
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(rod);
        processor = spy(new FishingProcessor());
        doReturn(true).when(processor).isCustomFishAllowed(player);
        // Reproduce the state of one previously admitted HIGHEST/MONITOR native cast.
        var type = Class.forName(FishingProcessor.class.getName() + "$CastSnapshot");
        var constructor = type.getDeclaredConstructor(UUID.class, EquipmentSlot.class, ItemStack.class, long.class);
        constructor.setAccessible(true);
        var casts = FishingProcessor.class.getDeclaredField("castSnapshots");
        casts.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, Object> map = (Map<UUID, Object>) casts.get(processor);
        map.put(player.getUniqueId(), constructor.newInstance(hook.getUniqueId(), EquipmentSlot.HAND, rod, System.nanoTime()));
    }
    @AfterEach void close() { main.close(); emf.close(); }

    private PlayerFishEvent bite(boolean accepted) throws Exception {
        if (accepted) {
            var field = FishingProcessor.class.getDeclaredField("acceptedBites");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") Set<UUID> bites = (Set<UUID>) field.get(processor);
            bites.add(hook.getUniqueId());
        }
        return new PlayerFishEvent(player, null, hook, EquipmentSlot.HAND, PlayerFishEvent.State.BITE);
    }

    @Test void monitorRequiresAcceptedUncancelledBiteAndConsumesAdmissionOnce() throws Exception {
        try (var animations = mockStatic(com.oheers.fish.effects.AnimationManager.class)) {
        var animation = mock(com.oheers.fish.effects.AnimationManager.class);
        animations.when(com.oheers.fish.effects.AnimationManager::getInstance).thenReturn(animation);
        processor.afterFishEvent(bite(false));
        verifyNoInteractions(packets);
        var cancelled = bite(true);
        cancelled.setCancelled(true);
        processor.afterFishEvent(cancelled);
        verifyNoInteractions(packets);
        verifyNoInteractions(animation);
        processor.afterFishEvent(bite(true));
        processor.afterFishEvent(bite(false));
        verify(packets, times(1)).bite(player, hook);
        verify(animation, times(1)).playHookBiteParticles(player, hook.getLocation());
        }
    }

    @Test void monitorRejectsChangedHookRodAndCurrentFishingPermission() throws Exception {
        UUID originalHook = hook.getUniqueId();
        when(hook.getUniqueId()).thenReturn(UUID.randomUUID());
        processor.afterFishEvent(bite(true));
        when(hook.getUniqueId()).thenReturn(originalHook);
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.STONE));
        processor.afterFishEvent(bite(true));
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(rod);
        when(config.requireFishingPermission()).thenReturn(true);
        processor.afterFishEvent(bite(true));
        verifyNoInteractions(packets);
    }

    @Test void doubleCatchCueWaitsForAnAcceptedCatchAndRunsOnce() throws Exception {
        var fish = mock(com.oheers.fish.fishing.items.Fish.class);
        var visualType = Class.forName(FishingProcessor.class.getName() + "$PendingCatchVisual");
        var constructor = visualType.getDeclaredConstructor(Location.class, com.oheers.fish.fishing.items.Fish.class,
            ItemStack.class, int.class, int.class, boolean.class);
        constructor.setAccessible(true);
        var pendingField = FishingProcessor.class.getDeclaredField("pendingVisuals");
        pendingField.setAccessible(true);
        @SuppressWarnings("unchecked") var pending = (Map<UUID, Object>) pendingField.get(processor);
        Object visual = constructor.newInstance(null, fish, new ItemStack(Material.COD), 2, 5, true);
        try (var animations = mockStatic(com.oheers.fish.effects.AnimationManager.class)) {
            var animation = mock(com.oheers.fish.effects.AnimationManager.class);
            animations.when(com.oheers.fish.effects.AnimationManager::getInstance).thenReturn(animation);
            pending.put(hook.getUniqueId(), visual);
            var cancelled = new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH);
            cancelled.setCancelled(true);
            processor.afterFishEvent(cancelled);
            verifyNoInteractions(animation);
            pending.put(hook.getUniqueId(), visual);
            var caught = new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH);
            processor.afterFishEvent(caught);
            processor.afterFishEvent(caught);
            verify(animation, times(1)).playDoubleFishAnimation(player, fish);
        }
    }

    @Test void monitorRechecksGlobalFishingPolicy() throws Exception {
        doReturn(false).when(processor).isCustomFishAllowed(player);
        processor.afterFishEvent(bite(true));
        verifyNoInteractions(packets);
    }

    @Test void monitorRechecksRequiredCustomRod() throws Exception {
        when(config.requireCustomRod()).thenReturn(true);
        // Isolate the existing rod checker; initializing its singleton requires loaded fish/bait catalogs.
        try (var checks = mockStatic(com.oheers.fish.Checks.class)) {
            checks.when(() -> com.oheers.fish.Checks.canUseRod(rod)).thenReturn(false);
            processor.afterFishEvent(bite(true));
            checks.verify(() -> com.oheers.fish.Checks.canUseRod(rod));
            verifyNoInteractions(packets);
        }
    }

    @Test void missedBiteKeepsNativeCastForTheNextCatchOnTheSameHook() {
        FishingPoolService pools = mock(FishingPoolService.class);
        when(plugin.getFishingPools()).thenReturn(pools);
        Location water = new Location(mock(World.class), 4, 64, 8);
        when(hook.getLocation()).thenReturn(water);
        Item nativeCatch = mock(Item.class);
        ItemStack fish = new ItemStack(Material.COD);
        doReturn(fish).when(processor).getCaughtItem(player, water, rod);

        processor.process(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.FAILED_ATTEMPT));
        verify(pools, never()).untrack(any());
        PlayerFishEvent caught = new PlayerFishEvent(player, nativeCatch, hook, PlayerFishEvent.State.CAUGHT_FISH);
        try (var fishUtils = mockStatic(com.oheers.fish.FishUtils.class)) {
            processor.process(caught);
        }

        verify(processor).getCaughtItem(any(), any(), any());
        verify(nativeCatch).setItemStack(fish);
        assertFalse(caught.isCancelled());
        verify(packets, atLeastOnce()).stop(player.getUniqueId());
        verify(pools).untrack(hook.getUniqueId());
    }

    @Test void actualReelStillClosesTheCastBeforeAnyLaterCatch() {
        processor.process(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.REEL_IN));
        processor.process(new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH));

        verify(processor, never()).getCaughtItem(any(), any(), any());
    }

    @Test void missedBiteDoesNotAllowReplacingTheAdmittedRod() {
        processor.process(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.FAILED_ATTEMPT));
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(new ItemStack(Material.STONE));
        processor.process(new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH));

        verify(processor, never()).getCaughtItem(any(), any(), any());
    }

    @Test void cancelledNativeCatchNeverReachesTheRewardPathAfterAMissedBite() {
        processor.process(new PlayerFishEvent(player, null, hook, PlayerFishEvent.State.FAILED_ATTEMPT));
        PlayerFishEvent caught = new PlayerFishEvent(player, mock(Item.class), hook, PlayerFishEvent.State.CAUGHT_FISH);
        caught.setCancelled(true);
        processor.process(caught);

        verify(processor, never()).getCaughtItem(any(), any(), any());
    }

    private static final class PlainRod extends ItemStack {
        PlainRod() { super(Material.FISHING_ROD); }
        @Override public ItemMeta getItemMeta() { return null; }
        @Override public boolean isSimilar(ItemStack other) { return other instanceof PlainRod; }
    }
}
