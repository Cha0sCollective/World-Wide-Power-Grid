package org.cha0scollective.wwpg.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable desired device circuit. All element and ground references use local
 * terminal/internal-node keys. WWPG compares identities and structural connections
 * with the previous description; parameter-only changes retain solver objects.
 * Its terminal set must equal the exposing {@link ElectricalDevice#terminals()}
 * snapshot, including key, native ID, position and hit radius. Ordering may differ.
 * An empty description is valid only when that geometry snapshot is also empty.
 * A disabled device that keeps its wiring points must keep its terminals.
 */
public record CircuitDescription(List<Terminal> terminals, List<InternalNode> internalNodes,
                                 List<Element> elements, Set<String> grounds) {
    /**
     * Constructs CircuitDescription with validated captured values.
     * There may be at most 4096 terminals plus internal nodes and 8192 elements. All endpoint, control, winding and ground keys must be declared. Collections reject null entries and are defensively copied.
     * @param terminals non-null immutable-copy input; unique local keys and native IDs; must match the provider geometry
     * @param internalNodes non-null nodes with keys unique across terminals and internal nodes
     * @param elements non-null elements with unique IDs and declared endpoint/control/winding nodes
     * @param grounds non-null set of declared local node keys
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
    public CircuitDescription {
        Objects.requireNonNull(terminals, "terminals");
        Objects.requireNonNull(internalNodes, "internalNodes");
        Objects.requireNonNull(elements, "elements");
        Objects.requireNonNull(grounds, "grounds");
        if ((long) terminals.size() + internalNodes.size() > WWPGApi.MAX_NODES)
            throw new IllegalArgumentException("device has more than " + WWPGApi.MAX_NODES + " nodes");
        if (elements.size() > WWPGApi.MAX_ELEMENTS)
            throw new IllegalArgumentException("device has more than " + WWPGApi.MAX_ELEMENTS + " elements");
        terminals = List.copyOf(terminals);
        internalNodes = List.copyOf(internalNodes);
        elements = List.copyOf(elements);
        grounds = Set.copyOf(grounds);
        var nodeKeys = new HashSet<String>();
        var terminalIds = new HashSet<Integer>();
        for (var terminal : terminals) {
            if (!nodeKeys.add(terminal.key())) throw new IllegalArgumentException("duplicate node key: " + terminal.key());
            if (!terminalIds.add(terminal.nativeId()))
                throw new IllegalArgumentException("duplicate native terminal ID " + terminal.nativeId() + " at " + terminal.key());
        }
        for (var node : internalNodes)
            if (!nodeKeys.add(node.key())) throw new IllegalArgumentException("duplicate node key: " + node.key());
        var elementIds = new HashSet<String>();
        for (var element : elements) {
            if (!elementIds.add(element.id())) throw new IllegalArgumentException("duplicate element ID: " + element.id());
            requireNode(nodeKeys, element.positiveNode(), "element " + element.id());
            requireNode(nodeKeys, element.negativeNode(), "element " + element.id());
            switch (element.model()) {
                case ElectricalModel.Transformer transformer -> {
                    requireNode(nodeKeys, transformer.secondaryPositiveNode(), "transformer " + element.id());
                    requireNode(nodeKeys, transformer.secondaryNegativeNode(), "transformer " + element.id());
                }
                case ElectricalModel.ControlledVoltageSource source -> {
                    requireNode(nodeKeys, source.controlPositiveNode(), "controlled voltage source " + element.id());
                    requireNode(nodeKeys, source.controlNegativeNode(), "controlled voltage source " + element.id());
                }
                case ElectricalModel.ControlledCurrentSource source -> {
                    requireNode(nodeKeys, source.controlPositiveNode(), "controlled current source " + element.id());
                    requireNode(nodeKeys, source.controlNegativeNode(), "controlled current source " + element.id());
                }
                default -> { }
            }
        }
        for (var ground : grounds) {
            ApiValidation.key(ground, "ground node");
            requireNode(nodeKeys, ground, "ground");
        }
    }

    /**
     * Description with no terminals, internal nodes, elements or grounds.
     * Use only when {@link ElectricalDevice#terminals()} also returns an empty list.
     * For a wired off state retain terminals and use an open {@link ElectricalModel.Switch}.
     * @return immutable description of a device exposing no electrical nodes
     */
    public static CircuitDescription empty() { return new CircuitDescription(List.of(), List.of(), List.of(), Set.of()); }

    private static void requireNode(Set<String> nodes, String key, String owner) {
        if (!nodes.contains(key)) throw new IllegalArgumentException(owner + " references undeclared node: " + key);
    }
}
