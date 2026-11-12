package org.cha0scollective.wwpg.wiring;

import java.util.*;
import org.cha0scollective.wwpg.experimental.RestorationComponents;
import org.cha0scollective.wwpg.experimental.RestorationQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.cha0scollective.wwpg.experimental.RestorationComponents.join;

class RestorationComponentsTest {
    @Test void chainAndCouplerHoldDoesNotReachAnIndependentCircuit() {
        var a = new RestorationComponents.Node(); var b = new RestorationComponents.Node();
        var c = new RestorationComponents.Node(); var coupled = new RestorationComponents.Node();
        var independent = new RestorationComponents.Node();
        join(a,b); join(b,c); a.hold(); join(c,coupled);
        assertTrue(coupled.held()); assertFalse(independent.held());
    }
    @Test void newSnapshotDoesNotRetainRemovedConnections() {
        var oldA = new RestorationComponents.Node(); var oldB = new RestorationComponents.Node();
        join(oldA,oldB); oldA.hold(); assertTrue(oldB.held());
        var newA = new RestorationComponents.Node(); var newB = new RestorationComponents.Node();
        newA.hold(); assertFalse(newB.held());
    }
    @Test void randomGraphsMatchBreadthFirstReachability() {
        var random = new Random(20261109);
        for (int trial=0; trial<200; trial++) {
            int count=4+random.nextInt(80);
            var nodes=new RestorationComponents.Node[count]; var adjacency=new ArrayList<Set<Integer>>();
            var expected=new HashSet<Integer>(); var pending=new ArrayDeque<Integer>();
            for(int i=0;i<count;i++) {
                nodes[i]=new RestorationComponents.Node(); adjacency.add(new HashSet<>());
                if(random.nextInt(10)==0) { nodes[i].hold(); expected.add(i); pending.add(i); }
            }
            for(int i=0;i<count;i++) {
                int a=random.nextInt(count),b=random.nextInt(count);
                join(nodes[a],nodes[b]); adjacency.get(a).add(b); adjacency.get(b).add(a);
            }
            while(!pending.isEmpty()) for(int other:adjacency.get(pending.removeFirst()))
                if(expected.add(other))pending.addLast(other);
            for(int i=0;i<count;i++)assertEquals(expected.contains(i),nodes[i].held(),"trial="+trial+" node="+i);
        }
    }
    @Test void largeChainAndClosedLoopDoNotRecurse() {
        var first = new RestorationComponents.Node(); var last=first;
        for(int i=0;i<100000;i++){var next=new RestorationComponents.Node();join(last,next);last=next;}
        join(last,first); last.hold(); assertTrue(first.held());
    }
    @Test void queueMetadataVisitPreservesOrderAndPendingWork() {
        var queue=new RestorationQueue<Integer,String>();queue.add(1,2,"a");queue.add(3,4,"b");
        var seen=new ArrayList<String>();queue.visitEndpoints((node,part)->seen.add(part+node));
        assertEquals(List.of("a1","a2","b3","b4"),seen);assertEquals(2,queue.size());
        var drained=new ArrayList<String>();queue.drain(1,100,()->0,p->true,n->Set.of(),p->{drained.add(p);return false;});
        assertEquals(List.of("a"),drained);assertEquals(1,queue.size());
    }
}
