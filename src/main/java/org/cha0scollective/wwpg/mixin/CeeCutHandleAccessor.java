package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WireSimulationState.WireCutHandle.class, remap = false)
public interface CeeCutHandleAccessor {
    @Accessor("id") int wwpg$id();
}
