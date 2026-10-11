package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice.PhaseWindingProperties;
import com.george_vi.electroenergetics.content.rotor.VirtualRotor;
import com.google.common.util.concurrent.AtomicDouble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.concurrent.atomic.AtomicInteger;

/** Numerical history only; used to reject a shared electrical frame safely. */
@Mixin(value = PhaseWindingProperties.class, remap = false)
public interface CeePhaseHistoryAccessor {
    @Accessor("storedEnergy") AtomicDouble wwpg$storedEnergy();
    @Accessor("workCounter") AtomicInteger wwpg$workCounter();
    @Accessor("virtualRotor") VirtualRotor wwpg$virtualRotor();
}
