package org.cha0scollective.wwpg.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable desired device circuit. All element and ground references use local
 * terminal/internal-node keys. WWPG compares identities and structural connections
 * with the previous description; parameter-only changes retain solver objects.
 * An empty description is valid for an electrically inactive device.
 */
public record CircuitDescription(List<Terminal> terminals, List<InternalNode> internalNodes,
                                 List<Element> elements, Set<String> grounds) {
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

    /** Description with no nodes or elements. */
    public static CircuitDescription empty() { return new CircuitDescription(List.of(), List.of(), List.of(), Set.of()); }

    private static void requireNode(Set<String> nodes, String key, String owner) {
        if (!nodes.contains(key)) throw new IllegalArgumentException(owner + " references undeclared node: " + key);
    }
}
