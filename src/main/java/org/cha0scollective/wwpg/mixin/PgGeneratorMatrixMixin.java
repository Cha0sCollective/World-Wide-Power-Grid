package org.cha0scollective.wwpg.mixin;
import org.cha0scollective.wwpg.bridge.PgGeneratorTiming;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=VoltageSourceCoupling.class,remap=false)
public abstract class PgGeneratorMatrixMixin {
    @Inject(method="couple",at=@At("RETURN"))
    private void wwpg$resetLinearization(IAdmittanceAdder adder,CallbackInfo ci){
        if((Object)this instanceof PgGeneratorTiming.Source source)source.wwpg$coupling().matrixReset();
    }
}
