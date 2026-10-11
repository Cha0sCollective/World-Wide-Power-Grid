package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.rotor.VirtualRotor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VirtualRotor.class, remap = false)
public interface CeeRotorHistoryAccessor {
    @Accessor("torqueAccumulated") double wwpg$torqueAccumulated();
    @Accessor("torqueAccumulated") void wwpg$torqueAccumulated(double value);
    @Accessor("angleError") double wwpg$angleError();
    @Accessor("angleError") void wwpg$angleError(double value);
}
