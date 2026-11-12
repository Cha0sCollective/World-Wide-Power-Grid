package org.cha0scollective.wwpg.wiring;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.cha0scollective.wwpg.experimental.RestorationRoots;
import static org.junit.jupiter.api.Assertions.*;

class RestorationRootsTest {
    private static Map<Integer, Boolean> result(WireTreeTraversalTest model) {
        var result = new TreeMap<Integer, Boolean>();
        for (var parts : model.partNodeMap.values()) for (var part : parts) result.put(part.id, part.resolved);
        return result;
    }
    @Test void coveredRootsMatchScalarResolutionAcrossLoopsLeavesAndMigrations() {
        var random = new Random(11082026);
        long skipped = 0;
        for (int test = 0; test < 800; test++) {
            int count = 3 + random.nextInt(35);
            boolean[] valid = new boolean[count];
            for (int i = 0; i < count; i++) valid[i] = random.nextInt(5) != 0;
            var edges = new ArrayList<WireTreeTraversalTest.Edge>();
            for (int i = 0; i < count * 2; i++) edges.add(new WireTreeTraversalTest.Edge(
                    random.nextInt(count), random.nextInt(count), test % 4 == 0 || random.nextBoolean(), test % 2 == 0 || random.nextInt(6) != 0));
            var fixture = new WireTreeTraversalTest.Fixture(valid, edges, test % 7 == 0, false, test % 3 == 0);
            var scalar = WireTreeTraversalTest.fixture(fixture);
            var batch = WireTreeTraversalTest.fixture(fixture);
            var roots = new ArrayList<WireTreeTraversalTest.IWireEndpoint>();
            for (int i = 0; i < count * 3; i++) roots.add(new WireTreeTraversalTest.Endpoint(random.nextInt(count), true));
            for (var root : roots) scalar.iterative(root, new HashSet<>());
            var counts = RestorationRoots.drain(roots, root -> {
                var seen = new HashSet<WireTreeTraversalTest.IWireEndpoint>();
                batch.iterative(root, seen);
                for (var endpoint : seen) {
                    var parts = batch.partNodeMap.get(batch.globalExternalNodes.get(endpoint));
                    if (parts == null || parts.isEmpty() || parts.stream().anyMatch(p -> !p.resolved)) return Set.of();
                }
                return seen;
            });
            assertEquals(result(scalar), result(batch), "Resolution differs in case " + test);
            assertEquals(scalar.migrations.keySet(), batch.migrations.keySet());
            skipped += counts.covered();
        }
        assertTrue(skipped > 1000, "Insufficient coalescing coverage: " + skipped);
    }
    @Test void coverageDoesNotSurviveAnotherTraversalOrAnotherFlush() {
        var seen = new ArrayList<Integer>();
        var counts = RestorationRoots.drain(List.of(1, 2, 3, 1), root -> {
            seen.add(root);
            return root == 1 ? Set.of(1, 2) : Set.of(root);
        });
        assertEquals(List.of(1, 3, 1), seen);
        assertEquals(1, counts.covered());
        assertEquals(1, RestorationRoots.drain(List.of(2), root -> { seen.add(root); return Set.of(root); }).traversed());
        seen.clear();
        var after = new ArrayList<Integer>();
        RestorationRoots.drain(List.of(1,2,1,2), root -> { seen.add(root); return Set.of(1,2); }, index -> {
            after.add(index); return index == 1;
        });
        assertEquals(List.of(1,1), seen, "Connection creation must expire covered roots");
        assertEquals(List.of(0,1,2,3), after, "A covered root must still complete its native connection");
    }
}
