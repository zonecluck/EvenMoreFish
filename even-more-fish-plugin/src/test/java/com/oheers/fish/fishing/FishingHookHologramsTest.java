package com.oheers.fish.fishing;

import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.oheers.fish.EvenMoreFish;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class FishingHookHologramsTest {
    private EvenMoreFish plugin;
    private TaskScheduler scheduler;
    private MyScheduledTask updateTask;
    private MockedStatic<EvenMoreFish> emf;
    private FishHook hook;
    private Player player;
    private World world;
    private TextDisplay display;
    private FishingHookHolograms holograms;

    @BeforeEach void setup() {
        plugin = mock(EvenMoreFish.class);
        scheduler = mock(TaskScheduler.class);
        updateTask = mock(MyScheduledTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
            .thenReturn(updateTask);
        emf = mockStatic(EvenMoreFish.class);
        emf.when(EvenMoreFish::getScheduler).thenReturn(scheduler);

        world = mock(World.class);
        player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        hook = mock(FishHook.class, withSettings().extraInterfaces(NativeHookTimersTest.LegacyCounters.class));
        when(hook.getUniqueId()).thenReturn(UUID.randomUUID());
        when(hook.isValid()).thenReturn(true);
        when(hook.getWorld()).thenReturn(world);
        when(hook.getShooter()).thenReturn(player);
        Location floatLocation = new Location(world, 12, 64, 23);
        when(hook.getLocation()).thenAnswer(invocation -> floatLocation.clone());

        display = mock(TextDisplay.class);
        when(display.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class), eq(TextDisplay.class), org.mockito.ArgumentMatchers.<Consumer<TextDisplay>>any()))
            .thenAnswer(invocation -> {
                Consumer<TextDisplay> initialize = invocation.getArgument(2);
                initialize.accept(display);
                return display;
            });
        holograms = new FishingHookHolograms(plugin);
    }

    @AfterEach void close() { emf.close(); }

    @Test void hookTextHasTransparentBackground() {
        holograms.showWaiting(hook);

        verify(display).setDefaultBackground(false);
        verify(display).setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        verify(display).setSeeThrough(false);
    }

    @Test void everyHookStateAndCountdownExplicitlyDisablesBold() {
        when(hook.getWaitTime()).thenReturn(100);
        holograms.showWaiting(hook);
        Runnable tick = scheduledTick();
        tick.run();
        tick.run();
        holograms.showBite(hook);
        tick.run();
        tick.run();
        holograms.showMissed(hook);
        tick.run();
        tick.run();

        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(display, atLeast(5)).text(text.capture());
        for (Component rendered : text.getAllValues()) {
            assertEquals(TextDecoration.State.FALSE, rendered.decoration(TextDecoration.BOLD));
            assertNoBoldChildren(rendered);
        }
    }

    private static void assertNoBoldChildren(Component component) {
        assertTrue(component.decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE);
        component.children().forEach(FishingHookHologramsTest::assertNoBoldChildren);
    }

    @Test void biteAnimationUpdatesHookDisplayWithoutSendingTitles() {
        holograms.showBite(hook);
        Runnable tick = scheduledTick();
        tick.run();
        tick.run();

        verify(display, atLeast(2)).text(any(Component.class));
        ArgumentCaptor<Location> locations = ArgumentCaptor.forClass(Location.class);
        verify(display, atLeast(2)).teleport(locations.capture());
        for (Location location : locations.getAllValues()) {
            assertEquals(12, location.getX());
            assertEquals(64.7, location.getY(), 0.0001);
            assertEquals(23, location.getZ());
        }
        verifyNoTitleControl();
    }

    @Test void missedTransitionDoesNotSendOrClearTitles() {
        holograms.showBite(hook);
        holograms.showMissed(hook);

        verify(display, atLeast(2)).text(any(Component.class));
        verifyNoTitleControl();
    }

    @Test void explicitRemovalDoesNotClearUnrelatedTitles() {
        holograms.showWaiting(hook);
        holograms.remove(hook);

        verify(display).remove();
        verify(updateTask).cancel();
        verifyNoTitleControl();
    }

    @Test void clearDoesNotClearUnrelatedTitles() {
        holograms.showWaiting(hook);
        holograms.clear();

        verify(display).remove();
        verify(updateTask).cancel();
        verifyNoTitleControl();
    }

    @Test void invalidHookRemovesDisplayAndStopsIdleUpdater() {
        holograms.showWaiting(hook);
        Runnable tick = scheduledTick();
        when(hook.isValid()).thenReturn(false);

        tick.run();
        tick.run();

        verify(display, times(1)).remove();
        verify(updateTask, times(1)).cancel();
        verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L));
    }

    @Test void waitingCountdownTracksNativeSecondsAndCachesUnchangedText() {
        when(hook.getWaitTime()).thenReturn(100);
        holograms.showWaiting(hook);
        Runnable tick = scheduledTick();
        tick.run();
        tick.run();
        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(display, times(2)).text(text.capture());
        String first = PlainTextComponentSerializer.plainText().serialize(text.getValue());
        assertTrue(first.contains("\n"));
        assertTrue(first.endsWith(" 5.0s"));

        tick.run();
        tick.run();
        verify(display, times(2)).text(any(Component.class));

        when(hook.getWaitTime()).thenReturn(50);
        tick.run();
        tick.run();
        verify(display, times(3)).text(text.capture());
        String second = PlainTextComponentSerializer.plainText().serialize(text.getValue());
        assertTrue(second.contains("\n"));
        assertTrue(second.endsWith(" 2.5s"));
        verify(hook, never()).setWaitTime(anyInt());
        verifyNoTitleControl();
    }

    @Test void offlineOwnerRemovesDisplayAndStopsUpdaterWithoutTouchingTitles() {
        holograms.showWaiting(hook);
        when(player.isOnline()).thenReturn(false);

        scheduledTick().run();

        verify(display).remove();
        verify(updateTask).cancel();
        verifyNoTitleControl();
    }

    @Test void ownerWorldChangeRemovesDisplayAndStopsUpdaterWithoutTouchingTitles() {
        holograms.showWaiting(hook);
        when(player.getWorld()).thenReturn(mock(World.class));

        scheduledTick().run();

        verify(display).remove();
        verify(updateTask).cancel();
        verifyNoTitleControl();
    }

    @Test void absentNativeBiteWindowResumesActualWaitingCountdownWithoutTitles() {
        when(((NativeHookTimersTest.LegacyCounters)hook).getHandle())
            .thenReturn(new NativeHookTimersTest.TimerFixture(0, 0));
        when(hook.getWaitTime()).thenReturn(100);
        holograms.showBite(hook);
        Runnable tick = scheduledTick();
        tick.run();
        tick.run();

        ArgumentCaptor<Component> text = ArgumentCaptor.forClass(Component.class);
        verify(display, atLeast(2)).text(text.capture());
        String current = PlainTextComponentSerializer.plainText().serialize(text.getValue());
        assertTrue(current.startsWith("Đang chờ cá\n"));
        assertTrue(current.endsWith(" 5.0s"));
        verifyNoTitleControl();
        verify(hook, never()).setWaitTime(anyInt());
    }

    private Runnable scheduledTick() {
        ArgumentCaptor<Runnable> scheduled = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskTimer(eq(plugin), scheduled.capture(), eq(1L), eq(1L));
        return scheduled.getValue();
    }

    private void verifyNoTitleControl() {
        verify(player, never()).showTitle(any(Title.class));
        verify(player, never()).sendTitlePart(any(), any());
        verify(player, never()).clearTitle();
    }
}
