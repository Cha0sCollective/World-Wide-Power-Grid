package org.cha0scollective.wwpg.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable current-tick result snapshot. Invalid status requires empty maps:
 * unavailable readings cannot be confused with accepted zeroes or stale results.
 * Node and element maps use the local stable keys from the prepared description.
 */
public record DeviceResults(TickContext context, MeasurementStatus status,
                            Map<String, NodeResult> nodes, Map<String, ElementResult> elements,
                            Map<String, ElementState> electricalState, String detail) {
    public DeviceResults {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(electricalState, "electricalState");
        if (nodes.size() > WWPGApi.MAX_NODES || elements.size() > WWPGApi.MAX_ELEMENTS
                || electricalState.size() > WWPGApi.MAX_ELEMENTS)
            throw new IllegalArgumentException("result snapshot exceeds device circuit limits");
        nodes = Map.copyOf(nodes);
        elements = Map.copyOf(elements);
        electricalState = Map.copyOf(electricalState);
        ApiValidation.detail(detail);
        nodes.keySet().forEach(key -> ApiValidation.key(key, "result node key"));
        elements.keySet().forEach(key -> ApiValidation.key(key, "result element ID"));
        electricalState.keySet().forEach(key -> ApiValidation.key(key, "result state element ID"));
        if (status != MeasurementStatus.VALID && (!nodes.isEmpty() || !elements.isEmpty() || !electricalState.isEmpty()))
            throw new IllegalArgumentException("invalid electrical status " + status + " must not expose numeric results or new history");
        if (!elements.keySet().containsAll(electricalState.keySet()))
            throw new IllegalArgumentException("electricalState must refer to measured element IDs");
    }

    /** Current node reading, absent for an invalid snapshot or unknown node. */
    public Optional<NodeResult> node(String key) { return Optional.ofNullable(nodes.get(key)); }
    /** Current element reading, absent for an invalid snapshot or unknown element. */
    public Optional<ElementResult> element(String id) { return Optional.ofNullable(elements.get(id)); }
    /** Committed storage history for the specified element, when available. */
    public Optional<ElementState> state(String id) { return Optional.ofNullable(electricalState.get(id)); }

    /** Construct a diagnostic snapshot with no stale electrical data. */
    public static DeviceResults unavailable(TickContext context, MeasurementStatus status, String detail) {
        if (status == MeasurementStatus.VALID) throw new IllegalArgumentException("unavailable status must not be VALID");
        return new DeviceResults(context, status, Map.of(), Map.of(), Map.of(), detail);
    }
}
