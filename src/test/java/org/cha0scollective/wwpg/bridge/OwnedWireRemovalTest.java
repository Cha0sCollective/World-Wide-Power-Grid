package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.*;
import org.patryk3211.powergrid.electricity.sim.node.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnedWireRemovalTest {
    @Test void detachedStampCannotLeaveAnEdgeWhenRetired() {
        var graph=new NetworkGraph();var network=new ElectricalNetwork(false);var a=new FloatingNode();var b=new FloatingNode();
        network.addNode(a);network.addNode(b);graph.addNode(a);graph.addNode(b);
        var stamp=new CurrentSourceWire(a,b,1);network.addWire(stamp);graph.connect(a,b,stamp);
        stamp.setNetwork(null);
        stamp.remove();
        assertTrue(graph.getWires(a).contains(stamp),"The regression must reproduce PG's detached-stamp no-op");
        OwnedWireRemoval.remove(stamp,graph);
        assertFalse(graph.getWires(a).contains(stamp));assertFalse(graph.getWires(b).contains(stamp));
        OwnedWireRemoval.remove(stamp,graph);
        assertEquals(2,network.getNodes().size());
    }
    @Test void detachedNumericalGroundDoesNotRemoveOtherGrounds() {
        var graph=new NetworkGraph();var network=new ElectricalNetwork(false);var a=new FloatingNode();network.addNode(a);graph.addNode(a);
        var old=new CurrentSourceWire(a,null,1000);var live=new CurrentSourceWire(a,null,1);
        for(var wire:new CurrentSourceWire[]{old,live}){network.addWire(wire);graph.connect(a,null,wire);}
        old.setNetwork(null);OwnedWireRemoval.remove(old,graph);
        assertFalse(graph.getWires(a).contains(old));assertTrue(graph.getWires(a).contains(live));assertSame(network,live.getNetwork());
    }
}
