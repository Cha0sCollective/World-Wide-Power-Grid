package org.cha0scollective.wwpg.bridge;
import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.*;
import org.patryk3211.powergrid.electricity.sim.node.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndexedIslandDiscoveryTest {
    @Test void randomLoopsBranchesAndGroundsMatchIndependentConnectivity() {
        var random=new Random(15000);
        for(int fixture=0;fixture<80;fixture++){
            int count=25;var graph=new NetworkGraph();var network=new ElectricalNetwork(false);var nodes=new ArrayList<FloatingNode>();
            var neighbors=new ArrayList<Set<Integer>>();for(int i=0;i<count;i++){var node=new FloatingNode();nodes.add(node);graph.addNode(node);network.addNode(node);neighbors.add(new HashSet<>());}
            for(int i=0;i<40;i++){int first=random.nextInt(count),second=random.nextInt(count);if(first==second)continue;var wire=new ElectricWire(1,nodes.get(first),nodes.get(second));network.addWire(wire);graph.connect(nodes.get(first),nodes.get(second),wire);neighbors.get(first).add(second);neighbors.get(second).add(first);}
            for(int i=0;i<count;i+=7){var ground=new ElectricWire(100,nodes.get(i),null);network.addWire(ground);graph.connect(nodes.get(i),null,ground);}
            var actual=IndexedIslandDiscovery.partition(graph,network,line->false,new HashSet<>(),new HashSet<>());
            var membership=new IdentityHashMap<INetworkElement,Integer>();for(int i=0;i<actual.size();i++)for(var element:actual.get(i))membership.put(element,i);
            for(int start=0;start<count;start++){var reached=new HashSet<Integer>();var queue=new ArrayDeque<Integer>();reached.add(start);queue.add(start);while(!queue.isEmpty())for(int next:neighbors.get(queue.removeFirst()))if(reached.add(next))queue.add(next);
                for(int target=0;target<count;target++)assertEquals(reached.contains(target),membership.get(nodes.get(start)).equals(membership.get(nodes.get(target))),"fixture="+fixture+", "+start+"->"+target);
            }
        }
    }
    @Test void couplingKeepsOtherwiseSeparateBranchesTogether() {
        var graph=new NetworkGraph();var network=new ElectricalNetwork(false);var a=new FloatingNode();var b=new FloatingNode();var c=new FloatingNode();
        for(var node:List.of(a,b,c)){graph.addNode(node);network.addNode(node);}
        var coupling=new VoltageSourceCoupling(a,b,10);network.addNode(coupling);graph.couple(coupling);
        var partitions=IndexedIslandDiscovery.partition(graph,network,line->false,new HashSet<>(),new HashSet<>());
        assertEquals(2,partitions.size());assertTrue(partitions.stream().anyMatch(part->part.contains(a)&&part.contains(b)&&part.contains(coupling)&&!part.contains(c)));
    }
}
