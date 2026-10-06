package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterDevice;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The meter counts PG substeps; CEE's independent substep setting no longer owns time. */
@Mixin(value = FrequencyMeterDevice.class, remap = false)
abstract class FrequencyMeterTimingMixin {
    @ModifyExpressionValue(method = "calculateFrequency", at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/config/ConfigBase$ConfigInt;get()Ljava/lang/Object;"))
    private Object wwpg$sampleRate(Object ignored) {
        return ModdedConfigs.server().electricity.solver.multiTicks.get();
    }
}
