package com.oheers.fish.fishing.simulator.visual;

import com.oheers.fish.fishing.simulator.population.FishAgent;
import com.oheers.fish.fishing.simulator.model.FishRenderSpec;
import java.util.UUID;

/** Version-specific transport. A failed spawn MAY have reached the client before metadata failed;
 * callers must attempt a compensating destroy even when spawn() returns false. */
public interface PacketTransport {
    boolean available();
    default boolean supportsCustom(UUID viewerId) { return false; }
    boolean spawn(UUID viewerId, FishAgent agent);
    /** Optional custom transport; legacy variants stay vanilla without touching PacketEvents. */
    default boolean spawn(UUID viewerId, FishAgent agent, FishRenderSpec render) {
        return render.isCustom() ? false : spawn(viewerId, agent);
    }

    boolean teleport(UUID viewerId, FishAgent agent);
    boolean destroy(UUID viewerId, int entityId);
    static PacketTransport unavailable() {
        return new PacketTransport() {
            public boolean available(){return false;}
            public boolean spawn(UUID v,FishAgent a){return false;}
            public boolean teleport(UUID v,FishAgent a){return false;}
            public boolean destroy(UUID v,int id){return false;}
        };
    }
}
