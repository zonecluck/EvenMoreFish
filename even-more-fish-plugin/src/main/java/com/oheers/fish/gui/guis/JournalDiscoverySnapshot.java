package com.oheers.fish.gui.guis;

import org.jdbi.v3.core.Jdbi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One bounded SELECT produces discovery and lore data without mutating EMF caches. */
record JournalDiscoverySnapshot(Map<Key, Entry> entries) {
    static final int MAX_CATALOG_KEYS = 2048;
    record Key(String rarity, String fish) { }
    record Entry(boolean caught, int quantity, String date, float shortest, float longest,
                 int serverQuantity, float serverShortest, float serverLongest, String discoverer) { }

    JournalDiscoverySnapshot { entries = Map.copyOf(entries); }

    boolean caught(String rarity, String fish) {
        Entry entry = entries.get(new Key(rarity, fish));
        return entry != null && entry.caught();
    }

    boolean caughtRarity(String rarity) {
        return entries.entrySet().stream().anyMatch(entry -> entry.getKey().rarity().equals(rarity) && entry.getValue().caught());
    }

    static JournalDiscoverySnapshot load(Jdbi database, String prefix, int userId, String rarity, Set<Key> scope, Set<String> catalogRarities) {
        if (database == null || prefix == null || !prefix.matches("[A-Za-z0-9_]{0,64}")
                || userId <= 0 || catalogRarities.isEmpty() || catalogRarities.size() > MAX_CATALOG_KEYS
                || (rarity != null && scope.isEmpty()) || scope.size() > MAX_CATALOG_KEYS) {
            throw new IllegalArgumentException("Journal discovery scope unavailable");
        }
        List<String> rarities = List.copyOf(catalogRarities);
        List<String> names = scope.stream().map(Key::fish).distinct().toList();
        String userTable = prefix + "user_fish_stats";
        String fishTable = prefix + "fish";
        String userColumns = "u.fish_rarity, u.fish_name, u.quantity AS user_quantity, u.first_catch_time AS user_date, "
                + "u.shortest_length AS user_shortest, u.longest_length AS user_longest, ";
        String noGlobal = "NULL AS server_quantity, NULL AS server_shortest, NULL AS server_longest, NULL AS discoverer";
        String sql;
        if (rarity == null) {
            sql = "SELECT " + userColumns + noGlobal + " FROM " + userTable
                    + " u WHERE u.user_id=:user AND u.fish_rarity IN (<rarities>) LIMIT :row_limit";
        } else {
            sql = "SELECT f.fish_rarity, f.fish_name, u.quantity AS user_quantity, u.first_catch_time AS user_date, "
                    + "u.shortest_length AS user_shortest, u.longest_length AS user_longest, "
                    + "f.total_caught AS server_quantity, f.shortest_length AS server_shortest, "
                    + "f.largest_fish AS server_longest, f.discoverer FROM " + fishTable + " f LEFT JOIN " + userTable
                    + " u ON u.user_id=:user AND u.fish_rarity=f.fish_rarity AND u.fish_name=f.fish_name "
                    + "WHERE f.fish_rarity=:rarity AND f.fish_name IN (<names>) UNION ALL SELECT "
                    + userColumns + noGlobal + " FROM " + userTable
                    + " u WHERE u.user_id=:user AND u.fish_rarity=:rarity AND u.fish_name IN (<names>) "
                    + "AND NOT EXISTS (SELECT 1 FROM " + fishTable
                    + " f WHERE f.fish_rarity=u.fish_rarity AND f.fish_name=u.fish_name) LIMIT :row_limit";
        }
        return database.withHandle(handle -> {
            var query = handle.createQuery(sql).bind("user", userId)
                    .bind("row_limit", MAX_CATALOG_KEYS + 1).setQueryTimeout(5);
            if (rarity == null) query.bindList("rarities", rarities);
            else query.bind("rarity", rarity).bindList("names", names);
            List<Map.Entry<Key, Entry>> rows = query.map((result, context) -> Map.entry(
                    new Key(result.getString("fish_rarity"), result.getString("fish_name")), read(result))).list();
            if (rows.size() > MAX_CATALOG_KEYS) throw new IllegalStateException("Journal discovery limit exceeded");
            Map<Key, Entry> entries = new HashMap<>();
            for (var row : rows) {
                if (rarity == null ? catalogRarities.contains(row.getKey().rarity()) : scope.contains(row.getKey())) {
                    entries.put(row.getKey(), row.getValue());
                }
            }
            return new JournalDiscoverySnapshot(entries);
        });
    }

    private static Entry read(ResultSet row) throws SQLException {
        String date = row.getString("user_date");
        // Read-only display projection; never invoke timestamp repair/migration mappers.
        date = date != null && date.matches("\\d{4}-\\d{2}-\\d{2}.*") ? date.substring(0, 10) : null;
        return new Entry(row.getObject("user_quantity") != null, row.getInt("user_quantity"), date,
                row.getFloat("user_shortest"), row.getFloat("user_longest"), row.getInt("server_quantity"),
                row.getFloat("server_shortest"), row.getFloat("server_longest"), row.getString("discoverer"));
    }
}
