package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.variac.VariacDevice;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VariacDevice.class, remap = false)
public abstract class VariacResultsMixin {
    // Require exactly one known upstream layout. CEE 1.1.3 needs the position
    // correction; 1.1.4 already supplies it. Unknown/ambiguous layouts still fail.
    @Group(name = "wwpg$variacVoltage", min = 1, max = 1)
    @Redirect(method = "postTick", require = 0, at = @At(value = "INVOKE", target = "Lcom/george_vi/electroenergetics/simulation/SimulationResults;getVoltageAt(II)D"))
    private double wwpg$localVoltage(SimulationResults results, int first, int second) {
        return results.getVoltageAt(((VariacDevice) (Object) this).pos, first, second);
    }

    @Group(name = "wwpg$variacVoltage", min = 1, max = 1)
    @Redirect(method = "postTick", require = 0, at = @At(value = "INVOKE", target = "Lcom/george_vi/electroenergetics/simulation/SimulationResults;getVoltageAt(Lnet/minecraft/core/BlockPos;II)D"))
    private double wwpg$upstreamVoltage(SimulationResults results, BlockPos pos, int first, int second) {
        return results.getVoltageAt(pos, first, second);
    }
}
