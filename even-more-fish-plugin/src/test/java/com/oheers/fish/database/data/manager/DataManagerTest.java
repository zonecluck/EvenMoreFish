package com.oheers.fish.database.data.manager;

import com.oheers.fish.database.data.strategy.BufferedSavingStrategy;
import com.oheers.fish.database.data.strategy.DataSavingStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataManagerTest {

    @Test
    void getDoesNotPersistLoadedValues() {
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicInteger saveAllCalls = new AtomicInteger();
        DataManager<String> dataManager = new DataManager<>(new RecordingImmediateStrategy(saveCalls, saveAllCalls), key -> "loaded");

        String value = dataManager.get("fish");

        assertEquals("loaded", value);
        assertEquals(0, saveCalls.get());
        assertEquals(0, saveAllCalls.get());
    }

    @Test
    void bufferedStrategyWritesThroughOnCreateAndBuffersUpdatesUntilFlush() {
        List<String> createWrites = new ArrayList<>();
        List<List<String>> batchWrites = new ArrayList<>();
        DataManager<String> dataManager = new DataManager<>(
            new BufferedSavingStrategy<>(createWrites::add, data -> batchWrites.add(new ArrayList<>(data)), true),
            key -> null
        );

        String created = dataManager.getOrCreate("fish", key -> null, () -> "first");
        dataManager.update("fish", "second");

        assertEquals("first", created);
        assertEquals(List.of("first"), createWrites);
        assertEquals(0, batchWrites.size());

        dataManager.flush();

        assertEquals(1, batchWrites.size());
        assertEquals(List.of("second"), batchWrites.getFirst());
    }

    @Test
    void shutdownFlushesBufferedValues() {
        List<List<String>> batchWrites = new ArrayList<>();
        DataManager<String> dataManager = new DataManager<>(
            new BufferedSavingStrategy<>(null, data -> batchWrites.add(new ArrayList<>(data)), false),
            key -> null
        );

        assertNull(dataManager.get("fish"));

        dataManager.update("fish", "value");
        dataManager.shutdown();

        assertEquals(1, batchWrites.size());
        assertEquals(List.of("value"), batchWrites.getFirst());
    }


    @Test
    void updateDuringSaveIsNotLostByFlushAcknowledgement() {
        AtomicInteger saves = new AtomicInteger();
        @SuppressWarnings("unchecked") DataManager<String>[] holder = new DataManager[1];
        holder[0] = new DataManager<>(new BufferedSavingStrategy<>(null, values -> {
            if (saves.getAndIncrement() == 0) {
                assertEquals(List.of("before"), new ArrayList<>(values));
                holder[0].update("fish", "after");
            }
        }, false), key -> null);

        holder[0].update("fish", "before");
        holder[0].flush();
        holder[0].flush();

        assertEquals(2, saves.get(), "second flush must persist the newer revision");
    }

    @Test
    void failedSaveKeepsDirtyChangesForRetry() {
        AtomicInteger saves = new AtomicInteger();
        DataManager<String> manager = new DataManager<>(
            new BufferedSavingStrategy<>(null, values -> {
                if (saves.getAndIncrement() == 0) throw new IllegalStateException("db disconnected");
            }, false), key -> null);

        manager.update("fish", "keep-me");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, manager::flush);
        manager.flush();
        assertEquals(2, saves.get());
    }

    private static final class RecordingImmediateStrategy implements DataSavingStrategy<String> {
        private final AtomicInteger saveCalls;
        private final AtomicInteger saveAllCalls;

        private RecordingImmediateStrategy(AtomicInteger saveCalls, AtomicInteger saveAllCalls) {
            this.saveCalls = saveCalls;
            this.saveAllCalls = saveAllCalls;
        }

        @Override
        public void save(String data) {
            saveCalls.incrementAndGet();
        }

        @Override
        public void saveAll(Collection<String> data) {
            saveAllCalls.incrementAndGet();
        }
    }
}
