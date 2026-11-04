package com.oheers.fish.fishing.simulator.presentation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Per-user minimal YAML persistence. Single bounded writer ensures writes to one UUID are ordered.
 * All disk I/O takes place on the owned worker, never in fishing/event callbacks.
 */
public final class VisualPreferenceStore {
    private final Path folder;
    private final ExecutorService worker;
    public VisualPreferenceStore(Path folder) {
        this.folder = folder;
        this.worker = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024), job -> {
                Thread thread = new Thread(job,"emf-simulator-preferences");
                thread.setDaemon(true);
                return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    }
    public CompletableFuture<UiProfile> load(UUID playerId, UiProfile fallback) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                Path file=path(playerId);
                if(!Files.isRegularFile(file)) return fallback;
                try {
                    if(Files.size(file)>2048) throw new IOException("Preference file is oversized");
                    for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
                        String trimmed=line.strip();
                        if(trimmed.startsWith("profile:")) {
                            return UiProfile.parse(trimmed.substring("profile:".length()).strip());
                        }
                    }
                    throw new IOException("Missing profile field");
                } catch(IOException | IllegalArgumentException exception) {
                    throw new IllegalStateException("Cannot load " + playerId + " preference: " + exception.getMessage(),exception);
                }
            },worker);
        } catch(RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
    public CompletableFuture<Void> save(UUID playerId, UiProfile profile) {
        try {
            return CompletableFuture.runAsync(() -> {
                Path destination=path(playerId), temporary=null;
                try {
                    Files.createDirectories(folder);
                    temporary=Files.createTempFile(folder,playerId.toString()+"-", ".tmp");
                    Files.writeString(temporary,"config-version: 1\nprofile: " + profile.name() + "\n",StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                    } catch(AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch(IOException exception) {
                    throw new IllegalStateException("Cannot write " + playerId + " visual preference",exception);
                } finally {
                    if(temporary!=null) {
                        try { Files.deleteIfExists(temporary); } catch(IOException ignored) { /* logged by failing write */ }
                    }
                }
            },worker);
        } catch(RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
    private Path path(UUID playerId) { return folder.resolve(playerId.toString()+".yml"); }
    /** Bounded graceful flush. A timeout remains a failure, never silently claims durable writes. */
    public boolean shutdown(Duration timeout) {
        worker.shutdown();
        try { return worker.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }
}
