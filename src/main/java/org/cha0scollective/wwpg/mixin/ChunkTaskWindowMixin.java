package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.MinecraftServer;
import org.cha0scollective.wwpg.experimental.ChunkTaskPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class ChunkTaskWindowMixin {
    @Unique private final ChunkTaskPump wwpg$chunkTaskPump = new ChunkTaskPump();

    @Inject(method = "waitUntilNextTick", at = @At("RETURN"))
    private void wwpg$serviceLoadingTasks(CallbackInfo ci) {
        wwpg$chunkTaskPump.afterWait((MinecraftServer) (Object) this);
    }

    @Inject(method = "haveTime", at = @At("RETURN"), cancellable = true)
    private void wwpg$allowBoundedLoadingWindow(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && wwpg$chunkTaskPump.allowsTime()) cir.setReturnValue(true);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void wwpg$reportLoadingWindow(CallbackInfo ci) { wwpg$chunkTaskPump.report(); }
}
