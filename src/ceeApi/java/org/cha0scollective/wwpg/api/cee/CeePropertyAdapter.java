package org.cha0scollective.wwpg.api.cee;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;

/**
 * Exact-class adapter for a custom CEE electrical property. Registration happens
 * during RegisterCeePropertyAdaptersEvent and freezes before worlds begin ticking.
 *
 * <p>prepare captures current parameters without advancing history. A dynamic
 * adapter may update captured state in beforeSubstep and commit history only in
 * afterSubstep, which is invoked once after each accepted electrical substep.
 * All numerical callbacks must avoid mutable world, block, entity, and client
 * access. The owning CEE device remains responsible for gameplay, synchronization,
 * and native serialization. This API supports two-terminal Norton/ideal-voltage
 * models; custom coupled models use the general electrical API or native CEE
 * transformer properties.</p>
 *
 * <p>Dynamic adapters must implement checkpoint. WWPG keeps a whole-tick checkpoint
 * plus a checkpoint for each substep. A rejected solve or callback failure restores
 * the last committed tick before native gameplay can persist new state. Earlier
 * accepted substeps within a rejected tick are rolled back. A checkpoint that
 * throws violates this contract and causes explicit adapter isolation.</p>
 *
 * @param <T> exact registered electrical property class
 */
public interface CeePropertyAdapter<T extends ElectricalProperties> {
    /**
     * Capture an immutable CEE-native parameter snapshot. No state advancement.
     * The idealVoltageSource family must stay unchanged within a game tick;
     * topology can change only during the next safe preparation.
     */
    CeeElectricalParameters prepare(T source);
    /** Whether this adapter participates in the accepted electrical substep schedule. */
    default boolean dynamic() { return false; }
    /** Capture all callback-mutated numerical fields for reliable rollback. */
    default CeeStateCheckpoint checkpoint(T source) {
        if (dynamic()) throw new IllegalStateException("A dynamic CEE adapter must implement checkpoint");
        return CeeStateCheckpoint.NONE;
    }
    /** Prepare the next substep from the previous accepted voltage and captured state. */
    default void beforeSubstep(T source, CeeSubstepContext context) { }
    /** Commit storage/history once after an accepted substep; never during a nonlinear trial. */
    default void afterSubstep(T source, CeeSubstepResult result) { }
}
