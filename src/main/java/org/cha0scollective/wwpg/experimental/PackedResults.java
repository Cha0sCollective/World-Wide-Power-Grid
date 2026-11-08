package org.cha0scollective.wwpg.experimental;

/** Experimental primitive-only result kernels. Every run joins before returning.
 * Buffers belong to the calling thread; workers see no game objects or JNI views. */
public final class PackedResults {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.packedResults");
    public static final boolean VERIFY = Boolean.getBoolean("wwpg.experimental.packedResults.verify");
    public static final int WORKERS = Integer.getInteger("wwpg.experimental.packedResults.workers", 0);
    public static final int POWER = 0, CURRENT = 1, VOLTAGE = 2;
    private static Runner runner;
    private static final long[] items = new long[3];
    private static long verified;

    private PackedResults() { }

    /** Reusable structure of arrays. Only [0,count) is part of a dispatch. */
    public static final class Buffer {
        public final int operation;
        public double[] a, b, c, d, e, old, oldSquares, x, y, z;
        public byte[] kind;
        public Buffer(int operation) { this.operation = operation; ensure(0); }
        public void ensure(int count) {
            if (a != null && a.length >= count) return;
            int size = Math.max(count, 16);
            a = new double[size]; b = new double[size]; c = new double[size];
            d = new double[size]; e = new double[size]; old = new double[size];
            oldSquares = new double[size]; x = new double[size]; y = new double[size];
            z = new double[size]; kind = new byte[size];
        }
    }

    public static void run(Buffer buffer, int count) {
        if (!ENABLED) throw new IllegalStateException("Packed results are disabled");
        if (runner == null) runner = new Runner(WORKERS, Integer.getInteger("wwpg.experimental.packedResults.minimum", 1024));
        runner.run(buffer, count);
        items[buffer.operation] += count;
    }

    public static void same(double expected, double actual) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual))
            throw new IllegalStateException("Packed result differs from scalar result: " + expected + " / " + actual);
        verified++;
    }

    static void kernel(Buffer v, int first, int end) {
        switch (v.operation) {
            case POWER -> {
                for (int i = first; i < end; i++) {
                    if (v.kind[i] == 0) continue;
                    double power = (v.a[i] * v.c[i]) * v.a[i];
                    v.x[i] = power;
                    v.y[i] = v.old[i] + power / v.d[i];
                }
            }
            case CURRENT -> {
                for (int i = first; i < end; i++) {
                    double raw = (v.a[i] - v.b[i]) * v.c[i] + v.d[i] + v.e[i];
                    v.x[i] = raw;
                    double current = Double.isFinite(raw) ? raw : 0;
                    v.y[i] = v.old[i] + current;
                    v.z[i] = v.oldSquares[i] + current * current;
                }
            }
            case VOLTAGE -> {
                for (int i = first; i < end; i++)
                    v.x[i] = v.kind[i] == 0 ? v.a[i] : v.a[i] * (1 - v.c[i]) + v.b[i] * v.c[i];
            }
            default -> throw new IllegalArgumentException("Unknown packed operation");
        }
    }

    /** Fixed, bounded platform threads, one coarse range each, no per-item tasks.
     * The monitor is the publication barrier in both directions. An interrupted
     * caller drains every range before throwing; scratch output is never committed. */
    public static final class Runner implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final Thread[] threads;
        private final int minimum;
        private Buffer job;
        private int count, pending;
        private long generation;
        private boolean closed;
        private Throwable failure;
        private long dispatches, ranges;

        public Runner(int workers, int minimum) {
            if (workers != 0 && workers != 2 && workers != 4) throw new IllegalArgumentException("Use 0, 2 or 4 workers");
            this.minimum = minimum;
            threads = new Thread[workers];
            for (int i = 0; i < workers; i++) {
                final int index = i;
                threads[i] = new Thread(() -> work(index), "wwpg-packed-" + i);
                threads[i].setDaemon(true);
                threads[i].start();
            }
        }

        public synchronized void run(Buffer buffer, int size) {
            checkOwner();
            if (closed || pending != 0) throw new IllegalStateException("Packed runner unavailable");
            if (size < 0 || size > buffer.a.length) throw new IllegalArgumentException("Invalid packed size");
            if (threads.length == 0 || size < minimum) { kernel(buffer, 0, size); return; }
            job = buffer; count = size; failure = null; pending = threads.length;
            generation++; dispatches++; notifyAll();
            boolean interrupted = Thread.interrupted();
            while (pending != 0) {
                try { wait(); } catch (InterruptedException e) { interrupted = true; }
            }
            job = null;
            if (interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Packed results interrupted after draining workers");
            }
            if (failure != null) throw new IllegalStateException("Packed worker failed after draining workers", failure);
        }

        private void work(int index) {
            long seen = 0;
            while (true) {
                Buffer buffer; int size;
                synchronized (this) {
                    while (!closed && generation == seen) {
                        try { wait(); } catch (InterruptedException ignored) { /* shutdown uses closed */ }
                    }
                    if (closed) return;
                    seen = generation; buffer = job; size = count;
                }
                Throwable error = null;
                try { kernel(buffer, size * index / threads.length, size * (index + 1) / threads.length); }
                catch (Throwable t) { error = t; }
                synchronized (this) {
                    if (error != null && failure == null) failure = error;
                    ranges++; pending--; notifyAll();
                }
            }
        }

        private void checkOwner() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Packed runner has a different owner");
        }
        public long dispatches() { return dispatches; }
        public long ranges() { return ranges; }
        @Override public void close() {
            synchronized (this) { checkOwner(); closed = true; notifyAll(); }
            boolean interrupted = Thread.interrupted();
            for (var thread : threads) {
                while (thread.isAlive()) {
                    try { thread.join(); } catch (InterruptedException e) { interrupted = true; }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public static String stopAndReport() {
        long dispatches = runner == null ? 0 : runner.dispatches(), ranges = runner == null ? 0 : runner.ranges();
        if (runner != null) { runner.close(); runner = null; }
        String result = "WWPG experimental packed results: enabled=" + ENABLED + ", verify=" + VERIFY
                + ", workers=" + WORKERS + ", power=" + items[POWER] + ", currents=" + items[CURRENT]
                + ", voltages=" + items[VOLTAGE] + ", dispatches=" + dispatches + ", ranges=" + ranges
                + ", verified=" + verified;
        java.util.Arrays.fill(items, 0); verified = 0;
        return result;
    }
}
