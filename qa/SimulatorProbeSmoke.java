import com.oheers.fish.fishing.simulator.engine.ProbeSession;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import java.util.UUID;

public class SimulatorProbeSmoke {
    public static void main(String[] args) {
        UUID player = UUID.randomUUID(), hook = UUID.randomUUID();
        ItemStack rod = new ItemStack(101);
        ProbeSession s = new ProbeSession(player, hook, "lake", 12, 100, EquipmentSlot.OFF_HAND, rod);
        rod.marker = 102;
        if (s.rodFingerprint().marker != 101) throw new AssertionError("rod not snapshotted");
        if (s.castHand() != EquipmentSlot.OFF_HAND) throw new AssertionError("offhand lost");
        if (s.revision() != 12 || !s.zoneId().equals("lake")) throw new AssertionError("session identity lost");
        if (s.state() != ProbeSession.State.WAITING || s.isClosed()) throw new AssertionError("initial state");
        if (!s.markBiteReady() || s.markBiteReady()) throw new AssertionError("single bite transition");
        if (!s.close() || s.close() || s.markBiteReady()) throw new AssertionError("double-close guard");
        if (s.state() != ProbeSession.State.CLOSED) throw new AssertionError("closed state");
        System.out.println("PASS: probe state, offhand, immutable snapshot, idempotent cleanup");
    }
}
