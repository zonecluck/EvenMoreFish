package com.oheers.fish.gui;

import java.util.Arrays;
import java.util.regex.Pattern;

/** Inventory backgrounds follow the resolved chest layout, never a guessed default. */
final class InventoryTitle {
    private static final Pattern ROW_GLYPH = Pattern.compile("(<glyph:row_?)[1-6]>");

    private InventoryTitle() {
    }

    static String forLayout(String title, String[] layout, Integer configuredRows) {
        int width = Arrays.stream(layout).mapToInt(String::length).max().orElse(0);
        // InventoryGui also supports dispenser/hopper layouts. Their geometry is not a chest.
        if (width <= 5 || width > 9 || layout.length == 0) {
            if (configuredRows != null) {
                throw new IllegalArgumentException("GUI rows requires a chest layout with 6–9 columns");
            }
            return title;
        }
        int rows = layout.length;
        if (configuredRows != null && (configuredRows < 1 || configuredRows > 6 || configuredRows != rows)) {
            throw new IllegalArgumentException("GUI rows must match the resolved layout (" + rows + " rows)");
        }
        if (title == null) {
            return null;
        }
        return ROW_GLYPH.matcher(title.replace("{rows}", Integer.toString(rows)))
            .replaceAll(match -> match.group(1) + rows + ">");
    }
}
