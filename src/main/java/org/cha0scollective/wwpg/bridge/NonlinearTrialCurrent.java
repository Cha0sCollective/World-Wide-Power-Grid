package org.cha0scollective.wwpg.bridge;

/** Finite rejection merit for extreme Newton trials; accepted currents stay exact. */
final class NonlinearTrialCurrent {
    // Its squared norm remains finite, including many simultaneous branches.
    // This is a trial-evaluation bound, never a clipped published current.
    private static final double LIMIT=1e100;
    static double residual(double current) {
        if(Double.isNaN(current))throw new IllegalArgumentException("Invalid CEE nonlinear current");
        return Math.clamp(current,-LIMIT,LIMIT);
    }
    static double accepted(double current) {
        if(!Double.isFinite(current)||Math.abs(current)>LIMIT)
            throw new IllegalArgumentException("CEE nonlinear solve retained an extreme trial current");
        return current;
    }
}
