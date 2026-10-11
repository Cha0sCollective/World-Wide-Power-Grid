package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import org.cha0scollective.wwpg.extension.ApiCeeWireIdentities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native loading builds saved connections directly; these hooks identify physical edits only. */
@Mixin(value = InfrastructureSavedData.class, remap = false)
public abstract class CeeWireIdentityMixin {
    @Inject(method = {
            "connect(Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;Lcom/george_vi/electroenergetics/simulation/infrastructure/WireData;)J",
            "connectNoUpdate"}, at = @At("RETURN"))
    private void wwpg$newPhysicalWireIdentity(InWorldNode first, InWorldNode second, WireData data, CallbackInfoReturnable<Long> cir) {
        ApiCeeWireIdentities.connected(((InfrastructureSavedData) (Object) this).level, first, second);
    }

    @Inject(method = {"removeConnection", "removeConnectionNoDrops"}, at = @At("RETURN"))
    private void wwpg$retirePhysicalWireIdentity(InWorldNodeConnection connection, CallbackInfoReturnable<WireData> cir) {
        if (cir.getReturnValue() != null) ApiCeeWireIdentities.removed(((InfrastructureSavedData) (Object) this).level, connection);
    }
}
