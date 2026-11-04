package com.oheers.fish.database.data.manager;


import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.database.data.strategy.DataSavingStrategy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Function;
import java.util.function.Supplier;

public class DataManager<T> {
    private final Map<String, T> cache = new ConcurrentHashMap<>();
    /** Revision guard: a completed flush may only clear the version it actually wrote. */
    private final Map<String, Long> dirtyVersions = new HashMap<>();
    private final Object stateLock = new Object();
    private final ReentrantLock flushLock = new ReentrantLock();
    private long revisionClock;
    private final DataSavingStrategy<T> savingStrategy;
    private final Function<String, T> defaultLoader;
    private final ScheduledExecutorService flushScheduler;

    public DataManager(DataSavingStrategy<T> savingStrategy, Function<String, T> defaultLoader) {
        this(savingStrategy, defaultLoader, null, null);
    }

    public DataManager(
        DataSavingStrategy<T> savingStrategy,
        Function<String, T> defaultLoader,
        Long flushInterval,
        TimeUnit flushUnit
    ) {
        this.savingStrategy = savingStrategy;
        this.defaultLoader = defaultLoader;
        this.flushScheduler = createFlushScheduler(flushInterval, flushUnit);
    }

    public T get(String key, Function<String, T> loader) {
        T cachedValue = cache.get(key);
        if (cachedValue != null) {
            return cachedValue;
        }

        T loadedValue = loader.apply(key);
        if (loadedValue == null) {
            return null;
        }

        T previousValue = cache.putIfAbsent(key, loadedValue);
        return previousValue == null ? loadedValue : previousValue;
    }

    public T getOrCreate(String key, Function<String, T> loader, Supplier<T> fallbackValueSupplier) {
        T loadedValue = get(key, loader);
        if (loadedValue != null) {
            debug("Using loaded value.");
            return loadedValue;
        }

        T cachedValue = cache.get(key);
        if (cachedValue != null) {
            debug("Using cached value.");
            return cachedValue;
        }

        T fallbackValue = fallbackValueSupplier.get();
        if (fallbackValue == null) {
            return null;
        }
        long createVersion;
        synchronized (stateLock) {
            T existing = cache.putIfAbsent(key, fallbackValue);
            if (existing != null) {
                return existing;
            }
            createVersion = ++revisionClock;
            dirtyVersions.put(key, createVersion);
        }
        if (savingStrategy.writesThroughOnCreate()) {
            // Failed writes leave the revision dirty for a later retry.
            savingStrategy.save(fallbackValue);
            synchronized (stateLock) {
                dirtyVersions.remove(key, createVersion);
            }
        }
        debug("Using fallback value.");
        return fallbackValue;
    }

    public T get(String key) {
        if (defaultLoader == null) {
            throw new IllegalStateException("No default loader configured");
        }
        return get(key, defaultLoader);
    }

    public T peek(String key) {
        return cache.get(key);
    }

    public void cacheLoadedValue(String key, T data) {
        if (data != null) {
            cache.putIfAbsent(key, data);
        }
    }

    public void update(String key, T data) {
        long version;
        synchronized (stateLock) {
            cache.put(key, data);
            version = ++revisionClock;
            dirtyVersions.put(key, version);
        }
        if (savingStrategy.writesThrough()) {
            savingStrategy.save(data);
            synchronized (stateLock) {
                dirtyVersions.remove(key, version);
            }
        }
    }

    public void updateAll(Map<String, T> data) {
        Map<String, Long> versions = new HashMap<>();
        synchronized (stateLock) {
            for (Map.Entry<String, T> entry : data.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
                long version = ++revisionClock;
                dirtyVersions.put(entry.getKey(), version);
                versions.put(entry.getKey(), version);
            }
        }
        if (savingStrategy.writesThrough()) {
            savingStrategy.saveAll(data.values());
            synchronized (stateLock) {
                versions.forEach((key, version) -> dirtyVersions.remove(key, version));
            }
        }
    }

    public void flush() {
        flushDirty();
    }

    public void shutdown() {
        if (flushScheduler != null) {
            flushScheduler.shutdown();
        }
        // The lock serializes periodic/manual/shutdown flushes and avoids stale writes.
        flushDirty();
    }

    private void flushDirty() {
        flushLock.lock();
        try {
            Map<String, T> values = new LinkedHashMap<>();
            Map<String, Long> versions = new HashMap<>();
            synchronized (stateLock) {
                for (Map.Entry<String, Long> entry : dirtyVersions.entrySet()) {
                    T value = cache.get(entry.getKey());
                    if (value != null) {
                        values.put(entry.getKey(), value);
                        versions.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (values.isEmpty()) {
                return;
            }
            // Never mutate/clear dirty versions before the save succeeds.
            savingStrategy.saveAll(values.values());
            synchronized (stateLock) {
                versions.forEach((key, version) -> dirtyVersions.remove(key, version));
            }
        } finally {
            flushLock.unlock();
        }
    }

    private ScheduledExecutorService createFlushScheduler(Long flushInterval, TimeUnit flushUnit) {
        if (flushInterval == null || flushUnit == null || flushInterval <= 0) {
            return null;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("emf-data-flush-" + THREAD_COUNTER.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                flushDirty();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "EMF data flush failed; pending changes will be retried", exception);
            }
        }, flushInterval, flushInterval, flushUnit);
        return scheduler;
    }

    private void debug(String message) {
        try {
            EvenMoreFish plugin = EvenMoreFish.getInstance();
            plugin.debug(message);
        } catch (RuntimeException ignored) {
            // Unit tests and early bootstrap paths may not have an initialized plugin singleton.
        }
    }

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);
    private static final Logger LOGGER = Logger.getLogger(DataManager.class.getName());
}
