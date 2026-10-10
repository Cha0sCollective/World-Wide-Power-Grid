package org.cha0scollective.wwpg.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaseWireEntity.class, remap = false)
public abstract class ClampWireMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void wwpg$clamp(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        var result = HandheldMeters.attachClamp((BaseWireEntity) (Object) this, player, hand);
        if (result.consumesAction()) cir.setReturnValue(result);
    }
}
