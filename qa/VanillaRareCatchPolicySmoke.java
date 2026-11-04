import com.oheers.fish.effects.RareCatchPolicy;
import java.util.List;

/** Standalone checks of production policy; no Bukkit runtime or stub integration. */
public final class VanillaRareCatchPolicySmoke {
    private static int checks;

    private static void expect(boolean expected, boolean actual, String description) {
        checks++;
        if (expected != actual) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        expect(false, RareCatchPolicy.eligible(null, 9, 1, List.of(), List.of()), "null rarity");
        expect(false, RareCatchPolicy.eligible("CoMmOn", 9, 1, List.of("COMMON"), List.of()), "common exclusion wins");
        expect(false, RareCatchPolicy.eligible("Thuong", 9, 1, List.of("THUONG"), List.of("thuong")), "custom exclusion wins");
        expect(true, RareCatchPolicy.eligible("rare", 0, 100, List.of("RaRe"), List.of()), "case insensitive explicit include");
        expect(true, RareCatchPolicy.eligible("Ancient", 3, 100, List.of(), List.of()), "index threshold");
        expect(false, RareCatchPolicy.eligible("Uncommon", 2, 100, List.of(), List.of()), "index below threshold");
        expect(true, RareCatchPolicy.eligible("Ancient", 0, 30, List.of(), List.of()), "weight upper boundary");
        expect(false, RareCatchPolicy.eligible("Ancient", 0, Math.nextUp(30.0), List.of(), List.of()), "weight above boundary");
        expect(true, RareCatchPolicy.eligible("Ancient", 0, Double.MIN_VALUE, List.of(), List.of()), "positive weight");
        for (double weight : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            expect(false, RareCatchPolicy.eligible("Unknown", 0, weight, List.of(), List.of()), "nonpositive or nonfinite weight " + weight);
        }
        System.out.println("PASS: " + checks + " production RareCatchPolicy exclusion, inclusion, index and weight checks");
    }
}
