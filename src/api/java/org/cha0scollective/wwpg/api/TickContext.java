package org.cha0scollective.wwpg.api;

/**
 * Authoritative server electrical timestep. gameTime is the dimension's game
 * tick; tickSeconds is the simulated duration, independent of wall-clock lag.
 * substeps is the PG schedule for this tick. Substep duration is tickSeconds/substeps.
 */
public record TickContext(long gameTime, double tickSeconds, int substeps) {
    /**
     * Constructs TickContext with validated captured values.
     * @param gameTime nonnegative dimension game tick
     * @param tickSeconds simulated seconds in [1e-12, 1] inclusive
     * @param substeps electrical steps per tick in [1, 4096] inclusive
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     */
    public TickContext {
        if (gameTime < 0) throw new IllegalArgumentException("gameTime must be nonnegative");
        ApiValidation.positive(tickSeconds, "tickSeconds");
        if (tickSeconds > 1) throw new IllegalArgumentException("tickSeconds must be at most 1 simulated second");
        if (substeps < 1 || substeps > 4096) throw new IllegalArgumentException("substeps must be 1..4096");
    }

    /** Duration of each accepted electrical substep in simulated seconds. */
    public double substepSeconds() { return tickSeconds / substeps; }
    /** Absolute simulated time at the start of this tick, for source waveforms. */
    public double startTimeSeconds() { return gameTime * tickSeconds; }
}
