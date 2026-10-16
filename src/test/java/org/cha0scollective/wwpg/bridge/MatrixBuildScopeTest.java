package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MatrixBuildScopeTest {
    private record Entry(int row, int column, double value) { }
    private static final class Solver {
        final List<Entry> writes = new ArrayList<>();
        final IMNA mna = (IMNA) Proxy.newProxyInstance(IMNA.class.getClassLoader(), new Class<?>[]{IMNA.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("jacobianAdd")) throw new AssertionError(method.getName());
                    writes.add(new Entry((int) args[0], (int) args[1], (double) args[2])); return null;
                });
    }
    @Test void unrelatedSolverWritesAndFinishesCannotAcquireAnotherBuild() {
        var owner = new Solver(); var other = new Solver();
        try (var scope = MatrixBuildScope.begin(owner.mna)) {
            assertTrue(MatrixBuildScope.nativeAdd(owner.mna, 0, 0, 3));
            assertFalse(MatrixBuildScope.nativeAdd(other.mna, 20, 20, 7));
            MatrixBuildScope.flush(other.mna);
            assertTrue(owner.writes.isEmpty()); assertTrue(other.writes.isEmpty());
            MatrixBuildScope.flush(owner.mna);
        }
        assertEquals(List.of(new Entry(0, 0, 3)), owner.writes);
        assertTrue(other.writes.isEmpty());
        assertFalse(MatrixBuildScope.nativeAdd(owner.mna, 0, 0, 1));
    }
    @Test void nestedBuildFlushRestoresItsParentWithoutCollectingForeignWrites() {
        var first = new Solver(); var second = new Solver();
        try (var outer = MatrixBuildScope.begin(first.mna)) {
            assertTrue(MatrixBuildScope.nativeAdd(first.mna, 0, 0, 1));
            try (var inner = MatrixBuildScope.begin(second.mna)) {
                assertFalse(MatrixBuildScope.nativeAdd(first.mna, 0, 0, 99));
                assertTrue(MatrixBuildScope.nativeAdd(second.mna, 1, 1, 2));
                MatrixBuildScope.flush(second.mna);
                assertFalse(MatrixBuildScope.nativeAdd(second.mna, 1, 1, 99));
            }
            assertTrue(MatrixBuildScope.nativeAdd(first.mna, 0, 0, 3));
            MatrixBuildScope.flush(first.mna);
        }
        assertEquals(List.of(new Entry(0, 0, 4)), first.writes);
        assertEquals(List.of(new Entry(1, 1, 2)), second.writes);
    }
    @Test void exceptionDiscardsOnlyItsOwnBuildAndLeavesNoActiveScope() {
        var owner = new Solver(); var innerOwner = new Solver();
        try (var outer = MatrixBuildScope.begin(owner.mna)) {
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = MatrixBuildScope.begin(innerOwner.mna)) {
                    MatrixBuildScope.nativeAdd(innerOwner.mna, 0, 0, 9);
                    throw new IllegalStateException("intentional QA failure");
                }
            });
            assertTrue(MatrixBuildScope.nativeAdd(owner.mna, 0, 0, 2));
            MatrixBuildScope.flush(owner.mna);
        }
        assertTrue(innerOwner.writes.isEmpty());
        assertEquals(List.of(new Entry(0, 0, 2)), owner.writes);
        assertFalse(MatrixBuildScope.nativeAdd(owner.mna, 0, 0, 1));
    }
    @Test void cancellingContributionsRetainTheirStructuralZero() {
        var owner = new Solver();
        try (var scope = MatrixBuildScope.begin(owner.mna)) {
            MatrixBuildScope.nativeAdd(owner.mna, 0, 0, 4);
            MatrixBuildScope.nativeAdd(owner.mna, 0, 0, -4);
            MatrixBuildScope.flush(owner.mna);
        }
        assertEquals(List.of(new Entry(0, 0, 4), new Entry(0, 0, -4)), owner.writes);
    }
    @Test void recursiveAssemblyCannotOverwriteTheSameSolversPendingBuild() {
        var owner = new Solver();
        try (var scope = MatrixBuildScope.begin(owner.mna)) {
            MatrixBuildScope.nativeAdd(owner.mna, 0, 0, 2);
            assertThrows(IllegalStateException.class, () -> MatrixBuildScope.begin(owner.mna));
            MatrixBuildScope.flush(owner.mna);
        }
        assertEquals(List.of(new Entry(0, 0, 2)), owner.writes);
    }
}
