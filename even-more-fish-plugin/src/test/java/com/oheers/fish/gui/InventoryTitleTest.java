package com.oheers.fish.gui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class InventoryTitleTest {
    @Test
    void preservesAuthoredShiftAndTextForEveryChestHeight() {
        for (int rows = 1; rows <= 6; rows++) {
            String[] layout = new String[rows];
            Arrays.fill(layout, "abcdefghi");
            assertEquals("<white><shift:-8><glyph:row_" + rows + "><shift:-120><dark_gray>Menu",
                InventoryTitle.forLayout("<white><shift:-8><glyph:row_{rows}><shift:-120><dark_gray>Menu", layout, rows));
            assertEquals("<glyph:row_" + rows + "> Menu",
                InventoryTitle.forLayout("<glyph:row_6> Menu", layout, null));
            assertEquals("<glyph:row" + rows + "> Menu",
                InventoryTitle.forLayout("<glyph:row6> Menu", layout, null));
        }
    }

    @Test
    void rejectsDeclaredRowsThatDisagreeWithTheActualLayout() {
        assertThrows(IllegalArgumentException.class, () ->
            InventoryTitle.forLayout("Menu", new String[]{"abcdefghi", "abcdefghi", "abcdefghi"}, 6));
    }

    @Test
    void preservesNonChestTitlesAndMissingTitle() {
        assertEquals("<glyph:row_6> {rows}",
            InventoryTitle.forLayout("<glyph:row_6> {rows}", new String[]{"abc", "def", "ghi"}, null));
        assertNull(InventoryTitle.forLayout(null, new String[]{"abcdefghi"}, null));
    }
}
