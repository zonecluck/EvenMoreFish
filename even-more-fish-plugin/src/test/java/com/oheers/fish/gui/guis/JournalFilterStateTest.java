package com.oheers.fish.gui.guis;

import com.oheers.fish.utils.sort.SortType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JournalFilterStateTest {
    @Test void literalSearchHandlesUnicodeCaseAndNeverTreatsQueryAsPattern() {
        var state = new JournalFilterState("  Cá Đỏ  ", SortType.INDEX, false);
        assertTrue(state.includes(true, true, true, "CÁ ĐỎ Biển"));
        assertFalse(new JournalFilterState(".*", SortType.INDEX, false).includes(true, true, false, "Cá Đỏ"));
        assertTrue(new JournalFilterState("<red>", SortType.INDEX, false).includes(true, true, false, "<red>"));
        assertThrows(IllegalArgumentException.class, () -> new JournalFilterState("cá\nđỏ", SortType.INDEX, false));
        assertThrows(IllegalArgumentException.class, () -> new JournalFilterState("cá\n", SortType.INDEX, false));
        assertDoesNotThrow(() -> new JournalFilterState("🐟".repeat(64), SortType.INDEX, false));
        assertThrows(IllegalArgumentException.class, () -> new JournalFilterState("🐟".repeat(65), SortType.INDEX, false));
    }

    @Test void serverPolicyCannotBeBypassedOrProbedThroughHiddenNames() {
        for (boolean shown : new boolean[]{false, true}) {
            for (boolean caught : new boolean[]{false, true}) {
                for (boolean hide : new boolean[]{false, true}) {
                    for (boolean caughtOnly : new boolean[]{false, true}) {
                        var empty = new JournalFilterState("", SortType.INDEX, caughtOnly);
                        assertEquals(shown && (!caughtOnly || caught), empty.includes(shown, caught, hide, "secret"));
                        var searched = new JournalFilterState("secret", SortType.INDEX, caughtOnly);
                        assertEquals(shown && (!caughtOnly || caught) && (!hide || caught),
                                searched.includes(shown, caught, hide, "secret"));
                    }
                }
            }
        }
        var searched = new JournalFilterState("secret", SortType.INDEX, false);
        assertEquals(searched.includes(true, false, true, "secret"), searched.includes(true, false, true, "different"));
    }

    @Test void nativeValuesRequireCompleteStrictTypesAndKnownSortIdentity() {
        var state = JournalFilterState.nativeValues(Map.of("query", "cá", "sort", "INDEX", "caught_only", "true"));
        assertEquals(SortType.INDEX, state.sort());
        assertTrue(state.caughtOnly());
        for (String invalid : new String[]{"", "yes", "1", "TRUE"}) {
            assertThrows(IllegalArgumentException.class, () -> JournalFilterState.nativeValues(
                    Map.of("query", "", "sort", "INDEX", "caught_only", invalid)));
        }
        assertThrows(IllegalArgumentException.class, () -> JournalFilterState.nativeValues(Map.of("query", "", "sort", "INDEX")));
        assertThrows(IllegalArgumentException.class, () -> JournalFilterState.nativeValues(Map.of("query", "", "sort", "unknown", "caught_only", "false")));
        Map<String,String> nullSort = new HashMap<>(); nullSort.put("query", ""); nullSort.put("sort", null); nullSort.put("caught_only", "false");
        assertThrows(IllegalArgumentException.class, () -> JournalFilterState.nativeValues(nullSort));
    }

    @Test void bedrockRequiresTheActualNormalizedStringAndIntegerPair() {
        Map<String, Object> values = new HashMap<>(Map.of("query", "cá", "sort", "Thứ tự danh mục", "sort.index", 2, "caught_only", true));
        assertEquals(new JournalFilterState("cá", SortType.INDEX, true), JournalFilterState.bedrockValues(values));
        for (Object invalid : new Object[]{2.0D, "2", -1, 3}) {
            values.put("sort.index", invalid);
            assertThrows(IllegalArgumentException.class, () -> JournalFilterState.bedrockValues(values));
        }
        values.put("sort.index", 2);
        values.put("sort", "INDEX");
        assertThrows(IllegalArgumentException.class, () -> JournalFilterState.bedrockValues(values));
        values.put("sort", "Thứ tự danh mục");
        values.remove("sort.index");
        assertThrows(IllegalArgumentException.class, () -> JournalFilterState.bedrockValues(values));
        values.put("sort.index", 2); values.put("extra", 1);
        assertThrows(IllegalArgumentException.class, () -> JournalFilterState.bedrockValues(values));
    }

    @Test void sortCyclingUsesTheExistingThreeSortsOnly() {
        var state = new JournalFilterState("", SortType.ALPHABETICAL, false);
        assertEquals(SortType.WEIGHT, state.nextSort().sort());
        assertEquals(SortType.INDEX, state.nextSort().nextSort().sort());
        assertEquals(state, state.nextSort().nextSort().nextSort());
    }
}
