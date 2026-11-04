package com.oheers.fish.fishing.simulator.commit;

import com.oheers.fish.api.events.EMFFishCaughtEvent;
import com.oheers.fish.fishing.items.Fish;
import org.bukkit.entity.Player;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Thread-local event identity gate for EMF's own catch-stat listener.
 * Simulator must fire the public cancellable event BEFORE item delivery for
 * addon compatibility. Internal DB history, however, must not be written
 * until AFTER successful delivery. Scope is only the identical Fish instance,
 * not other nested vanilla catches fired by another plugin on the same tick.
 */
public final class SimulatorCatchEventGate {
    private record Frame(UUID playerId, Fish fish, Frame previous) {}
    private static final ThreadLocal<Frame> ACTIVE=new ThreadLocal<>();
    private SimulatorCatchEventGate(){}

    public static boolean invokeDeferred(Player player,Fish fish,BooleanSupplier dispatch) {
        Objects.requireNonNull(player);Objects.requireNonNull(fish);Objects.requireNonNull(dispatch);
        Frame prior=ACTIVE.get();
        ACTIVE.set(new Frame(player.getUniqueId(),fish,prior));
        try {return dispatch.getAsBoolean();}
        finally {
            if(prior==null)ACTIVE.remove();
            else ACTIVE.set(prior);
        }
    }

    public static boolean deferInternalStats(EMFFishCaughtEvent event) {
        for(Frame frame=ACTIVE.get();frame!=null;frame=frame.previous()){
            if(frame.fish()==event.getFish() && frame.playerId().equals(event.getPlayer().getUniqueId()))return true;
        }
        return false;
    }
}
