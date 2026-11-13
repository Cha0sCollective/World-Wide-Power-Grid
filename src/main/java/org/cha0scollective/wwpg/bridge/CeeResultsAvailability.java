package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.nodes.Node;

/** Shared availability contract for both native CEE result layouts. */
public interface CeeResultsAvailability {
    boolean allAvailable();
    boolean available(Node node);
    default boolean available(Node first, Node second) { return available(first) && available(second); }
}
