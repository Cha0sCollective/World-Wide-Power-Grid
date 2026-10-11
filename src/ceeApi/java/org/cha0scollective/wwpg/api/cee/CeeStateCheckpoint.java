package org.cha0scollective.wwpg.api.cee;

/**
 * Captured numerical state restoration. Dynamic adapters must snapshot every
 * source field their numerical callbacks can mutate, including committed history
 * and accepted-substep counters. The checkpoint must restore those captured
 * values without world access or further electrical advancement.
 */
@FunctionalInterface
public interface CeeStateCheckpoint {
    /** Checkpoint for a stateless linear adapter. */
    CeeStateCheckpoint NONE = () -> { };
    /** Restore exactly the captured numerical state; implementations must not throw. */
    void restore();
}
