package org.cha0scollective.wwpg.compat.cee12.mixin;
import com.george_vi.electroenergetics.foundation.electrical_properties.CapacitorProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=CapacitorProperties.class,remap=false)
public interface CapacitorNumerics {
    @Accessor("currentConductance") double wwpg$currentConductance();
    @Accessor("currentConductance") void wwpg$currentConductance(double value);
}
