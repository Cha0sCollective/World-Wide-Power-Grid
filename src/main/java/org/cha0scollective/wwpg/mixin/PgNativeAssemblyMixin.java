package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.MatrixBuildScope;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NativeMNA.class, remap = false)
public abstract class PgNativeAssemblyMixin {
    @Inject(method = "jacobianAdd", at = @At("HEAD"), cancellable = true)
    private void wwpg$collect(int row, int column, double value, CallbackInfo ci) {
        if (MatrixBuildScope.nativeAdd((NativeMNA) (Object) this, row, column, value)) ci.cancel();
    }
}
