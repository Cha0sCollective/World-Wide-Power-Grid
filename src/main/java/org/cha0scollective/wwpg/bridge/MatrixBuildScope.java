package org.cha0scollective.wwpg.bridge;

import org.cha0scollective.wwpg.mixin.PgMatrixAccessor;
import org.ejml.data.DMatrixSparseCSC;
import org.patryk3211.powergrid.electricity.sim.solver.DynamicallyTypedMatrix;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import java.util.IdentityHashMap;

/** Server-thread-local full assembly only; incremental/nonlinear writes remain native. */
public final class MatrixBuildScope {
    private static final ThreadLocal<MatrixBuildScope> ACTIVE = new ThreadLocal<>();
    private final IdentityHashMap<DynamicallyTypedMatrix, SparseAssembly> javaMatrices = new IdentityHashMap<>();
    private final SparseAssembly nativeMatrix = new SparseAssembly();
    private boolean nativeUsed;
    public static void begin() {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested matrix assembly");
        ACTIVE.set(new MatrixBuildScope());
    }
    public static void abort() { ACTIVE.remove(); }
    public static boolean add(DynamicallyTypedMatrix target, int row, int column, double value) {
        var scope = ACTIVE.get(); if (scope == null) return false;
        var storage = ((PgMatrixAccessor) (Object) target).wwpg$matrix();
        if (!(storage instanceof DMatrixSparseCSC matrix)) return false;
        if (row < 0 || column < 0 || row >= matrix.numRows || column >= matrix.numCols)
            throw new IllegalArgumentException("Matrix coordinate outside dimensions");
        scope.javaMatrices.computeIfAbsent(target, ignored -> {
            var assembly = new SparseAssembly(); assembly.seed(matrix); return assembly;
        }).add(row, column, value);
        return true;
    }
    public static boolean nativeAdd(int row, int column, double value) {
        var scope = ACTIVE.get(); if (scope == null) return false;
        if (value != 0) { scope.nativeUsed = true; scope.nativeMatrix.add(row, column, value); }
        return true;
    }
    public static void flush(IMNA solver) {
        var scope = ACTIVE.get(); ACTIVE.remove(); if (scope == null) return;
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
