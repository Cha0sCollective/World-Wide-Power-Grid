package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.NativeBootstrap;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=NativeMNA.class,remap=false)
abstract class NativeResourceMixin {
    @Inject(method="tryLoad",at=@At("HEAD"))
    private static void wwpg$restorePinnedResources(CallbackInfo ci){NativeBootstrap.installIfMissing();}
}
