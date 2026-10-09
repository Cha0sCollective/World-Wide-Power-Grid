package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.variac.VariacDevice;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VariacDevice.class, remap = false)
public abstract class VariacResultsMixin {
    // CEE 1.1.3's loss calculation passes local terminal IDs to the global-index overload.
    @Redirect(method = "postTick", at = @At(value = "INVOKE", target = "Lcom/george_vi/electroenergetics/simulation/SimulationResults;getVoltageAt(II)D"))
    private double wwpg$localVoltage(SimulationResults results, int first, int second) {
        return results.getVoltageAt(((VariacDevice) (Object) this).pos, first, second);
    }
}
