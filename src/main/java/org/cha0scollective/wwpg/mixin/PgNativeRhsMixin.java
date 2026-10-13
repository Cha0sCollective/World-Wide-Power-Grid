package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep valid additive zeroes out of PG's command buffer without hiding malformed rows. */
@Mixin(value = NativeMNA.class, remap = false)
public abstract class PgNativeRhsMixin {
    @Shadow private int size;

    @Inject(method = "rhsAdd", at = @At("HEAD"), cancellable = true)
    private void wwpg$skipZero(int row, double value, CallbackInfo ci) {
        if (value == 0 && row >= 0 && row < size) ci.cancel();
    }
}
