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
    /**
     * Constructs DeviceResults with validated captured values.
     * Non-VALID snapshots require all three maps to be empty. Every history ID must have an element reading. Collections reject null entries and are defensively copied.
     * @param context non-null authoritative timestep
     * @param status non-null availability status
     * @param nodes non-null map of valid local keys to non-null node readings; at most 4096 entries
     * @param elements non-null map of valid local IDs to non-null element readings; at most 8192 entries
     * @param electricalState non-null history map; at most 8192 entries, with IDs present in elements
     * @param detail non-null explanation of at most 1024 characters
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
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
