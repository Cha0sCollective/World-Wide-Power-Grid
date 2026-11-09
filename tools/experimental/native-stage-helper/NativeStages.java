package org.cha0scollective.wwpg.benchmark;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;

/** Copies diagnostic counters only; the borrowed view never escapes this call. */
public final class NativeStages {
    private static final boolean CONFIGURED = Boolean.getBoolean("wwpg.benchmark.nativeStages");
    private static final long MAGIC = 0x5757504750524f31L;
    private static final long[] ABSENT = new long[0];
    private static final String[] STAGES = {"total", "residual_multiply", "residual_norm", "sparse_solve",
        "factorization", "triangular_solve", "permutation", "finite", "copy", "delta", "java_callback", "jacobian", "rhs"};
    private static native ByteBuffer snapshot(long pointer);

    public static long[] capture(long pointer) {
        if (!CONFIGURED) return ABSENT;
        if (NativeStages.class.getClassLoader() != NativeMNA.class.getClassLoader())
            throw new IllegalStateException("Native stage observer must share PowerGrid's native-library loader");
        ByteBuffer values = snapshot(pointer).order(ByteOrder.nativeOrder());
        int words = 4 + STAGES.length * 2;
        if (values.capacity() != words * Long.BYTES)
            throw new IllegalStateException("Unexpected native profile layout");
        long[] result = new long[words];
        for (int i = 0; i < result.length; i++) result[i] = values.getLong(i * Long.BYTES);
        if (result[0] != MAGIC || result[1] <= 0 || result[2] != STAGES.length || result[3] != 0)
            throw new IllegalStateException("Invalid native profile identity or reentrant sample");
        return result;
    }

    public static String header() {
        if (!CONFIGURED) return "";
        StringBuilder value = new StringBuilder(",profile_magic,profile_frequency,profile_stages,profile_invalid");
        for (String stage : STAGES) value.append(",c_").append(stage).append("_calls,c_").append(stage).append("_qpc");
        return value.toString();
    }

    public static String columns(long[] values) {
        if (!CONFIGURED) return "";
        if (values == null || values.length != 4 + STAGES.length * 2)
            throw new IllegalStateException("Missing native profile snapshot");
        StringBuilder text = new StringBuilder();
        for (long value : values) text.append(',').append(value);
        return text.toString();
    }
}
