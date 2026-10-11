package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.PerformanceCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WorldNetworks.class, remap = false)
public interface WorldNetworksAccessor {
    @Accessor("perf") PerformanceCounter wwpg$performance();
}
