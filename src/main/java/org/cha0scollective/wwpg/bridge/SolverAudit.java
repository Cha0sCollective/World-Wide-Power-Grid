package org.cha0scollective.wwpg.bridge;

import java.util.concurrent.atomic.AtomicLong;

/** Detects an upstream entry point escaping the single-solver boundary. */
public final class SolverAudit {
    private static final AtomicLong CEE_ATTEMPTS = new AtomicLong();
    private SolverAudit() {}
    public static long ceeAttempts() { return CEE_ATTEMPTS.get(); }
    public static void rejectCeeSolve() {
        CEE_ATTEMPTS.incrementAndGet();
        throw new IllegalStateException("WWPG: CEE solver was invoked outside the compatibility boundary");
    }
}
