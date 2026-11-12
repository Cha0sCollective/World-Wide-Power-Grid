package org.cha0scollective.wwpg.benchmark;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;

/** Sparse whole-section observation; absent from the gameplay artifact. */
public final class PreparationSections {
    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static BufferedWriter writer;
    private static long preparations, prepareReturns, stableStarts, stableReturns, rows, invalid;
    private static final class Frame {
        final Object owner;
        final long tick;
        final String phase;
        final boolean sampled, forced;
        final long[] sections = new long[7];
        long begin, stableBegin, last, stableNanos;
        int stage = -1, updates, accepted, stageMask;
        Frame(Object owner) {
            this.owner = owner;
            tick = Session.observationTick() + 1;
            phase = Session.observerPhase();
            forced = tick % 200 == 1;
            sampled = (phase.equals("WARMUP") || phase.equals("STABLE")) && (tick % 20 == 7 || forced);
            if (sampled) begin = System.nanoTime();
        }
    }

    public static void prepareBegin(Object owner) {
        preparations++;
        FRAMES.get().push(new Frame(owner));
    }
    public static void stableBegin(Object owner) {
        stableStarts++;
        var frame = FRAMES.get().peek();
        if (frame == null || frame.owner != owner) { invalid++; return; }
        frame.updates++;
        if (frame.stage != -1) { invalid++; return; }
        if (frame.sampled) {
            frame.stage = 0;
            frame.stageMask |= 1;
            frame.last = frame.stableBegin = System.nanoTime();
        }
    }
    public static void stage(int stage) {
        var frame = FRAMES.get().peek();
        if (frame == null) { invalid++; return; }
        if (!frame.sampled) return;
        if (stage < 1 || stage > 6 || frame.stage != stage - 1) { invalid++; return; }
        long now = System.nanoTime();
        frame.sections[frame.stage] += now - frame.last;
        frame.last = now;
        frame.stage = stage;
        frame.stageMask |= 1 << stage;
    }
    public static void stableEnd(boolean success) {
        stableReturns++;
        var frame = FRAMES.get().peek();
        if (frame == null) { invalid++; return; }
        if (success) frame.accepted++;
        if (!frame.sampled) return;
        if (frame.stage < 0 || success && frame.stage != 6) { invalid++; return; }
        long now = System.nanoTime();
        frame.sections[frame.stage] += now - frame.last;
        frame.stableNanos += now - frame.stableBegin;
        frame.stage = -1;
    }
    public static void prepareEnd(Object owner) {
        long now = System.nanoTime();
        prepareReturns++;
        var frame = FRAMES.get().poll();
        if (frame == null || frame.owner != owner) { invalid++; return; }
        if (frame.stage != -1) invalid++;
        if (!frame.sampled) return;
        try {
            open();
            writer.write(frame.tick + "," + frame.phase + "," + System.identityHashCode(owner) + "," + frame.forced
                    + "," + frame.updates + "," + frame.accepted + "," + frame.stageMask
                    + "," + (now - frame.begin) + "," + frame.stableNanos);
            for (long section : frame.sections) writer.write("," + section);
            writer.newLine();
            rows++;
            if (rows % 20 == 0) writer.flush();
        } catch (Exception error) { throw new IllegalStateException("Cannot record preparation sections", error); }
    }
    private static void open() throws Exception {
        if (writer != null) return;
        Files.createDirectories(Path.of("benchmark"));
        writer = Files.newBufferedWriter(Path.of("benchmark/preparation-sections.csv"), StandardOpenOption.CREATE_NEW);
        writer.write("tick,phase,owner,forced_edit,updates,accepted,stage_mask,prepare_ns,stable_ns,guards_ns,anchors_ns,descriptors_ns,validate_ns,rebind_ns,apply_ns,changing_ns\n");
    }
    private static void close() {
        try {
            if (writer != null) writer.close();
            Files.createDirectories(Path.of("benchmark"));
            Files.writeString(Path.of("benchmark/preparation-sections-end.json"),
                    "{\"schema\":1,\"preparations\":" + preparations + ",\"prepare_returns\":" + prepareReturns
                    + ",\"stable_starts\":" + stableStarts + ",\"stable_returns\":" + stableReturns
                    + ",\"rows\":" + rows + ",\"invalid\":" + invalid + "}\n", StandardOpenOption.CREATE_NEW);
        } catch (Exception error) { throw new IllegalStateException("Cannot close preparation observation", error); }
    }
    static { Runtime.getRuntime().addShutdownHook(new Thread(PreparationSections::close, "WWPG preparation evidence")); }
}
