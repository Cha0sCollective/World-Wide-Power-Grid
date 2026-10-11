package org.cha0scollective.wwpg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IElectric.class, remap = false)
public interface PgElectricMixin {
    @Inject(method = "getAt", at = @At("HEAD"), cancellable = true)
    private static void wwpg$ceeTerminals(Level level, BlockPos pos, CallbackInfoReturnable<IElectric> cir) {
        cir.setReturnValue(Terminals.electric(level, pos));
    }
}
