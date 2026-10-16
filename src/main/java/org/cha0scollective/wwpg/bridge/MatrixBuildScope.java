package org.cha0scollective.wwpg.bridge;

import org.cha0scollective.wwpg.mixin.PgMatrixAccessor;
import org.ejml.data.DMatrixSparseCSC;
import org.patryk3211.powergrid.electricity.sim.solver.DynamicallyTypedMatrix;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import java.util.IdentityHashMap;
import java.util.Objects;

/** Server-thread-local full assembly only; incremental/nonlinear writes remain native. */
public final class MatrixBuildScope implements AutoCloseable {
    private static final ThreadLocal<MatrixBuildScope> ACTIVE = new ThreadLocal<>();
    private final IMNA owner;
    private final MatrixBuildScope parent;
    private final IdentityHashMap<DynamicallyTypedMatrix, SparseAssembly> javaMatrices = new IdentityHashMap<>();
    private final SparseAssembly nativeMatrix = new SparseAssembly();
    private boolean nativeUsed;
    private MatrixBuildScope(IMNA owner, MatrixBuildScope parent) {
        this.owner = Objects.requireNonNull(owner, "solver"); this.parent = parent;
    }
    public static MatrixBuildScope begin(IMNA owner) {
        for (var active = ACTIVE.get(); active != null; active = active.parent)
            if (active.owner == owner) throw new IllegalStateException("Recursive matrix assembly for the same solver");
        var scope = new MatrixBuildScope(owner, ACTIVE.get());
        ACTIVE.set(scope); return scope;
    }
    @Override public void close() {
        if (parent == null) ACTIVE.remove(); else ACTIVE.set(parent);
    }
    public static boolean add(IMNA owner, DynamicallyTypedMatrix target, int row, int column, double value) {
        var scope = ACTIVE.get(); if (scope == null || scope.owner != owner) return false;
        var storage = ((PgMatrixAccessor) (Object) target).wwpg$matrix();
        if (!(storage instanceof DMatrixSparseCSC matrix)) return false;
        if (row < 0 || column < 0 || row >= matrix.numRows || column >= matrix.numCols)
            throw new IllegalArgumentException("Matrix coordinate outside dimensions");
        scope.javaMatrices.computeIfAbsent(target, ignored -> {
            var assembly = new SparseAssembly(); assembly.seed(matrix); return assembly;
        }).add(row, column, value);
        return true;
    }
    public static boolean nativeAdd(IMNA owner, int row, int column, double value) {
        var scope = ACTIVE.get(); if (scope == null || scope.owner != owner) return false;
        if (value != 0) { scope.nativeUsed = true; scope.nativeMatrix.add(row, column, value); }
        return true;
    }
    public static void flush(IMNA solver) {
        var scope = ACTIVE.get(); if (scope == null || scope.owner != solver) return;
        // Flush through the ordinary write path. A nested build restores its
        // parent only when its enclosing try-with-resources exits.
        ACTIVE.remove();
        scope.javaMatrices.forEach((target, assembly) -> {
            var access = (PgMatrixAccessor) (Object) target;
            var previous = access.wwpg$matrix();
            access.wwpg$matrix(assembly.matrix(previous.getNumRows(), previous.getNumCols()));
            access.wwpg$resultMatrix(null); access.wwpg$solver(null); access.wwpg$refactorize(true);
        });
        if (scope.nativeUsed) scope.nativeMatrix.ordered((row, column, value, first) -> {
            // Preserve a structural zero created by cancelling nonzero contributions.
            if (value == 0) { solver.jacobianAdd(row, column, first); solver.jacobianAdd(row, column, -first); }
            else solver.jacobianAdd(row, column, value);
        });
    }
}
