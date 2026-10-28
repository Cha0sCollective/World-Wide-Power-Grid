package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;

/** A moving contact keeps its identity when it passes a shared overhead terminal. */
public interface MovingWireCuts {
    boolean wwpg$transferCut(WireSimulationState.WireCutHandle handle, AttachedNode node,
                            ConnectionEntry from, ConnectionEntry to, float point);
}
