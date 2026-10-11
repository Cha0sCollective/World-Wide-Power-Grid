package org.cha0scollective.wwpg.api;

/** Validity of a current electrical sample. Invalid samples contain no usable numbers. */
public enum MeasurementStatus {
    /** Accepted solution, including a legitimate zero reading. */
    VALID,
    /** Device, assembly, wire entities, or first electrical sample are not ready. */
    NOT_READY,
    /** Unsupported or invalid electrical description has been explicitly isolated. */
    ISOLATED,
    /** The affected network did not produce an accepted solution this tick. */
    SOLVE_FAILED
}
