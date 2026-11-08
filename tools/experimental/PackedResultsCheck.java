import org.cha0scollective.wwpg.experimental.PackedResults;
import java.util.Random;

/** Standalone focused numeric/concurrency check, compiled with the actual runtime class. */
public final class PackedResultsCheck {
    private static long comparisons;
    private static long nanComparisons;
    private static void same(double a, double b) {
        // Java does not specify the sign/payload of an arithmetic NaN. These
        // probes deliberately include rejected inputs; finite values and signed
        // zero still require raw-bit identity, as do accepted in-game results.
        if (Double.isNaN(a) && Double.isNaN(b)) { nanComparisons++; return; }
        if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)) throw new AssertionError(a + " / " + b);
        comparisons++;
    }
    private static double value(Random random, int i) {
        return switch (i % 13) {
            case 0 -> -0.0; case 1 -> 0.0; case 2 -> Double.NaN;
            case 3 -> Double.POSITIVE_INFINITY; case 4 -> Double.NEGATIVE_INFINITY;
            case 5 -> Double.MIN_VALUE; case 6 -> Double.MAX_VALUE;
            default -> (random.nextDouble() - .5) * 1e4;
        };
    }
    public static void main(String[] args) throws Exception {
        for (int workers : new int[]{0, 2, 4}) try (var runner = new PackedResults.Runner(workers, 1)) {
            for (int operation = 0; operation < 3; operation++) {
                var buffer = new PackedResults.Buffer(operation);
                var random = new Random(140650);
                for (int n : new int[]{1, 1027, 14650, 3, 0, 20637}) {
                    buffer.ensure(n);
                    for (int i = 0; i < n; i++) {
                        buffer.a[i] = value(random, i); buffer.b[i] = value(random, i + 2);
                        buffer.c[i] = value(random, i + 7); buffer.d[i] = value(random, i + 4);
                        buffer.e[i] = value(random, i + 1); buffer.old[i] = value(random, i + 3);
                        buffer.oldSquares[i] = value(random, i + 5); buffer.kind[i] = 1;
                    }
                    runner.run(buffer, n);
                    for (int i = 0; i < n; i++) {
                        if (operation == PackedResults.POWER) {
                            double power = (buffer.a[i] * buffer.c[i]) * buffer.a[i];
                            same(power, buffer.x[i]); same(buffer.old[i] + power / buffer.d[i], buffer.y[i]);
                        } else if (operation == PackedResults.CURRENT) {
                            double current = (buffer.a[i] - buffer.b[i]) * buffer.c[i] + buffer.d[i] + buffer.e[i];
                            same(current, buffer.x[i]);
                            if (!Double.isFinite(current)) current = 0;
                            same(buffer.old[i] + current, buffer.y[i]);
                            same(buffer.oldSquares[i] + current * current, buffer.z[i]);
                        } else same(buffer.a[i] * (1 - buffer.c[i]) + buffer.b[i] * buffer.c[i], buffer.x[i]);
                    }
                }
            }
            var broken = new PackedResults.Buffer(PackedResults.VOLTAGE);
            broken.ensure(14650); broken.x = new double[1];
            try { runner.run(broken, 14650); throw new AssertionError("Worker failure disappeared"); }
            catch (IllegalStateException | ArrayIndexOutOfBoundsException expected) { }
            broken.x = new double[broken.a.length];
            if (workers > 0) {
                Thread.currentThread().interrupt();
                try { runner.run(broken, 14650); throw new AssertionError("Interruption disappeared"); }
                catch (IllegalStateException expected) {
                    if (!Thread.interrupted()) throw new AssertionError("Interrupt flag was lost");
                }
            }
            // A subsequent generation must be usable after both failed and drained runs.
            java.util.Arrays.fill(broken.a, 42); runner.run(broken, 14650);
            for (int i = 0; i < 14650; i++) same(42, broken.x[i]);
            var wrongOwner = new Thread(() -> {
                try { runner.run(broken, 1); throw new AssertionError("Cross-thread owner accepted"); }
                catch (IllegalStateException expected) { }
            });
            var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            wrongOwner.setUncaughtExceptionHandler((thread, failure) -> error.set(failure));
            wrongOwner.start(); wrongOwner.join();
            if (error.get() != null) throw new AssertionError(error.get());
            if (workers > 0 && runner.ranges() != runner.dispatches() * workers) throw new AssertionError("Undrained range");
            System.out.println("workers=" + workers + " dispatches=" + runner.dispatches() + " ranges=" + runner.ranges());
        }
        if (Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().startsWith("wwpg-packed-")))
            throw new AssertionError("Worker survived close");
        System.out.println("PASS bitwise comparisons=" + comparisons + "; rejected NaN classification=" + nanComparisons
                + "; failure, interrupt, reuse, owner and shutdown checks");
    }
}
