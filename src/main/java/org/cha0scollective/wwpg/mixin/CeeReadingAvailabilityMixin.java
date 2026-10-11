package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.CEELang;
import net.createmod.catnip.lang.LangBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native CEE overlays have primitive readings; nonfinite values mean unavailable. */
@Mixin(value = CEELang.class, remap = false)
public abstract class CeeReadingAvailabilityMixin {
    @Inject(method = {"formatVoltage", "formatAmperage"}, at = @At("HEAD"), cancellable = true)
    private static void wwpg$unavailableReading(double value, CallbackInfoReturnable<LangBuilder> cir) {
        if (!Double.isFinite(value)) cir.setReturnValue(new LangBuilder("wwpg").translate("reading.unavailable"));
    }
}
