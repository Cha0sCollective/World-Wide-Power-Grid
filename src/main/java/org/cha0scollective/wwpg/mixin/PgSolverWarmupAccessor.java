package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.solver.JavaMNA;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Both pinned backends decrement this counter only after numerical convergence. */
@Mixin(value = {JavaMNA.class, NativeMNA.class}, remap = false)
public interface PgSolverWarmupAccessor {
    @Accessor("warmUpTicks") int wwpg$warmUpTicks();
}
