package com.oheers.fish.gui.guis;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.SqlLogger;
import org.jdbi.v3.core.statement.StatementContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JournalDiscoverySnapshotTest {
    @Test void oneReadOnlySelectLoadsCaughtAndGlobalOnlyEntriesWithoutCacheAssumptions() throws Exception {
        var path = Files.createTempFile("emf-journal-snapshot-", ".db");
        try {
            Jdbi database = Jdbi.create("jdbc:sqlite:" + path);
            database.useHandle(handle -> {
                handle.execute("CREATE TABLE emf_fish (fish_rarity TEXT, fish_name TEXT, total_caught INTEGER, shortest_length REAL, largest_fish REAL, discoverer TEXT)");
                handle.execute("CREATE TABLE emf_user_fish_stats (user_id INTEGER, fish_rarity TEXT, fish_name TEXT, quantity INTEGER, first_catch_time TEXT, shortest_length REAL, longest_length REAL)");
                handle.execute("INSERT INTO emf_fish VALUES ('rare','caught',7,1,9,'00000000-0000-0000-0000-000000000001'),('rare','global-only',3,2,5,NULL)");
                handle.execute("INSERT INTO emf_user_fish_stats VALUES (1,'rare','caught',2,'2026-10-17 00:00:00',3,4),(1,'rare','user-only',1,'2026-10-16T00:00:00',2,2),(2,'rare','other-player',1,NULL,1,1),(1,'empty','retired-fish',1,NULL,1,1)");
            });
            AtomicInteger statements = new AtomicInteger();
            database.setSqlLogger(new SqlLogger() {
                @Override public void logBeforeExecution(StatementContext context) {
                    statements.incrementAndGet();
                    assertTrue(context.getRenderedSql().stripLeading().startsWith("SELECT"));
                }
            });
            var scope = Set.of(key("caught"), key("global-only"), key("user-only"), key("never-caught"));
            var snapshot = JournalDiscoverySnapshot.load(database, "emf_", 1, "rare", scope, Set.of("rare"));
            assertEquals(1, statements.get());
            assertTrue(snapshot.caught("rare", "caught"));
            assertTrue(snapshot.caught("rare", "user-only"));
            assertFalse(snapshot.caught("rare", "global-only"));
            assertFalse(snapshot.caught("rare", "never-caught"));
            assertTrue(snapshot.caughtRarity("rare"));
            assertEquals(7, snapshot.entries().get(key("caught")).serverQuantity());
            assertEquals("2026-10-17", snapshot.entries().get(key("caught")).date());
            assertEquals("2026-10-16", snapshot.entries().get(key("user-only")).date());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.entries().clear());
            statements.set(0);
            var root = JournalDiscoverySnapshot.load(database, "emf_", 1, null, scope, Set.of("rare", "empty"));
            assertEquals(1, statements.get());
            assertTrue(root.caughtRarity("rare"));
            assertTrue(root.caughtRarity("empty"));
            assertFalse(root.caught("rare", "other-player"));
        } finally { Files.deleteIfExists(path); }
    }

    @Test void failedOrInvalidReadsCannotBecomeAnEmptySuccessfulSnapshot() {
        Jdbi invalid = Jdbi.create("jdbc:sqlite::memory:");
        assertThrows(RuntimeException.class, () -> JournalDiscoverySnapshot.load(invalid, "missing_", 1, "rare", Set.of(key("fish")), Set.of("rare")));
        assertThrows(IllegalArgumentException.class, () -> JournalDiscoverySnapshot.load(invalid, "emf_;DROP TABLE fish", 1, "rare", Set.of(key("fish")), Set.of("rare")));
        assertThrows(IllegalArgumentException.class, () -> JournalDiscoverySnapshot.load(invalid, "emf_", 0, "rare", Set.of(key("fish")), Set.of("rare")));
    }

    private static JournalDiscoverySnapshot.Key key(String name) { return new JournalDiscoverySnapshot.Key("rare", name); }
}
