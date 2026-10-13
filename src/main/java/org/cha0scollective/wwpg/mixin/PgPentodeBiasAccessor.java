package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.special.PentodeWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=PentodeWire.class,remap=false)
public interface PgPentodeBiasAccessor {
    @Accessor("screen") IElectricNode wwpg$screen();
}
