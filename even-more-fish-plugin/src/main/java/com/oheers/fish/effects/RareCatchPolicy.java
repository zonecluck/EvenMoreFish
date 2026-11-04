package com.oheers.fish.effects;

import java.util.Collection;

/** Common exclusions win even when an admin enables a broad rarity heuristic. */
public final class RareCatchPolicy {
    private RareCatchPolicy() {}

    public static boolean eligible(String id, int index, double weight,
                                   Collection<String> included, Collection<String> excluded) {
        if (id == null || contains(excluded, id)) return false;
        return contains(included, id) || index >= 3 || (Double.isFinite(weight) && weight > 0 && weight <= 30);
    }

    private static boolean contains(Collection<String> ids, String id) {
        return ids.stream().anyMatch(id::equalsIgnoreCase);
    }
}
