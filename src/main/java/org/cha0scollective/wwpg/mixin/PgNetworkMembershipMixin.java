package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import java.util.Set;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class PgNetworkMembershipMixin {
    @Shadow private int sourceCount;
    @Shadow private int groundReferenceCount;
    @Shadow @Final protected Set<ISolverHook> leafInnerHooks;

    @Inject(method = "clear", at = @At("RETURN"))
    private void wwpg$clearMembershipCounts(CallbackInfo ci) {
        // Rebuilds can rejoin a remembered owner after merge emptied it. Its
        // counters must start with the same empty membership as its collections.
        sourceCount = 0;
        groundReferenceCount = 0;
        // A later removeLeaf must not revive callbacks belonging to retired wires.
        leafInnerHooks.clear();
    }
}
