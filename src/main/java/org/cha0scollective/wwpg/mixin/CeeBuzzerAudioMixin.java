package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.buzzer.BuzzerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CEE's physical-client helper also runs on the integrated server thread. */
@Mixin(value = BuzzerBlockEntity.class, remap = false)
abstract class CeeBuzzerAudioMixin {
    @Inject(method = "tickAudio", at = @At("HEAD"), cancellable = true)
    private void wwpg$onlyClientWorldPlaysSound(CallbackInfo ci) {
        var level = ((BlockEntity) (Object) this).getLevel();
        if (level == null || !level.isClientSide()) ci.cancel();
    }
}
