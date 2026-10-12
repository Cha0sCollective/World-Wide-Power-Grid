package org.cha0scollective.wwpg.mixin;

import org.ejml.data.DMatrix;
import org.ejml.data.DMatrixRMaj;
import org.ejml.interfaces.linsol.LinearSolver;
import org.patryk3211.powergrid.electricity.sim.solver.DynamicallyTypedMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DynamicallyTypedMatrix.class, remap = false)
public abstract class PgSparseMatrixMixin {
    @Shadow private DMatrix matrix;
    @Shadow private boolean sparse;
    @Shadow private DMatrix resultMatrix;
    @Shadow private LinearSolver<? extends DMatrix, DMatrixRMaj> solver;
    @Shadow private boolean refactorize;

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void wwpg$collectBuild(int row, int column, double value, CallbackInfo ci) {
        if (org.cha0scollective.wwpg.bridge.MatrixBuildScope.add(
                (DynamicallyTypedMatrix) (Object) this, row, column, value)) ci.cancel();
    }

    @Inject(method = "denseZero", at = @At("HEAD"), cancellable = true)
    private void wwpg$clearSparseWithoutDenseAllocation(CallbackInfo ci) {
        if (!sparse) return;
        matrix.zero();
        // Upstream's sparse-to-dense rebuild invalidates both the factorization
        // and its working copy. Preserve that behavior without N-squared storage.
        resultMatrix = null;
        solver = null;
        refactorize = true;
        ci.cancel();
    }
}
