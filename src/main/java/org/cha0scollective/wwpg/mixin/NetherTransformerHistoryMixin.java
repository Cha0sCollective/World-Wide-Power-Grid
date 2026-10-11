package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** PG 0.6.2 writes one entry per sample but reads the list at twice that index. */
@Mixin(value = NetherTransformerBlockEntity.class, remap = false)
public abstract class NetherTransformerHistoryMixin {
    @ModifyArg(method = "read", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/nbt/ListTag;getDouble(I)D"), index = 0)
    private int wwpg$restoreEachSample(int doubledIndex) {
        return doubledIndex / 2;
    }
}
