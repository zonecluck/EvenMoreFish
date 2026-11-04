package com.oheers.fish.fishing.pool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Same-directory atomic replacement keeps a failed write from truncating the live YAML. */
final class FishingPoolStore {
    private FishingPoolStore() {}

    static void write(Path file, String yaml) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "fishing-pools-", ".tmp");
        try {
            Files.writeString(temporary, yaml, StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("Filesystem cannot atomically save fishing-pools.yml", unsupported);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
