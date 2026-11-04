import com.oheers.fish.database.data.manager.DataManager;
import com.oheers.fish.database.data.strategy.BufferedSavingStrategy;
import com.oheers.fish.database.execute.DbExecutor;
import com.oheers.fish.fishing.items.RegionWeightMultiplier;
import com.oheers.fish.utils.WeightedRandom;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Independent JVM smoke, intentionally not a substitute for Gradle/Paper tests. */
public final class OfflineSmoke {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        AtomicInteger writes = new AtomicInteger();
        @SuppressWarnings("unchecked") DataManager<String>[] ref = new DataManager[1];
        ref[0] = new DataManager<>(new BufferedSavingStrategy<>(null, list -> {
            int n = writes.incrementAndGet();
            if (n == 1) {
                check(list.contains("old"), "first flush snapshot");
                ref[0].update("fish", "new");
            } else check(list.contains("new"), "new revision must be saved");
        }, false), key -> null);
        ref[0].update("fish", "old");
        ref[0].flush();
        ref[0].flush();
        ref[0].flush();
        check(writes.get() == 2, "revision should be retried exactly once");

        AtomicInteger attempts = new AtomicInteger();
        DataManager<String> failures = new DataManager<>(new BufferedSavingStrategy<>(null, list -> {
            if (attempts.getAndIncrement() == 0) throw new IllegalArgumentException("simulate DB failure");
        }, false), key -> null);
        failures.update("x", "saved");
        boolean rejected = false;
        try { failures.flush(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "first flush should fail");
        failures.flush();
        check(attempts.get() == 2, "failed flush must remain dirty");

        check(RegionWeightMultiplier.apply(10, 0.5) == 5, "half region boost");
        check(RegionWeightMultiplier.apply(10, 1.8) == 18, "decimal region boost");
        check(RegionWeightMultiplier.apply(10, 0) == 0, "disabled rarity weight");
        Random rng = new Random(12);
        int rare = 0;
        for (int i = 0; i < 20_000; ++i) {
            String fish = WeightedRandom.pick(List.of("Common", "Rare"),
                f -> RegionWeightMultiplier.apply(10, f.equals("Rare") ? .5 : 1.0), rng);
            if (fish.equals("Rare")) rare++;
        }
        check(rare > 6000 && rare < 7400, "weighted distribution: " + rare);

        DbExecutor executor = new DbExecutor(1, "smoke-db", 4);
        CountDownLatch latch = new CountDownLatch(1);
        List<Integer> ordered = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> a = executor.runAsync(() -> {
            try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            ordered.add(1);
        });
        CompletableFuture<Void> b = executor.runAsync(() -> ordered.add(2));
        latch.countDown();
        check(executor.awaitShutdown(10, TimeUnit.SECONDS), "executor flush on shutdown");
        a.join(); b.join();
        check(ordered.equals(List.of(1,2)), "DB writes should retain queue order");
        System.out.println("PASS: dirty revision, failed flush retry, fractional boost, RNG, DB executor ordering");
    }
}
