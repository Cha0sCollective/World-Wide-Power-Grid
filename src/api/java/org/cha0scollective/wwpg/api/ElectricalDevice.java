package org.cha0scollective.wwpg.api;

import java.util.List;

/**
 * A stationary electrical device exposed through the WWPG block capability.
 *
 * <p>providerId is a namespaced registration ID and slotId identifies the single
 * device or whole assembly returned by this block's capability. Both remain stable
 * across restarts. This API does not discover multiple simultaneous capability
 * owners at one position; nested components use distinct local node and element IDs,
 * or their native CEE panel and Power Grid circuit-board integration.
 * terminals is a read-only geometry query available without preparing a circuit;
 * implementations return an immutable snapshot and never modify world or electrical
 * state from this method. Server preparation returns captured immutable inputs and
 * does not advance history. WWPG calls commit once per participating server tick,
 * including invalid results when a circuit cannot be solved.</p>
 *
 * <p>The add-on owns gameplay, synchronization, and serialization. Persist the
 * committed electricalState snapshots with the native block data. Restore those
 * values into initial model inputs when reconstructing elements. Initial values
 * are used only for creation/restoration; unchanged element IDs retain runtime
 * history through parameter edits. Commit must apply gameplay only to valid
 * results; invalid results are never previous readings or artificial zeroes.</p>
 */
public interface ElectricalDevice {
    /** Stable namespaced provider ID, such as example:machine. */
    String providerId();
    /** Stable identity slot for the capability's single device or whole assembly. */
    default String slotId() { return "main"; }
    /** Immutable external terminal geometry snapshot; no electrical advancement. */
    List<Terminal> terminals();
    /**
     * Capture circuit topology and parameters for the authoritative tick.
     * The returned terminal set must equal {@link #terminals()}, including each
     * key, native ID, position and hit radius; list ordering may differ.
     * Keep terminals when disabled and open a switch instead. A fully empty
     * description is permitted only when the geometry query is also empty.
     * @param context authoritative server timestep; preparation must not advance history
     * @return non-null immutable description with the same external terminal set
     */
    CircuitDescription prepare(TickContext context);
    /** Consume accepted results once; save committed history and update native gameplay. */
    void commit(DeviceResults results);
}
