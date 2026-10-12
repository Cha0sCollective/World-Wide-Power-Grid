package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.NetworkGraph;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePort;
import java.util.*;

/** Strong-component traversal followed by PG's existing weak-line port creation. */
public final class IndexedIslandDiscovery {
    private IndexedIslandDiscovery() { }
    public static List<Set<INetworkElement>> partition(WorldNetworks world, ElectricalNetwork network,
            Set<TransmissionLine> weak, Set<INode> obsoletePorts) {
        return partition(world.globalGraph, network, WorldNetworks::canWeakCouple, weak, obsoletePorts);
    }
    public static List<Set<INetworkElement>> partition(NetworkGraph graph, ElectricalNetwork network,
            java.util.function.Predicate<TransmissionLine> weakLine, Set<TransmissionLine> weak, Set<INode> obsoletePorts) {
        var membership = new IdentityHashMap<IElectricNode, Integer>();
        var components = new ArrayList<Set<INetworkElement>>();
        var seeds = new ArrayDeque<IElectricNode>();
        for (var node : network.getNodes()) if (node instanceof IElectricNode electric) seeds.add(electric);
        seeds.addAll(network.getLeafs());
        var queue = new ArrayDeque<IElectricNode>();
        while (!seeds.isEmpty()) {
            var seed = seeds.removeFirst(); if (membership.containsKey(seed)) continue;
            int index = components.size();
            Set<INetworkElement> elements = new HashSet<>(); components.add(elements);
            membership.put(seed, index); queue.add(seed);
            while (!queue.isEmpty()) {
                var node = queue.removeFirst(); elements.add(node);
                for (var wire : graph.getWires(node)) {
                    if (wire instanceof TransmissionLine line && weakLine.test(line)) {
                        weak.add(line);
                        for (var adjacent : line.coupledNodes()) if (adjacent != null && !membership.containsKey(adjacent)) seeds.add(adjacent);
                        continue;
                    }
                    elements.add(wire);
                    for (var adjacent : wire.coupledNodes()) if (adjacent != null && membership.putIfAbsent(adjacent, index) == null) queue.add(adjacent);
                }
                for (var coupling : graph.getCouplings(node)) {
                    if (coupling instanceof TransmissionLinePort) { obsoletePorts.add(coupling); continue; }
                    elements.add(coupling);
                    for (var adjacent : coupling.coupledNodes()) if (adjacent != null && membership.putIfAbsent(adjacent, index) == null) queue.add(adjacent);
                }
            }
        }
        weak.removeIf(line -> {
            var a = membership.get(line.getNode1()); var b = membership.get(line.getNode2());
            if (a != null && a.equals(b)) { components.get(a).add(line); return true; }
            return false;
        });
        return components;
    }
    public static void run(WorldNetworks world, ElectricalNetwork network) {
        var weak = Collections.newSetFromMap(new IdentityHashMap<TransmissionLine, Boolean>());
        var ports = Collections.newSetFromMap(new IdentityHashMap<INode, Boolean>());
        var components = partition(world, network, weak, ports);
        ports.forEach(INode::remove);
        if (components.size() <= 1) return;
        for (var elements : components) world.newNetwork().fromElements(elements);
        for (var line : weak) { line.setNetwork(null); line.makePortPair(); }
        network.clear(); world.subnetworks.remove(network); network.cleanup();
    }
}
