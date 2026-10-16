package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.NativeBootstrap;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.patryk3211.powergrid.PowerGrid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=NativeMNA.class,remap=false)
abstract class NativeResourceMixin {
    @Shadow private static boolean supported;
    @Shadow private static int verifySupport() { throw new AssertionError(); }

    @Inject(method="tryLoad",at=@At("HEAD"),cancellable=true)
    private static void wwpg$loadGuarded(CallbackInfo ci) {
        ci.cancel();
        supported = false;
        try {
            var library = NativeBootstrap.installGuarded();
            if (library == null) return;
            // Execute in NativeMNA's loader: never fall back to an old v7 file.
            System.load(library.toString());
            if (verifySupport() != 0) throw new UnsatisfiedLinkError("Guarded native backend is unsupported on this CPU");
            supported = true;
            PowerGrid.LOGGER.info("Loaded guarded PG native solver: {}", library);
        } catch (java.io.IOException | RuntimeException | LinkageError error) {
            PowerGrid.LOGGER.error("Guarded native solver unavailable; unsafe v7 fallback is disabled. Use the Java backend.", error);
        }
    }
}
