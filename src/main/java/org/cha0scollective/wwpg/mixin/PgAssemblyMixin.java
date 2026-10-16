package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.cha0scollective.wwpg.bridge.MatrixBuildScope;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class PgAssemblyMixin {
    @Shadow private IMNA mna;
    @WrapMethod(method = "populateConductanceMatrix")
    private void wwpg$fullBuild(Operation<Void> original) {
        if (mna == null) { original.call(); return; }
        try (var scope = MatrixBuildScope.begin(mna)) { original.call(); }
    }
    @Redirect(method = "populateConductanceMatrix", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/sim/solver/IMNA;finishJacobianWrite()V"))
    private void wwpg$finish(IMNA solver) { MatrixBuildScope.flush(solver); solver.finishJacobianWrite(); }
}
