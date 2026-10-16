package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.nio.ByteBuffer;

/** Failed native allocation must not retain a released direct-buffer view. */
@Mixin(value = NativeMNA.class, remap = false)
abstract class PgNativeFailureMixin {
    @Shadow protected boolean converged;
    @Shadow private ByteBuffer stateBuffer;

    @WrapMethod(method = "allocate")
    private void wwpg$dropFailedAllocation(int size, Operation<Void> original) {
        try { original.call(size); }
        catch (RuntimeException | Error error) { stateBuffer = null; converged = false; throw error; }
    }
}
