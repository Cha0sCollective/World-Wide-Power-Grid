package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.ICouplingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds the regions that need their own voltage reference within a transformer network. */
final class ReferenceIslands {
    static final class Region { boolean grounded; }

    private final Map<IElectricNode, IElectricNode> parents = new IdentityHashMap<>();
    private final Set<IElectricNode> grounded = Collections.newSetFromMap(new IdentityHashMap<>());

    static Map<IElectricNode, Region> partition(ElectricalNetwork network,
            Collection<AbstractElectricWire> wires, Set<AbstractElectricWire> numericalReferences,
            Map<TransformerCoupling, List<List<IElectricNode>>> windings) {
        var groups = new ReferenceIslands();
        for (var node : network.getNodes())
            if (node instanceof IElectricNode electric) groups.root(electric);
        for (var node : network.getLeafs()) groups.root(node);

        for (var wire : wires) {
            if (numericalReferences.contains(wire)) continue;
            groups.connect(network, wire.coupledNodes(), false);
            // PG omits earth from coupledNodes() for ordinary wires.
            var first = wire.getNode1();
            var second = wire.getNode2();
            if (wire.conductance() > 0 && (first == null || second == null)) {
                var connected = first == null ? second : first;
                if (connected != null && connected.getNetwork() == network) {
                    groups.root(connected);
                    groups.grounded.add(connected);
                }
            }
        }
        for (var node : network.getNodes()) {
            if (!(node instanceof ICouplingNode coupling)) continue;
            var separate = windings.get(coupling);
            if (separate != null) {
                for (var winding : separate) groups.connect(network, winding, false);
            } else if (coupling instanceof VoltageSourceCoupling source) {
                groups.connect(network, Arrays.asList(source.getPositive(), source.getNegative()), true);
            } else {
                groups.connect(network, coupling.coupledNodes(), true);
            }
        }

        var regions = new IdentityHashMap<IElectricNode, Region>();
        var roots = new IdentityHashMap<IElectricNode, Region>();
        for (var node : groups.parents.keySet())
            regions.put(node, roots.computeIfAbsent(groups.root(node), ignored -> new Region()));
        for (var node : groups.grounded) regions.get(node).grounded = true;
        return regions;
    }

    private void connect(ElectricalNetwork network, Collection<IElectricNode> nodes, boolean earth) {
        IElectricNode first = null;
        boolean hasEarth = false;
        for (var node : nodes) {
            if (node == null) {
                hasEarth = true;
            } else if (node.getNetwork() == network) {
                if (first == null) first = node;
                parents.put(root(node), root(first));
            }
        }
        if (earth && hasEarth && first != null) grounded.add(first);
    }

    private IElectricNode root(IElectricNode node) {
        parents.putIfAbsent(node, node);
        var result = node;
        while (parents.get(result) != result) result = parents.get(result);
        while (parents.get(node) != node) {
            var next = parents.get(node);
            parents.put(node, result);
            node = next;
        }
        return result;
    }
}
