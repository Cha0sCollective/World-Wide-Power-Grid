package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CircuitBuilder.class, remap = false)
public interface CircuitBuilderAccessor {
    @Accessor("defaultZeroPotentials") Int2IntMap wwpg$groundPriorities();
}
