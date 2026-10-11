package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Internal API parameter edits preserve PG's native nonlinear iteration state. */
@Mixin(value = PNJunctionWire.class, remap = false)
public interface PnJunctionParametersAccessor {
    @Mutable @Accessor("reverseSaturationCurrent")
    void wwpg$setSaturationCurrent(double value);

    @Mutable @Accessor("seriesResistance")
    void wwpg$setSeriesResistance(double value);
}
