package org.cha0scollective.wwpg.mixin;

import net.minecraft.network.chat.Component;
import org.cha0scollective.wwpg.compat.cio.CioCompatibility;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Keep legacy blocks and physical terminals, but no source, transfer, heat or sound. */
@Pseudo
@Mixin(targets = {
        "com.cio.createinteroperable.bridge.MiniGridCouplerBlockEntity",
        "com.cio.createinteroperable.bridge.InteroperableSmallBlockEntity",
        "com.cio.createinteroperable.bridge.InteroperableCouplerBlockEntity",
        "com.cio.createinteroperable.bridge.InteroperableDoubleCouplerBlockEntity",
        "com.cio.createinteroperable.bridge.InteroperablePgAssembledBlockEntity"
}, remap = false)
public abstract class CioCouplerMixin {
    @Inject(method = "buildCircuit", at = @At("HEAD"), cancellable = true)
    private void wwpg$passiveTerminals(IElectricEntity.CircuitBuilder builder, CallbackInfo ci) {
        builder.setTerminalCount(2);
        ci.cancel();
    }

    @Inject(method = {"electricalTick", "tickAudio"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void wwpg$disableTransfer(CallbackInfo ci) { ci.cancel(); }

    @Inject(method = "addToGoggleTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void wwpg$disabledTooltip(List<Component> tooltip, boolean sneaking, CallbackInfoReturnable<Boolean> cir) {
        tooltip.add(CioCompatibility.disabledMessage());
        cir.setReturnValue(true);
    }
}
