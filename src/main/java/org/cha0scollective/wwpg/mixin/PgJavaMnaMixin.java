package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.SparseMatrixSupport;
import org.ejml.data.DMatrixSparseCSC;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.ICouplingNode;
import org.patryk3211.powergrid.electricity.sim.solver.DynamicallyTypedMatrix;
import org.patryk3211.powergrid.electricity.sim.solver.JavaMNA;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JavaMNA.class, remap = false)
public abstract class PgJavaMnaMixin {
    @Shadow @Final public ElectricalNetwork network;
    @Shadow private double[] columnScales;
    @Shadow private double[] rowScales;
    @Shadow private int scalesAge;

    @Redirect(method = "allocate", at = @At(value = "NEW",
            target = "org/patryk3211/powergrid/electricity/sim/solver/DynamicallyTypedMatrix"))
    private DynamicallyTypedMatrix wwpg$allocateSparse(int rows, int cols, DynamicallyTypedMatrix.Solver type) {
        // Match PG's existing threshold; keep its small dense systems intact.
        if (rows <= 6 && cols <= 6) return new DynamicallyTypedMatrix(rows, cols, type);
        var result = new DynamicallyTypedMatrix(0, 0, type);
        var access = (PgMatrixAccessor) (Object) result;
        access.wwpg$matrix(new DMatrixSparseCSC(rows, cols));
        access.wwpg$sparse(true);
        return result;
    }

    @Inject(method = "computeScales", at = @At("HEAD"), cancellable = true)
    private void wwpg$sparseScales(DynamicallyTypedMatrix work, CallbackInfo ci) {
        var matrix = ((PgMatrixAccessor) (Object) work).wwpg$matrix();
        if (!(matrix instanceof DMatrixSparseCSC sparse)) return;
        var nodes = network.getNodes();
        SparseMatrixSupport.scales(sparse, index -> nodes.get(index) instanceof ICouplingNode, columnScales, rowScales);
        scalesAge = 0;
        ci.cancel();
    }
}
