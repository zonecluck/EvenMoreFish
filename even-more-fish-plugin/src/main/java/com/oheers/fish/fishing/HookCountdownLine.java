package com.oheers.fish.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Tracks the observed native phase maximum; does not advance or change a fishing timer. */
final class HookCountdownLine {
    private static final int SEGMENTS = 20;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private NativeHookTimers.Phase phase = NativeHookTimers.Phase.UNKNOWN;
    private int total;

    Component appendTo(Component header, NativeHookTimers.Reading reading) {
        if (reading.phase() == NativeHookTimers.Phase.UNKNOWN || reading.remainingTicks() < 0) return header;
        int remaining = reading.remainingTicks();
        if (phase != reading.phase()) { phase = reading.phase(); total = remaining; }
        total = Math.max(total, remaining);
        int filled = total == 0 ? 0 : (int)Math.min(SEGMENTS,
            ((long)remaining * SEGMENTS + total - 1) / total);
        long tenths = (remaining + 1L) / 2;
        String seconds = tenths / 10 + "." + tenths % 10 + "s";

        double ratio = total == 0 ? 0 : (double)remaining / total;
        String gradient = (reading.phase() == NativeHookTimers.Phase.BITE || ratio <= 0.25)
            ? "<gradient:#FF416C:#FF4B2B>"
            : (reading.phase() == NativeHookTimers.Phase.APPROACH || ratio <= 0.55)
                ? "<gradient:#FFE259:#FFA751>"
                : "<gradient:#00F5FF:#FFE259>";

        Component filledPart = filled > 0
            ? MINI_MESSAGE.deserialize(gradient + "▏".repeat(filled) + "</gradient>").decoration(TextDecoration.BOLD, false)
            : Component.empty().decoration(TextDecoration.BOLD, false);
        Component bar = Component.empty().decoration(TextDecoration.BOLD, false)
            .append(filledPart)
            .append(Component.text("▏".repeat(SEGMENTS - filled), NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
            .append(Component.text(" " + seconds, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
        return header.append(Component.newline()).append(bar);
    }

    void reset() { phase = NativeHookTimers.Phase.UNKNOWN; total = 0; }
}
