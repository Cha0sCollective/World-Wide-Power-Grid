package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ElectricalNetwork.class, remap = false)
public interface ElectricalNetworkAccessor {
    @Accessor("mna") IMNA wwpg$solver();
    @Accessor("groundReferenceCount") int wwpg$groundCount();
    @Accessor("conductanceUpdates") int wwpg$conductanceUpdates();
}
