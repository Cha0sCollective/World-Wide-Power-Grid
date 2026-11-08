package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldNetworks.class, remap = false)
public abstract class PgWireTreeBatchMixin {
    @Inject(method = "makeTransmissionLine(Lorg/patryk3211/powergrid/electricity/wire/IWireEndpoint;Lorg/patryk3211/powergrid/electricity/wire/IWireEndpoint;Lorg/patryk3211/powergrid/electricity/wire/BaseWireEntity;Lorg/patryk3211/powergrid/electricity/WorldNetworks$PartId;)Lorg/patryk3211/powergrid/electricity/sim/ElectricWire;", at = @At("HEAD"), cancellable = true)
    private void wwpg$restorePart(IWireEndpoint first, IWireEndpoint second, BaseWireEntity wire, WorldNetworks.PartId id,
                                  CallbackInfoReturnable<ElectricWire> cir) {
        var part = WireTreeBatch.attach((WorldNetworks) (Object) this, first, second, wire, id);
        if (part != null) cir.setReturnValue(part);
    }
}
