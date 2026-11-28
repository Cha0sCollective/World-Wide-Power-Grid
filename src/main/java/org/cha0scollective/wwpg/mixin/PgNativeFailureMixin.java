package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.cha0scollective.wwpg.bridge.NativeMatrixAllocationError;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.ByteBuffer;

/** Failed allocations invalidate the Java view and require a full matrix rebuild. */
@Mixin(value = NativeMNA.class, remap = false)
abstract class PgNativeFailureMixin {
    @Shadow protected boolean converged;
    @Shadow private ByteBuffer stateBuffer;
    @Shadow private int size;
    @Shadow @Final public ElectricalNetwork network;

    @WrapMethod(method = "allocate")
    private void wwpg$dropFailedAllocation(int size, Operation<Void> original) {
        try {
            original.call(size);
        } catch (RuntimeException | Error failure) {
            wwpg$invalidateMatrix();
            if (failure instanceof OutOfMemoryError allocation)
                throw new NativeMatrixAllocationError("resize", size, allocation);
            throw failure;
        }
    }

    @WrapMethod(method = "finishJacobianWrite()V")
    private void wwpg$finishJacobian(Operation<Void> original) {
        try {
            original.call();
        } catch (OutOfMemoryError allocation) {
            wwpg$invalidateMatrix();
            throw new NativeMatrixAllocationError("finishJacobianWrite", size, allocation);
        }
    }

    @WrapOperation(method = "jacobianAdd", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/sim/solver/NativeMNA;processJacobianBuffer(JI)V"))
    private void wwpg$flushJacobian(long handle, int commands, Operation<Void> original) {
        try {
            original.call(handle, commands);
        } catch (OutOfMemoryError allocation) {
            wwpg$invalidateMatrix();
            throw new NativeMatrixAllocationError("processJacobianBuffer", size, allocation);
        }
    }

    @Unique
    private void wwpg$invalidateMatrix() {
        stateBuffer = null;
        converged = false;
        network.setDirty();
    }
}
