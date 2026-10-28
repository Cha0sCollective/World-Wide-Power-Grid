package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.NetworkGraph;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import static org.junit.jupiter.api.Assertions.*;

class CatenaryReconnectionTest {
    // Exercise the real solver without the game-only series optimizer/config.
    private static final class Network extends ElectricalNetwork {
        Network(){super(false);}
        void populateGraph(NetworkGraph graph){
            for(var node:getNodes())if(node instanceof org.patryk3211.powergrid.electricity.sim.node.IElectricNode electric)graph.addNode(electric);
            for(var wire:wires)graph.connect(wire.getNode1(),wire.getNode2(),wire);
        }
    }
    @Test void continuousHandoverChangesTheExactCircuitWithoutReplacingMembershipOrWarmup() {
        var graph=new NetworkGraph();var network=new Network();
        var a=new FloatingNode();var junction=new FloatingNode();var b=new FloatingNode();var contact=new FloatingNode();var ground=new FloatingNode();
        network.addNodes(a,junction,b,contact,ground);
        network.addWire(new ElectricWire(.001,ground,null));
        var source=new LinearBranch(network,ground,a,ElectricalProperties.fromThevenin(1,24),false);
        var first=new LinearBranch(network,a,contact,ElectricalProperties.resistor(.5),false);
        var middle=new LinearBranch(network,contact,junction,ElectricalProperties.resistor(.5),false);
        var second=new LinearBranch(network,junction,b,ElectricalProperties.resistor(1),false);
        network.addWire(new ElectricWire(10,contact,ground));
        network.populateGraph(graph);
        network.calculate(16);assertTrue(network.isConverged());
        double before=Math.abs(contact.getVoltage()-ground.getVoltage());
        var oldFirst=graph.getFirstWire(a,contact);var oldSecond=graph.getFirstWire(junction,b);
        int nodes=network.size();
        first.reconnect(graph,a,junction);second.reconnect(graph,contact,b);
        first.update(ElectricalProperties.resistor(1));middle.update(ElectricalProperties.resistor(.25));second.update(ElectricalProperties.resistor(.75));
        network.prepare(16);network.singleTick();
        assertTrue(network.isConverged(),"A retained contact must not acquire a new-node warm-up rejection");
        assertEquals(nodes,network.size());assertSame(network,first.network());assertSame(network,second.network());
        assertNull(graph.getFirstWire(a,contact));assertNull(graph.getFirstWire(junction,b));
        assertSame(oldFirst,graph.getFirstWire(a,junction));assertSame(oldSecond,graph.getFirstWire(contact,b));
        assertEquals(24*10/12.25,Math.abs(contact.getVoltage()-ground.getVoltage()),1e-5);
        assertTrue(before>Math.abs(contact.getVoltage()-ground.getVoltage()));
        assertEquals(24/12.25,Math.abs(source.current()),1e-5);
        first.remove(graph);assertNull(graph.getFirstWire(a,junction));
    }
    @Test void crossNetworkEndpointsCannotBeSilentlyRetargeted() {
        var graph=new NetworkGraph();var network=new Network();
        var a=new FloatingNode();var b=new FloatingNode();network.addNodes(a,b);
        var branch=new LinearBranch(network,a,b,ElectricalProperties.resistor(5),false);
        network.populateGraph(graph);
        var foreign=new FloatingNode();var other=new Network();other.addNode(foreign);
        assertThrows(IllegalArgumentException.class,()->branch.reconnect(graph,a,foreign));
        assertNotNull(graph.getFirstWire(a,b));assertNull(graph.getFirstWire(a,foreign));
    }
}
