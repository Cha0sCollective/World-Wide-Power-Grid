package org.cha0scollective.wwpg.wiring;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.cha0scollective.wwpg.experimental.RestorationQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestorationQueueTest {
    @Test void deadlineYieldsOnlyAfterBothRootsAndConnectionFinish() {
        var queue = new RestorationQueue<Integer, String>();
        queue.add(1, 2, "first"); queue.add(3, 4, "second");
        var clock = new AtomicLong(); var operations = new ArrayList<String>();
        var slice = queue.drain(32, 10, clock::get, p -> true, n -> {
            operations.add("root" + n); clock.addAndGet(7); return Set.of(n);
        }, p -> { operations.add(p); clock.addAndGet(3); return true; });
        assertEquals(List.of("root1", "root2", "first"), operations);
        assertEquals(1, queue.size()); assertEquals(17, slice.elapsedNanos());
        assertEquals(17, slice.longestEntryNanos()); assertEquals(1, slice.processed());
    }
    @Test void capAndFreshCoverageSurviveAppendingAndWorldChanges() {
        var queue = new RestorationQueue<Integer, String>();
        queue.add(1, 2, "a"); queue.add(1, 2, "b");
        var roots = new ArrayList<Integer>(); var parts = new ArrayList<String>();
        var first = queue.drain(1, 100, () -> 0, p -> true, n -> { roots.add(n); return Set.of(1,2); }, p -> { parts.add(p); return false; });
        assertEquals(1, first.covered()); assertEquals(1, queue.size());
        queue.add(3, 4, "c");
        // World changed between ticks: the previous resolved coverage is unusable.
        var second = queue.drain(1, 100, () -> 0, p -> true, n -> { roots.add(n); return Set.of(); }, p -> { parts.add(p); return false; });
        assertEquals(2, second.traversed()); assertEquals(List.of(1,1,2), roots);
        queue.drain(1, 100, () -> 0, p -> true, n -> Set.of(n), p -> { parts.add(p); return false; });
        assertEquals(List.of("a", "b", "c"), parts); assertEquals(0, queue.size());
    }
    @Test void removedOwnersCannotBeResurrectedAndFinishExpiresCoverage() {
        var queue = new RestorationQueue<Integer, String>();
        queue.add(1,2,"removed"); queue.add(1,2,"a"); queue.add(1,2,"b");
        var finished = new ArrayList<String>();
        var slice = queue.drain(3, 100, () -> 0, p -> !p.equals("removed"), n -> Set.of(1,2), p -> { finished.add(p); return true; });
        assertEquals(List.of("a","b"), finished);
        assertEquals(2, slice.discarded()); assertEquals(2, slice.traversed()); assertEquals(2, slice.covered());
    }
    @Test void alternatingCompletedRegionsAreWalkedOncePerDrain() {
        var queue = new RestorationQueue<Integer, Integer>();
        for (int i=0;i<1000;i++) queue.add(i%2*2, i%2*2+1, i);
        var visits = new ArrayList<Integer>();
        var first = queue.drain(500, Long.MAX_VALUE, ()->0, p->true, root->{
            visits.add(root); return root<2 ? Set.of(0,1) : Set.of(2,3);
        }, p->false, ()->0);
        assertEquals(2, first.traversed()); assertEquals(998, first.covered());
        var next = queue.drain(500, Long.MAX_VALUE, ()->0, p->true, root->{
            visits.add(root); return root<2 ? Set.of(0,1) : Set.of(2,3);
        }, p->false, ()->0);
        assertEquals(2, next.traversed(), "Coverage escaped its server-thread drain");
        assertEquals(List.of(0,2,0,2), visits);
    }
    @Test void migrationAndFailedConnectionAttemptsExpireEarlierRegions() {
        for (boolean finishMutation : List.of(false, true)) {
            var queue = new RestorationQueue<Integer, Integer>();
            queue.add(0,1,0); queue.add(2,3,1); queue.add(0,1,2);
            var revision = new AtomicLong(); var visits = new ArrayList<Integer>();
            queue.drain(32, Long.MAX_VALUE, ()->0, p->true, root->{
                visits.add(root);
                if (root==2 && !finishMutation) revision.incrementAndGet();
                return root<2 ? Set.of(0,1) : Set.of(2,3);
            }, p->p==1 && finishMutation, revision::get);
            assertEquals(List.of(0,2,0), visits, "A changed graph retained earlier coverage");
        }
    }
    @Test void boundedWavesMatchScalarTraversalOnMixedGraphs() {
        var random = new Random(82026);
        for (int trial = 0; trial < 200; trial++) {
            int count = 4 + random.nextInt(20); boolean[] valid = new boolean[count];
            for (int i=0;i<count;i++) valid[i] = random.nextBoolean();
            var edges = new ArrayList<WireTreeTraversalTest.Edge>();
            for (int i=0;i<count*2;i++) edges.add(new WireTreeTraversalTest.Edge(random.nextInt(count),random.nextInt(count),trial%3==0||random.nextBoolean(),random.nextBoolean()));
            var fixture = new WireTreeTraversalTest.Fixture(valid,edges,trial%5==0,false,trial%2==0);
            var scalar = WireTreeTraversalTest.fixture(fixture); var batch = WireTreeTraversalTest.fixture(fixture);
            var queue = new RestorationQueue<WireTreeTraversalTest.IWireEndpoint, Integer>();
            for (int i=0;i<count*2;i++) {
                var a = new WireTreeTraversalTest.Endpoint(random.nextInt(count),true);
                var b = new WireTreeTraversalTest.Endpoint(random.nextInt(count),true);
                scalar.iterative(a,new HashSet<>()); scalar.iterative(b,new HashSet<>());
                queue.add(a,b,i);
            }
            while (queue.size()>0) queue.drain(1+random.nextInt(4),Long.MAX_VALUE,()->0,p->true,root->{
                var seen = new HashSet<WireTreeTraversalTest.IWireEndpoint>(); batch.iterative(root,seen);
                for (var endpoint:seen) {
                    var parts=batch.partNodeMap.get(batch.globalExternalNodes.get(endpoint));
                    if (parts==null||parts.isEmpty()||parts.stream().anyMatch(p->!p.resolved)) return Set.of();
                }
                return seen;
            },p->false,()->batch.revision);
            var expected = new TreeMap<Integer,Boolean>(); var actual = new TreeMap<Integer,Boolean>();
            scalar.partNodeMap.values().forEach(parts->parts.forEach(p->expected.put(p.id,p.resolved)));
            batch.partNodeMap.values().forEach(parts->parts.forEach(p->actual.put(p.id,p.resolved)));
            assertEquals(expected,actual,"Different resolution in trial "+trial);
        }
    }
}
