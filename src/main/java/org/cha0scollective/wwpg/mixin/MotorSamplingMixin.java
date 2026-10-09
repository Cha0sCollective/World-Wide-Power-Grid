package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the motor's three-tick RMS history measured in authoritative PG substeps. */
@Mixin(value=ElectricMotorBlockEntity.class,remap=false)
abstract class MotorSamplingMixin {
    @ModifyExpressionValue(method="<init>",at=@At(value="INVOKE",
            target="Lnet/createmod/catnip/config/ConfigBase$ConfigInt;get()Ljava/lang/Object;"))
    private Object wwpg$historySamples(Object ignored){
        return ModdedConfigs.server().electricity.solver.multiTicks.get();
    }
}
