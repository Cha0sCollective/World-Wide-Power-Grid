package org.cha0scollective.wwpg.bridge;

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.NetworkGraph;

/** Retiring a compatibility stamp removes both its solver membership and its graph edge. */
public final class OwnedWireRemoval {
    private OwnedWireRemoval() { }
    public static void remove(AbstractElectricWire wire, NetworkGraph graph) {
        var recorded=wire.getNetwork();
        var first=wire.getNode1()==null?null:wire.getNode1().getNetwork();
        var second=wire.getNode2()==null?null:wire.getNode2().getNetwork();
        remove(recorded,wire);
        if(first!=recorded)remove(first,wire);
        if(second!=recorded&&second!=first)remove(second,wire);
        // PG clear/detach can leave a stamp without a recorded solver. Its ordinary remove() then does nothing.
        graph.disconnect(wire.getNode1(),wire.getNode2(),wire);
    }
    private static void remove(ElectricalNetwork network,AbstractElectricWire wire) {
        if(network==null)return;
        network.setDirty(); network.removeWire(wire);
    }
}
