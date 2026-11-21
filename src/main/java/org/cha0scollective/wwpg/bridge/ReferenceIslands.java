package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.*;
import java.util.*;

/** Numerical zeroes follow conductive paths, not magnetic transformer coupling. */
final class ReferenceIslands {
    static final class Region { boolean grounded; }
    private final IdentityHashMap<IElectricNode, IElectricNode> parents = new IdentityHashMap<>();
    private final Set<IElectricNode> grounded = Collections.newSetFromMap(new IdentityHashMap<>());

    static Map<IElectricNode, Region> partition(ElectricalNetwork network,
            Collection<AbstractElectricWire> wires, Set<AbstractElectricWire> numericalReferences,
            Map<INode, List<List<IElectricNode>>> windings) {
        var groups = new ReferenceIslands();
        for (var node : network.getNodes()) if (node instanceof IElectricNode electric) groups.root(electric);
        for (var node : network.getLeafs()) groups.root(node);
        for (var wire : wires) {
            if (numericalReferences.contains(wire)) continue;
            groups.connect(network, wire.coupledNodes(), false);
            if (wire.conductance() > 0 && (wire.getNode1() == null || wire.getNode2() == null)) {
                var node = wire.getNode1() == null ? wire.getNode2() : wire.getNode1();
                if (node != null && node.getNetwork() == network) groups.grounded.add(node);
            }
        }
        for (var node : network.getNodes()) if (node instanceof ICouplingNode coupling) {
            var separate = windings.get(node);
            if (separate != null) for (var winding : separate) groups.connect(network, winding, false);
            else groups.connect(network, coupling.coupledNodes(), true);
            if (coupling instanceof VoltageSourceCoupling source)
                groups.connect(network, Arrays.asList(source.getPositive(), source.getNegative()), true);
        }
        var regions = new IdentityHashMap<IElectricNode, Region>();
        var roots = new IdentityHashMap<IElectricNode, Region>();
        for (var node : List.copyOf(groups.parents.keySet()))
            regions.put(node, roots.computeIfAbsent(groups.root(node), ignored -> new Region()));
        for (var node : groups.grounded) regions.get(node).grounded = true;
        return regions;
    }

    private void connect(ElectricalNetwork network, Collection<IElectricNode> nodes, boolean earth) {
        IElectricNode first = null;
        boolean hasEarth = false;
        for (var node : nodes) {
            if (node == null) { hasEarth = true; continue; }
            if (node.getNetwork() != network) continue;
            if (first == null) { first = node; root(node); }
            else parents.put(root(node), root(first));
        }
        if (earth && hasEarth && first != null) grounded.add(first);
    }

    private IElectricNode root(IElectricNode node) {
        parents.putIfAbsent(node, node);
        var result = node;
        while (parents.get(result) != result) result = parents.get(result);
        while (parents.get(node) != node) {
            var next = parents.get(node); parents.put(node, result); node = next;
        }
        return result;
    }
}
