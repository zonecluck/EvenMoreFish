package com.oheers.fish.baits.manager;

import java.util.LinkedHashMap;
import java.util.Map;

/** Validate the whole owned blob before any rod metadata is changed. */
final class BaitQuantities {
    private BaitQuantities() { }
    static Map<String, Integer> parse(String blob) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        if (blob == null || blob.isEmpty()) return quantities;
        for (String entry : blob.split(",", -1)) {
            String[] parts = entry.split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || quantities.containsKey(parts[0]))
                throw new IllegalArgumentException("Invalid or duplicate bait entry");
            int quantity;
            if ("∞".equals(parts[1])) quantity = BaitNBTManager.UNLIMITED_BAIT;
            else {
                if (!parts[1].matches("[0-9]+")) throw new IllegalArgumentException("Invalid bait quantity");
                quantity = Integer.parseInt(parts[1]);
                if (quantity <= 0) throw new IllegalArgumentException("Bait quantity must be positive");
            }
            quantities.put(parts[0], quantity);
        }
        return quantities;
    }
    static int total(Map<String, Integer> quantities) {
        int total = 0;
        for (int quantity : quantities.values()) total = Math.addExact(total, quantity == -1 ? 1 : quantity);
        return total;
    }
}
