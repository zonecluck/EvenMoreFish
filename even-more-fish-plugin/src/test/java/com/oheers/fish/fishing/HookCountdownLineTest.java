package com.oheers.fish.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookCountdownLineTest {
    private static String text(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }

    @Test void waitingAndBiteShowSeparateLineWithTwentySegmentsAndNativeSeconds() {
        var line = new HookCountdownLine();
        String waiting = text(line.appendTo(Component.text("Đang chờ cá"),
            new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, 26)));
        assertEquals("Đang chờ cá\n" + "▏".repeat(20) + " 1.3s", waiting);
        String bite = text(line.appendTo(Component.text("Hãy giật cần"),
            new NativeHookTimers.Reading(NativeHookTimers.Phase.BITE, 17)));
        assertTrue(bite.startsWith("Hãy giật cần\n"));
        assertTrue(bite.endsWith(" 0.9s"));
    }

    @Test void phaseChangesPoolRestorationAndResetUseObservedNativeValues() {
        var line = new HookCountdownLine();
        Component header = Component.text("Đang chờ cá");
        line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, 100));
        assertTrue(text(line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, 50))).endsWith(" 2.5s"));
        assertTrue(text(line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, 120))).endsWith(" 6.0s"));
        assertTrue(text(line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.APPROACH, 26))).endsWith(" 1.3s"));
        line.reset();
        assertTrue(text(line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, 20))).endsWith(" 1.0s"));
    }

    @Test void unknownCountersOmitTheLineAndZeroAndHugeCountersRemainBounded() {
        var line = new HookCountdownLine();
        Component header = Component.text("Hãy giật cần");
        assertSame(header, line.appendTo(header, NativeHookTimers.Reading.unknown()));
        assertTrue(text(line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.BITE, 0))).endsWith(" 0.0s"));
        assertTrue(text(line.appendTo(header, new NativeHookTimers.Reading(NativeHookTimers.Phase.WAITING, Integer.MAX_VALUE))).length() < 60);
    }
}
