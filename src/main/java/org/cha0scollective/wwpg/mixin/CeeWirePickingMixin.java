package org.cha0scollective.wwpg.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.cha0scollective.wwpg.wiring.CeeTerminalPicking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PG's highlighter and wire tools both consume Entity.pick on their respective side. */
@Mixin(Entity.class)
public abstract class CeeWirePickingMixin {
    @Inject(method = "pick", at = @At("RETURN"), cancellable = true)
    private void wwpg$exposedCeePins(double reach, float partialTick, boolean fluids, CallbackInfoReturnable<HitResult> cir) {
        if (fluids || !((Object) this instanceof Player player) || !CeeTerminalPicking.holdsPgWire(player)) return;
        var from = player.getEyePosition(partialTick);
        var to = from.add(player.getViewVector(partialTick).scale(reach));
        cir.setReturnValue(CeeTerminalPicking.pick(player.level(), from, to, cir.getReturnValue()));
    }
}
