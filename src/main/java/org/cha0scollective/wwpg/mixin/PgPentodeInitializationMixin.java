package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.PentodeInitialization;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.special.PentodeWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ElectricalNetwork.class,remap=false)
public abstract class PgPentodeInitializationMixin {
    @Inject(method="addWire",at=@At("HEAD"))
    private void wwpg$trackFreshTube(AbstractElectricWire wire,CallbackInfo ci) {
        var network=(ElectricalNetwork)(Object)this;
        if(wire instanceof PentodeWire tube&&wire.getNetwork()!=network)PentodeInitialization.added(network,tube);
    }
}
