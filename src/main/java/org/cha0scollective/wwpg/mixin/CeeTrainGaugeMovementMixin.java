package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.gauge.ElectricGaugeMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ElectricGaugeMovementBehaviour.class, remap = false)
abstract class CeeTrainGaugeMovementMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void wwpg$recoverDialAnimation(MovementContext context, CallbackInfo ci) {
        var data = context.data;
        if (!data.contains("DialTarget")) return;

        // Keep the unavailable target for rendering. Only the interpolation state is reset.
        float target = data.getFloat("DialTarget");
        if (!Float.isFinite(target)) {
            data.remove("Initialized");
            data.putFloat("PrevDialState", 0);
            data.putFloat("DialState", 0);
        } else if (!Float.isFinite(data.getFloat("DialState")) || !Float.isFinite(data.getFloat("PrevDialState"))) {
            // A contraption saved before this fix can already contain an invalid dial.
            data.putFloat("PrevDialState", target);
            data.putFloat("DialState", target);
        }
    }
}
