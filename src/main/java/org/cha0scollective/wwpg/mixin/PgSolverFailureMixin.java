package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.solver.JavaMNA;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Neither backend may retain the preceding substep's acceptance after an exception. */
@Mixin(value = {NativeMNA.class, JavaMNA.class}, remap = false)
abstract class PgSolverFailureMixin {
    @Shadow(remap = false) protected boolean converged;
    @Shadow(remap = false) @Final public ElectricalNetwork network;

    @WrapMethod(method = "singleTick()V")
    private void wwpg$rejectFailedStep(Operation<Void> original) {
        try { original.call(); }
        catch (RuntimeException | Error error) { converged = false; throw error; }
        finally { network.countUpdates = true; }
    }
}
