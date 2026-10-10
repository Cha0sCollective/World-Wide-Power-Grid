package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CRSeriesWire.class, remap = false)
public interface CapacitorHistoryAccessor {
    /** State used by the next substep, including the last committed leakage. */
    @Accessor("V") double wwpg$storedVoltage();
}
