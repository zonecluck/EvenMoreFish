package com.oheers.fish.gui.guis;

import com.oheers.fish.utils.sort.SortType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Temporary browsing choices; never persisted as fishing preferences. */
record JournalFilterState(String query, SortType sort, boolean caughtOnly) {
    static final int MAX_QUERY_CODEPOINTS = 64;
    static final List<String> SORT_LABELS = List.of("Tên A–Z", "Trọng số", "Thứ tự danh mục");
    private static final List<SortType> SORTS = List.of(SortType.ALPHABETICAL, SortType.WEIGHT, SortType.INDEX);

    JournalFilterState {
        if (query == null || sort == null) throw new IllegalArgumentException("Missing journal filter");
        if (query.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Tên tìm kiếm không chứa ký tự điều khiển.");
        }
        query = query.strip();
        if (query.codePointCount(0, query.length()) > MAX_QUERY_CODEPOINTS) {
            throw new IllegalArgumentException("Tên tìm kiếm tối đa 64 ký tự và không chứa ký tự điều khiển.");
        }
    }

    static JournalFilterState nativeValues(Map<String, String> values) {
        Map<?, ?> raw = values;
        if (values == null || !values.keySet().equals(Set.of("query", "sort", "caught_only"))
                || !(raw.get("query") instanceof String query)
                || !(raw.get("sort") instanceof String sort)
                || !(raw.get("caught_only") instanceof String caught)) {
            throw new IllegalArgumentException("Incomplete journal filter");
        }
        if (!"true".equals(caught) && !"false".equals(caught)) throw new IllegalArgumentException("Invalid caught filter");
        return new JournalFilterState(query, SortType.valueOf(sort), Boolean.parseBoolean(caught));
    }

    static JournalFilterState bedrockValues(Map<String, Object> values) {
        if (values == null || !values.keySet().equals(Set.of("query", "sort", "sort.index", "caught_only"))
                || !(values.get("query") instanceof String query)
                || !(values.get("sort") instanceof String label)
                || !(values.get("sort.index") instanceof Integer index)
                || index < 0 || index >= SORTS.size()
                || !SORT_LABELS.get(index).equals(label)
                || !(values.get("caught_only") instanceof Boolean caught)) {
            throw new IllegalArgumentException("Invalid journal form values");
        }
        return new JournalFilterState(query, SORTS.get(index), caught);
    }

    int sortIndex() { return SORTS.indexOf(sort); }
    JournalFilterState nextSort() { return new JournalFilterState(query, SORTS.get((sortIndex() + 1) % SORTS.size()), caughtOnly); }
    JournalFilterState withQuery(String value) { return new JournalFilterState(value, sort, caughtOnly); }
    JournalFilterState toggleCaught() { return new JournalFilterState(query, sort, !caughtOnly); }

    boolean includes(boolean shownInJournal, boolean discovered, boolean hideUndiscovered, String visibleName) {
        if (!shownInJournal || (caughtOnly && !discovered)) return false;
        if (query.isEmpty()) return true;
        // Hidden identities cannot be probed through search result counts.
        if (hideUndiscovered && !discovered) return false;
        return visibleName != null && visibleName.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
