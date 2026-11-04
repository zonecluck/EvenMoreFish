import com.oheers.fish.config.simulator.SimulatorSettings;
import com.oheers.fish.fishing.simulator.zones.ZoneSelection;
import java.util.List;

public class SimulatorPhase23Smoke {
    static SimulatorSettings.Zone z(String id, boolean enabled, boolean deny, int priority) {
        return new SimulatorSettings.Zone(id, enabled, deny, SimulatorSettings.ZoneType.CUBOID,
            "world", "", priority, 0, 40, 0, 10, 80, 10);
    }
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var west = z("west", true, false, 10);
        var east = z("east", true, false, 10);
        var higher = z("higher", true, false, 15);
        var blocked = z("blocked", true, true, -1000);
        var off = z("disabled", false, true, 1000);
        check(ZoneSelection.choose(List.of()).zone() == null, "no zone");
        check(ZoneSelection.choose(List.of(west, east)).zone() == east, "alphabetical tie");
        check(ZoneSelection.choose(List.of(west, higher, east)).zone() == higher, "higher priority");
        check(ZoneSelection.choose(List.of(west, blocked, higher)).denied(), "deny beats priority");
        check(!ZoneSelection.choose(List.of(west, off)).denied(), "disabled must not deny");
        check(west.containsCuboid(0, 40, 0), "min inclusive");
        check(west.containsCuboid(10.999, 80.999, 10.999), "max block inclusive");
        check(!west.containsCuboid(11, 80, 10), "next block excluded");
        check(west.isInWorld("WORLD"), "world case insensitive");
        var cfg = new SimulatorSettings(7, false, false, SimulatorSettings.UnavailablePolicy.LEGACY_EMF,
            new SimulatorSettings.Probe(false, 8, 60, 600, 1000000, 2, 35), List.of(west));
        check(cfg.zones().size() == 1 && cfg.revision() == 7, "immutable snapshot");
        try {
            cfg.zones().clear();
            throw new AssertionError("zones should be immutable");
        } catch (UnsupportedOperationException expected) { }
        System.out.println("PASS: 10 simulator config/zone ownership tests");
    }
}
