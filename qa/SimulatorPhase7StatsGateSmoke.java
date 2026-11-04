import com.oheers.fish.fishing.simulator.commit.SimulatorCatchEventGate;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.api.events.EMFFishCaughtEvent;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Identity-level regression against actual stats-gate class, using minimal Bukkit stubs. */
public final class SimulatorPhase7StatsGateSmoke {
    static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
    public static void main(String[] args){
        UUID id=UUID.randomUUID(), other=UUID.randomUUID();
        Player alice=()->id;
        Player bob=()->other;
        Fish golden=new Fish("GoldenCarp"), cod=new Fish("Cod");
        EMFFishCaughtEvent expected=new EMFFishCaughtEvent(golden,alice);
        EMFFishCaughtEvent unrelated=new EMFFishCaughtEvent(cod,alice);
        EMFFishCaughtEvent otherUser=new EMFFishCaughtEvent(golden,bob);
        check(!SimulatorCatchEventGate.deferInternalStats(expected),"no scope without simulator");
        boolean accepted=SimulatorCatchEventGate.invokeDeferred(alice,golden,()->{
            check(SimulatorCatchEventGate.deferInternalStats(expected),"same fish identity deferred");
            check(!SimulatorCatchEventGate.deferInternalStats(unrelated),"nested legacy event still records");
            check(!SimulatorCatchEventGate.deferInternalStats(otherUser),"other user still records");
            return SimulatorCatchEventGate.invokeDeferred(alice,cod,()->{
                check(SimulatorCatchEventGate.deferInternalStats(expected),"parent context remains");
                check(SimulatorCatchEventGate.deferInternalStats(unrelated),"nested context deferred");
                return true;
            });
        });
        check(accepted,"callback invoked");
        check(!SimulatorCatchEventGate.deferInternalStats(expected),"thread local cleaned");
        try {SimulatorCatchEventGate.invokeDeferred(alice,golden,()->{throw new IllegalStateException("cancelled");});}
        catch(IllegalStateException ignored){}
        check(!SimulatorCatchEventGate.deferInternalStats(expected),"exception removes context");
        System.out.println("PASS: Phase 7 stats deferral uses fish identity + UUID, nested event isolation and finally cleanup");
    }
}
