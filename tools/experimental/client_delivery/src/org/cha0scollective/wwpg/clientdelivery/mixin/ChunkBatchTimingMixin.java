package org.cha0scollective.wwpg.clientdelivery.mixin;

import net.minecraft.Util;
import net.minecraft.client.multiplayer.ChunkBatchSizeCalculator;
import org.cha0scollective.wwpg.clientdelivery.ClientDeliveryDiagnostic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkBatchSizeCalculator.class)
public abstract class ChunkBatchTimingMixin {
    @Shadow private double aggregatedNanosPerChunk;
    @Shadow private int oldSamplesWeight;
    @Shadow private volatile long chunkBatchStartTime;
    @Inject(method = "onBatchFinished", at = @At("RETURN"))
    private void delivery$batch(int chunks, CallbackInfo ci) {
        ClientDeliveryDiagnostic.batch(chunks, Util.getNanos() - chunkBatchStartTime,
                aggregatedNanosPerChunk, oldSamplesWeight);
    }
}
