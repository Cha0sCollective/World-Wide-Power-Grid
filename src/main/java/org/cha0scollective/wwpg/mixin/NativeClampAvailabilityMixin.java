package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An unavailable native CEE wire reading must not animate current flow. */
@Mixin(value = ClampMeterItem.class, remap = false)
public abstract class NativeClampAvailabilityMixin {
    @Inject(method = "setMetering", at = @At("HEAD"), cancellable = true)
    private void wwpg$unavailableCurrent(float current, CallbackInfo ci) {
        if (!Float.isFinite(current)) {
            ElectricPropertiesOverlay.INSTANCE.setAmmeter(Float.NaN);
            ci.cancel();
        }
    }
}
