package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IStaticResidual;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class PgResidualAssemblyMixin {
    @Shadow private IMNA mna;
    @Shadow @Final protected Set<IStaticResidual> residuals;
    @Shadow @Final protected Map<IElectricNode, IElectricNode> leafNodes;

    @Inject(method = "computeRHS", at = @At("HEAD"), cancellable = true)
    private void wwpg$assembleWithoutLeafChecks(CallbackInfo ci) {
        if (!leafNodes.isEmpty()) return;
        mna.zeroRHS();
        IResidualAdder add = mna::rhsAdd;
        // No incidence list can intersect an empty leaf map. Keep every residual
        // in native order: source values and reactive history still change each step.
        for (var residual : residuals) residual.addStaticResidual(add);
        ci.cancel();
    }
}
