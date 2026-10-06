package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ElectricBehaviour.class, remap = false)
public abstract class PgBehaviourMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void wwpg$removeCeeWires(CallbackInfo ci) {
        var behaviour = (ElectricBehaviour) (Object) this;
        if (behaviour.getWorld() instanceof ServerLevel level)
            Terminals.removePgNodes(level, behaviour.getPos());
    }
    @Inject(method = "rebuildCircuit", at = @At("RETURN"))
    private void wwpg$refreshCeeTerminals(boolean external, CallbackInfo ci) {
        var behaviour = (ElectricBehaviour) (Object) this;
        if (external && behaviour.getWorld() instanceof ServerLevel level)
            Terminals.refreshPgNodes(level, behaviour.getPos());
    }
}
