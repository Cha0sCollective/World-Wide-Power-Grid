package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.bridge.ServerElectricalSchedule;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlobalElectricNetworks.class, remap = false)
public abstract class GlobalNetworksScheduleMixin {
    @Inject(method = "preTick", at = @At("HEAD"), cancellable = true)
    private static void wwpg$interleaveDimensions(Level world, CallbackInfo ci) {
        if (world instanceof ServerLevel level) {
            ServerElectricalSchedule.preTick(level);
            ci.cancel();
        }
    }
}
