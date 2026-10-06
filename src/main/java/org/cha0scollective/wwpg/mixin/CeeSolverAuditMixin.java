package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.simulation.util.CholeskySolver;
import com.george_vi.electroenergetics.simulation.util.LUSolver;
import org.cha0scollective.wwpg.bridge.SolverAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {LUSolver.class, CholeskySolver.class}, remap = false)
public abstract class CeeSolverAuditMixin {
    @Inject(method = "solve", at = @At("HEAD"))
    private static void wwpg$audit(CallbackInfoReturnable<double[]> cir) { SolverAudit.rejectCeeSolve(); }
}
