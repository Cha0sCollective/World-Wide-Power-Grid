package org.cha0scollective.wwpg.mixin;

import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.electricity.base.ISynchronizedElement;
import org.patryk3211.powergrid.network.packets.StateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StateS2CPacket.PosKey.class, remap = false)
public abstract class SyncPositionMixin {
    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void wwpg$sidecarSync(Level level, CallbackInfoReturnable<ISynchronizedElement> cir) {
        if (cir.getReturnValue() != null) return;
        var adapter = Terminals.cee(level, ((StateS2CPacket.PosKey) (Object) this).pos());
        if (adapter != null) cir.setReturnValue(adapter.behaviour());
    }
}
