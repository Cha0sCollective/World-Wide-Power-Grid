package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.pole.ConcretePoleDevice;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** CEE's two-segment scan must check the preceding segment after overshooting. */
@Mixin(value = ConcretePoleDevice.class, remap = false)
public abstract class ConcretePoleHeightMixin {
    @ModifyArg(method = "preTick", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/devices/device/DevicesSavedData;getDevice(Lnet/minecraft/core/BlockPos;Ljava/lang/Class;)Lcom/george_vi/electroenergetics/devices/device/SimulatedDevice;",
            ordinal = 1), index = 0)
    private BlockPos wwpg$checkLastSegment(BlockPos overshotPosition) {
        return overshotPosition.below();
    }
}
