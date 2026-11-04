package com.oheers.fish.baits.manager;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaitQuantitiesTest {
    @Test void acceptsPositiveAndInfiniteQuantitiesWithoutLosingOrder() {
        var quantities = BaitQuantities.parse("blood:5,rare:∞");
        assertEquals(5, quantities.get("blood")); assertEquals(-1, quantities.get("rare"));
        assertEquals(6, BaitQuantities.total(quantities));
    }
    @Test void rejectsWholeMalformedOrDuplicateBlob() {
        for (String blob : new String[]{"a:2,b:bad", "a:2,a:4", "a:0", "a:-1", "a:2,", ":2", "a:2:3", "a:2147483648"})
            assertThrows(IllegalArgumentException.class, () -> BaitQuantities.parse(blob), blob);
        assertThrows(ArithmeticException.class, () -> BaitQuantities.total(BaitQuantities.parse("a:2147483647,b:1")));
    }
}
