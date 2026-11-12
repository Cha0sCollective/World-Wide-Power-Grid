package org.cha0scollective.wwpg.benchmark;

import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/** Sampled server-thread JNI work counts; output is outside tickServer timing. */
public final class NativeWork {
    private static final boolean CONFIGURED = Boolean.getBoolean("wwpg.benchmark.nativeWork");
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final ArrayList<Frame> frames = new ArrayList<>();
    private static Thread serverThread;
    private static BufferedWriter writer;
    private static long tick, entries, exits, rows, invalid;
    private static String phase;
    private static boolean selected, closed;

    public static final class Frame {
        Object owner;
        final Frame parent;
        final int maxIterations, initialCommands;
        final StringBuilder indices = new StringBuilder(), commands = new StringBuilder();
        long start, nanos;
        int iterationCalls, residualCalls, returnedRows;
        boolean returned;
        Frame(Object owner, int maxIterations, int initialCommands, Frame parent) {
            this.owner = owner;
            this.maxIterations = maxIterations;
            this.initialCommands = initialCommands;
            this.parent = parent;
        }
    }

    public static void beginTick() {
        if (!CONFIGURED) return;
        if (serverThread == null) serverThread = Thread.currentThread();
        if (serverThread != Thread.currentThread()) { invalid++; return; }
        if (!frames.isEmpty() || CURRENT.get() != null) invalid++;
        frames.clear();
        tick = Session.observationTick() + 1;
        phase = Session.observerPhase();
        selected = Metrics.enabled && (phase.equals("WARMUP") || phase.equals("STABLE"));
    }

    public static Frame begin(Object owner, int iterations, int commands) {
        if (!selected || Thread.currentThread() != serverThread) return null;
        var frame = new Frame(owner, iterations, commands, CURRENT.get());
        if (frame.parent != null || frames.size() >= 64) invalid++;
        frames.add(frame);
        CURRENT.set(frame);
        entries++;
        frame.start = System.nanoTime();
        return frame;
    }

    public static void iteration(Object owner, int iteration, int commands) {
        var frame = CURRENT.get();
        if (frame == null) return;
        if (frame.owner != owner || iteration < 0 || iteration >= frame.maxIterations || commands < 0) invalid++;
        if (frame.iterationCalls++ != 0) { frame.indices.append('|'); frame.commands.append('|'); }
        frame.indices.append(iteration);
        frame.commands.append(commands);
    }

    public static void residual(Object owner) {
        var frame = CURRENT.get();
        if (frame == null) return;
        if (frame.owner != owner) invalid++;
        frame.residualCalls++;
    }

    public static void end(Frame frame, boolean returned, ByteBuffer values) {
        if (frame == null) return;
        frame.nanos = System.nanoTime() - frame.start;
        if (CURRENT.get() != frame) invalid++;
        CURRENT.set(frame.parent);
        frame.returned = returned;
        frame.returnedRows = values == null ? 0 : values.capacity() / Double.BYTES;
        frame.owner = null;
        exits++;
    }

    public static void write(String actualPhase, long tickNanos, long actualTick) {
        if (!CONFIGURED) return;
        try {
            if (selected) {
                if (actualTick != tick || !actualPhase.equals(phase) || CURRENT.get() != null) invalid++;
                if (writer == null) {
                    Files.createDirectories(Path.of("benchmark"));
                    writer = Files.newBufferedWriter(Path.of("benchmark/native-work.csv"), StandardOpenOption.CREATE_NEW);
                    writer.write("tick,phase,step,tick_ns,max_iterations,jacobian_commands,iteration_calls,hook_indices,hook_commands,residual_calls,returned,rows,native_ns\n");
                }
                for (int i = 0; i < frames.size(); i++) {
                    var f = frames.get(i);
                    writer.write(tick + "," + phase + "," + (i + 1) + "," + tickNanos + "," + f.maxIterations
                        + "," + f.initialCommands + "," + f.iterationCalls + "," + f.indices + "," + f.commands
                        + "," + f.residualCalls + "," + f.returned + "," + f.returnedRows + "," + f.nanos + "\n");
                    rows++;
                }
                writer.flush();
            }
            frames.clear();
            selected = false;
            if (writer != null && Session.observerPhase().isEmpty()) close();
        } catch (Exception error) { throw new IllegalStateException("Cannot preserve native work observation", error); }
    }

    private static void close() throws Exception {
        if (closed) return;
        closed = true;
        if (writer != null) writer.close();
        Files.createDirectories(Path.of("benchmark"));
        Files.writeString(Path.of("benchmark/native-work-end.json"), "{\"schema\":1,\"entries\":" + entries
            + ",\"exits\":" + exits + ",\"rows\":" + rows + ",\"invalid\":" + invalid + "}\n", StandardOpenOption.CREATE_NEW);
    }

    static {
        if (CONFIGURED) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { close(); } catch (Exception error) { throw new IllegalStateException("Cannot close native observation", error); }
        }, "WWPG native work evidence"));
    }
}
