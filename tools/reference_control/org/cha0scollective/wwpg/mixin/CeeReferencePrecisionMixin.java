package org.cha0scollective.wwpg.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Test-only causal control. The raw stock run never applies this mixin. */
@Mixin(targets="com.george_vi.electroenergetics.simulation.simulator.Network",remap=false)
abstract class CeeReferencePrecisionMixin {
    @ModifyConstant(method="runSolver",constant=@Constant(doubleValue=1e-2),require=1)
    private double tightResidual(double original){return 1e-16;}
}
