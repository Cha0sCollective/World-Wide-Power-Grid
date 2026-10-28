package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.NetworkGraph;

/** Marks a CEE property stamp whose incremental conductance must remain exact. */
public final class PropertyCurrentSourceWire extends CurrentSourceWire {
    public PropertyCurrentSourceWire(IElectricNode first, IElectricNode second, double conductance) {
        super(first, second, conductance);
    }

    /** Used only for a proven continuous, same-network catenary handover. */
    void reconnect(NetworkGraph graph, IElectricNode first, IElectricNode second) {
        if (!assigned(network, first) || !assigned(network, second))
            throw new IllegalArgumentException("Catenary handover changed electrical ownership");
        // Membership, residual and power hooks stay intact. Rebuild the exact
        // matrix for the new endpoints; no source/node was added or removed.
        network.setDirty();
        graph.disconnect(node1, node2, this);
        node1 = first; node2 = second;
        graph.connect(first, second, this);
    }
    static boolean assigned(org.patryk3211.powergrid.electricity.sim.ElectricalNetwork owner, IElectricNode node) {
        return owner != null && node != null && node.getNetwork() == owner && node.getIndex() >= 0
                && node.getIndex() < owner.getNodes().size() && owner.getNodes().get(node.getIndex()) == node && !owner.isLeaf(node);
    }

    @Override
    public double internalPower() {
        if (network == null) return super.internalPower();
        double conductance = conductance();
        if (!Double.isFinite(conductance) || conductance <= 0) return super.internalPower();
        // This final stamp inherits PG's I = V * G current; read its differential once.
        double voltage = potentialDifference();
        return (voltage * conductance) * voltage;
    }
}
