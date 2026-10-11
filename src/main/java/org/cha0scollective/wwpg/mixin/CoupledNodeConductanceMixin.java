package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Match PG's full matrix rebuild when nonlinear stamps have more than two terminals. */
@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class CoupledNodeConductanceMixin {
    @Inject(method = "updateConductance", at = @At("HEAD"), cancellable = true)
    private void wwpg$validateAllCoupledNodes(AbstractElectricWire wire, double change, CallbackInfo ci) {
        var network = (ElectricalNetwork) (Object) this;
        if (network.isDirty()) return;
        for (var node : wire.coupledNodes()) {
            // Leaf stamps are absent from PG's rebuilt matrix. Subtracting them during
            // CompoundWire removal would address a reduced control node at index -1.
            if (network.isLeaf(node)) { ci.cancel(); return; }
            if (node == null) continue;
            int index = node.getIndex();
            if (index < 0 || index >= network.size() || network.getNodes().get(index) != node) {
                // A departed terminal requires PG's normal stale-wire/full-rebuild path.
                network.setDirty();
                ci.cancel();
                return;
            }
        }
    }
}
