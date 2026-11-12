package org.cha0scollective.wwpg.clientdelivery.mixin;

import net.minecraft.util.thread.BlockableEventLoop;
import org.cha0scollective.wwpg.clientdelivery.TaskQueueTimings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockableEventLoop.class)
public abstract class TaskQueueTimingMixin {
    @Inject(method = "tell(Ljava/lang/Runnable;)V", at = @At("HEAD"))
    private void delivery$queued(Runnable task, CallbackInfo ci) { TaskQueueTimings.enqueue(this, task); }
    @Inject(method = "doRunTask(Ljava/lang/Runnable;)V", at = @At("HEAD"))
    private void delivery$started(Runnable task, CallbackInfo ci) { TaskQueueTimings.start(this, task); }
    @Inject(method = "doRunTask(Ljava/lang/Runnable;)V", at = @At("RETURN"))
    private void delivery$finished(Runnable task, CallbackInfo ci) { TaskQueueTimings.finish(this, task); }
}
