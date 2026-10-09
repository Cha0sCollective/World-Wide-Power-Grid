package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.wiring.RemovedWires;
import org.patryk3211.powergrid.electricity.wire.WireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WireEntity.class, remap = false)
public abstract class PgWireRemovalMixin {
    @Inject(method = "makeWire", at = @At("HEAD"), cancellable = true)
    private void wwpg$discardDeletedWire(CallbackInfo ci) {
        var wire = (WireEntity) (Object) this;
        if (wire.level() instanceof ServerLevel level && RemovedWires.get(level).contains(wire.getUUID())) {
            wire.kill(); ci.cancel();
        }
    }
}
