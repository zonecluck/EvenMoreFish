package com.oheers.fish.fishing.simulator.visual;

import java.util.concurrent.atomic.AtomicInteger;

/** Uses a dedicated high positive entity-ID range; never reuses IDs during a JVM lifetime. */
public final class PacketEntityIdAllocator {
    private static final AtomicInteger NEXT = new AtomicInteger(1_400_000_000);
    private PacketEntityIdAllocator() {}
    public static int next() {
        int id = NEXT.getAndIncrement();
        if(id < 1_400_000_000 || id > 2_000_000_000) {
            throw new IllegalStateException("Virtual fish entity ID range exhausted; disable preview");
        }
        return id;
    }
}
