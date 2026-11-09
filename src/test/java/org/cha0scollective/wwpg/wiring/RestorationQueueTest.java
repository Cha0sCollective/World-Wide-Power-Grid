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
            },p->false);
            var expected = new TreeMap<Integer,Boolean>(); var actual = new TreeMap<Integer,Boolean>();
            scalar.partNodeMap.values().forEach(parts->parts.forEach(p->expected.put(p.id,p.resolved)));
            batch.partNodeMap.values().forEach(parts->parts.forEach(p->actual.put(p.id,p.resolved)));
            assertEquals(expected,actual,"Different resolution in trial "+trial);
        }
    }
    @Test void priorityCannotStarveOldWorkAcrossOneItemSlices() {
        var queue = new RestorationQueue<Integer, String>();
        queue.add(0, 1, "old-a"); queue.add(2, 3, "old-b");
        var finished = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            queue.add(4, 5, "urgent-" + i);
            var slice = queue.drain(1, 100, () -> 0, p -> true, n -> Set.of(),
                    p -> { finished.add(p); return false; }, p -> p.startsWith("urgent"));
            assertEquals(1, slice.processed());
        }
        assertEquals("old-a", finished.get(3));
        assertEquals("old-b", finished.get(7));
        assertEquals(2, queue.size());
    }
    @Test void priorityIsReevaluatedWithoutLosingArrivalOrderOrNewWork() {
        var queue = new RestorationQueue<Integer, String>();
        queue.add(0, 1, "a"); queue.add(2, 3, "b"); queue.add(4, 5, "c");
        var finished = new ArrayList<String>();
        var urgent = new HashSet<>(Set.of("b"));
        queue.drain(1, 100, () -> 0, p -> true, n -> Set.of(), p -> {
            finished.add(p); queue.add(6, 7, "new"); return false;
        }, urgent::contains);
        urgent.clear(); urgent.add("c");
        queue.drain(1, 100, () -> 0, p -> true, n -> Set.of(), p -> { finished.add(p); return false; }, urgent::contains);
        queue.drain(10, 100, () -> 0, p -> true, n -> Set.of(), p -> { finished.add(p); return false; }, p -> false);
        assertEquals(List.of("b", "c", "a", "new"), finished);
        assertEquals(0, queue.size());
    }
    @Test void priorityPreservesUnprocessedWorkAfterFailureAndDiscardsRemovedOwners() {
        var queue = new RestorationQueue<Integer, String>();
        queue.add(0, 1, "old"); queue.add(2, 3, "fails"); queue.add(4, 5, "removed");
        assertThrows(IllegalStateException.class, () -> queue.drain(3, 100, () -> 0, p -> true,
                n -> { throw new IllegalStateException("endpoint callback failed"); }, p -> false, p -> p.equals("fails")));
        var finished = new ArrayList<String>();
        var result = queue.drain(3, 100, () -> 0, p -> !p.equals("removed"), n -> Set.of(),
                p -> { finished.add(p); return false; }, p -> p.equals("removed"));
        assertEquals(List.of("old"), finished);
        assertEquals(2, result.discarded()); assertEquals(0, queue.size());
    }
    @Test void priorityInspectionIsIncludedInCooperativeDeadline() {
        var queue = new RestorationQueue<Integer, String>();
        for (int i = 0; i < 3; i++) queue.add(i * 2, i * 2 + 1, "p" + i);
        var clock = new AtomicLong();
        var result = queue.drain(3, 10, clock::get, p -> true, n -> {
            clock.addAndGet(7); return Set.of();
        }, p -> { clock.addAndGet(3); return false; }, p -> { clock.addAndGet(4); return true; });
        assertEquals(1, result.processed()); assertEquals(29, result.elapsedNanos());
        assertEquals(17, result.longestEntryNanos()); assertEquals(2, queue.size());
    }
    @Test void priorityRetainsScalarFinalResolutionAcrossSavedGraphWaves() {
        var random = new Random(9112026);
        for (int trial = 0; trial < 250; trial++) {
            int count = 4 + random.nextInt(35); var valid = new boolean[count];
            for (int i = 0; i < count; i++) valid[i] = random.nextBoolean();
            var edges = new ArrayList<WireTreeTraversalTest.Edge>();
            for (int i = 0; i < count * 2; i++) edges.add(new WireTreeTraversalTest.Edge(
                    random.nextInt(count), random.nextInt(count), random.nextBoolean(), random.nextBoolean()));
            var fixture = new WireTreeTraversalTest.Fixture(valid, edges, false, false, false);
            var scalar = WireTreeTraversalTest.fixture(fixture); var batch = WireTreeTraversalTest.fixture(fixture);
            var scalarParts = new TreeMap<Integer, WireTreeTraversalTest.TransmissionLinePart>();
            var batchParts = new TreeMap<Integer, WireTreeTraversalTest.TransmissionLinePart>();
            scalar.partNodeMap.values().forEach(parts -> parts.forEach(p -> scalarParts.put(p.id, p)));
            batch.partNodeMap.values().forEach(parts -> parts.forEach(p -> batchParts.put(p.id, p)));
            var queue = new RestorationQueue<WireTreeTraversalTest.IWireEndpoint, WireTreeTraversalTest.TransmissionLinePart>();
            for (var part : scalarParts.values()) {
                scalar.iterative(part.a, new HashSet<>()); scalar.iterative(part.b, new HashSet<>());
                if (!part.resolved) scalar.makeTransmissionLine(part);
                var other = batchParts.get(part.id); queue.add(other.a, other.b, other);
            }
            while (queue.size() != 0) queue.drain(1 + random.nextInt(5), Long.MAX_VALUE, () -> 0,
                    p -> true, root -> { batch.iterative(root, new HashSet<>()); return Set.of(); },
                    part -> { if (part.resolved) return false; batch.makeTransmissionLine(part); return true; }, p -> !p.resolved);
            scalarParts.forEach((id, part) -> assertEquals(part.resolved, batchParts.get(id).resolved));
        }
    }
}
