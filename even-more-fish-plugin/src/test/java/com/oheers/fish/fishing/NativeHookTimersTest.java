package com.oheers.fish.fishing;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.FishHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NativeHookTimersTest {
    @Test void modernApproachCounterTakesPriorityOverWaitingAndNativeApproach() {
        FishHook hook = mock(FishHook.class, withSettings().extraInterfaces(ModernCounters.class));
        TimerFixture nativeHook = new TimerFixture(17, 35);
        when(((ModernCounters)hook).getTimeUntilBite()).thenReturn(12);
        when(((ModernCounters)hook).getHandle()).thenReturn(nativeHook);
        when(hook.getWaitTime()).thenReturn(100);

        assertEquals(new NativeHookTimers.Reading(NativeHookTimers.Phase.APPROACH, 12),
            NativeHookTimers.read(hook, false));
        verify(hook, never()).getWaitTime();
        verifyTimersUnchanged(hook, nativeHook, 17, 35);
    }

    @Test void exactLegacyApproachFieldTakesPriorityOverWaiting() {
        FishHook hook = mock(FishHook.class, withSettings().extraInterfaces(LegacyCounters.class));
        TimerFixture nativeHook = new TimerFixture(11, 24);
        when(((LegacyCounters)hook).getHandle()).thenReturn(nativeHook);
        when(hook.getWaitTime()).thenReturn(100);

        assertEquals(new NativeHookTimers.Reading(NativeHookTimers.Phase.APPROACH, 24),
            NativeHookTimers.read(hook, false));
        verify(hook, never()).getWaitTime();
        verifyTimersUnchanged(hook, nativeHook, 11, 24);
    }

    @Test void biteUsesActualNativeNibbleIncludingZeroWithoutWaitingFallback() {
        FishHook hook = mock(FishHook.class, withSettings().extraInterfaces(ModernCounters.class));
        TimerFixture nativeHook = new TimerFixture(17, 24);
        when(((ModernCounters)hook).getHandle()).thenReturn(nativeHook);
        when(((ModernCounters)hook).getTimeUntilBite()).thenReturn(12);
        when(hook.getWaitTime()).thenReturn(100);

        assertEquals(new NativeHookTimers.Reading(NativeHookTimers.Phase.BITE, 17),
            NativeHookTimers.read(hook, true));
        verifyTimersUnchanged(hook, nativeHook, 17, 24);
        nativeHook.nibble = 0;
        assertEquals(new NativeHookTimers.Reading(NativeHookTimers.Phase.BITE, 0),
            NativeHookTimers.read(hook, true));
        verify(hook, never()).getWaitTime();
        verify((ModernCounters)hook, never()).getTimeUntilBite();
        verifyTimersUnchanged(hook, nativeHook, 0, 24);
    }

    @Test void missingNativeMappingOmitsNumericBiteInsteadOfGuessingOtherIntegers() {
        FishHook hook = mock(FishHook.class, withSettings().extraInterfaces(UnmappedCounters.class));
        UnmappedFixture nativeHook = new UnmappedFixture();
        when(((UnmappedCounters)hook).getHandle()).thenReturn(nativeHook);
        when(hook.getWaitTime()).thenReturn(100);

        NativeHookTimers.Reading reading = NativeHookTimers.read(hook, true);
        assertEquals(NativeHookTimers.Reading.unknown(), reading);
        Component header = Component.text("Bite");
        assertSame(header, new HookCountdownLine().appendTo(header, reading));
        assertEquals(99, nativeHook.otherTimer);
        verify(hook, never()).getWaitTime();
        verify(hook, never()).setWaitTime(anyInt());
    }

    @Test void ordinarySdkHookReadsWaitingAndKeepsUnknownBiteUnknown() {
        FishHook hook = mock(FishHook.class);
        when(hook.getWaitTime()).thenReturn(50);

        assertEquals(new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, 50),
            NativeHookTimers.read(hook, false));
        assertEquals(NativeHookTimers.Reading.unknown(), NativeHookTimers.read(hook, true));
        when(hook.getWaitTime()).thenReturn(0);
        assertEquals(NativeHookTimers.Reading.unknown(), NativeHookTimers.read(hook, false));
        verify(hook, never()).setWaitTime(anyInt());
    }

    private static void verifyTimersUnchanged(FishHook hook, TimerFixture nativeHook, int nibble, int approach) {
        assertEquals(nibble, nativeHook.nibble);
        assertEquals(approach, nativeHook.timeUntilHooked);
        verify(hook, never()).setWaitTime(anyInt());
        verify(hook, never()).setMinWaitTime(anyInt());
        verify(hook, never()).setMaxWaitTime(anyInt());
    }

    public interface ModernCounters {
        int getTimeUntilBite();
        TimerFixture getHandle();
    }
    public interface LegacyCounters { TimerFixture getHandle(); }
    public interface UnmappedCounters { UnmappedFixture getHandle(); }

    public static final class TimerFixture {
        private int nibble;
        private int timeUntilHooked;
        TimerFixture(int nibble, int approach) { this.nibble = nibble; this.timeUntilHooked = approach; }
    }
    public static final class UnmappedFixture { private final int otherTimer = 99; }
}
