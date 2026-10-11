package org.cha0scollective.wwpg.extension;

import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.Objects;

/** Exact public ground reference, with PG's native explicit-ground bookkeeping. */
final class ApiGround {
    private final IElectricNode node;
    private final VoltageSourceCoupling reference;
    private final PropertyCurrentSourceWire groundMarker;

    ApiGround(IElectricNode node) {
        this.node = Objects.requireNonNull(node, "ground node");
        var network = Objects.requireNonNull(node.getNetwork(), "ground node must already belong to a network");
        reference = new VoltageSourceCoupling(node, null, 0f);
        reference.setVoltage(0);
        // PG counts one-terminal wires as explicit ground references. The ideal
        // constraint makes this marker carry zero current and consume zero energy;
        // its only purpose is to prevent PG adding an automatic reference elsewhere.
        groundMarker = new PropertyCurrentSourceWire(node, null, 1000);
        network.setDirty();
        network.addWire(groundMarker);
        network.addNode(reference);
    }

    IElectricNode node() { return node; }
    ElectricalNetwork network() { return reference.getNetwork(); }

    void remove() {
        var network = network();
        if (network != null) network.setDirty();
        reference.remove();
        groundMarker.remove();
        if (network != null) network.setDirty();
    }
}
