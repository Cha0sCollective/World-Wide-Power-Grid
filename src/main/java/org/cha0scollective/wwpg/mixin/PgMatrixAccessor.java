package org.cha0scollective.wwpg.mixin;

import org.ejml.data.DMatrix;
import org.ejml.data.DMatrixRMaj;
import org.ejml.interfaces.linsol.LinearSolver;
import org.patryk3211.powergrid.electricity.sim.solver.DynamicallyTypedMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DynamicallyTypedMatrix.class, remap = false)
public interface PgMatrixAccessor {
    @Accessor("matrix") DMatrix wwpg$matrix();
    @Accessor("matrix") void wwpg$matrix(DMatrix matrix);
    @Accessor("sparse") void wwpg$sparse(boolean sparse);
    @Accessor("resultMatrix") void wwpg$resultMatrix(DMatrix matrix);
    @Accessor("solver") void wwpg$solver(LinearSolver<? extends DMatrix, DMatrixRMaj> solver);
    @Accessor("refactorize") void wwpg$refactorize(boolean refactorize);
}
