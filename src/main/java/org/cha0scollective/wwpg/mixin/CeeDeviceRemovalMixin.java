package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import net.minecraft.core.BlockPos;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DevicesSavedData.class, remap = false)
public abstract class CeeDeviceRemovalMixin {
    @Inject(method = "removeDevice", at = @At("HEAD"))
    private void wwpg$cutPgWires(BlockPos pos, CallbackInfoReturnable<SimulatedDevice> cir) {
        Terminals.removeCee(((DevicesSavedData) (Object) this).level, pos);
    }
}
