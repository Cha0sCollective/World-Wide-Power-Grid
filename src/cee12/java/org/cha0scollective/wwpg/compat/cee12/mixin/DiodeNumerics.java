package org.cha0scollective.wwpg.compat.cee12.mixin;
import com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=DiodeProperties.class,remap=false)
public interface DiodeNumerics {
    @Accessor("vOld") double wwpg$oldVoltage();
    @Accessor("vOld") void wwpg$oldVoltage(double value);
    @Accessor("vt") double wwpg$thermalVoltage();
    @Accessor("is") double wwpg$saturationCurrent();
}
