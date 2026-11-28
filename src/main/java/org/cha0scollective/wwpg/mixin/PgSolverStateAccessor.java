package org.cha0scollective.wwpg.mixin;

import org.patryk3211.powergrid.electricity.sim.solver.JavaMNA;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Both pinned backends expose the same acceptance and warm-up state. */
@Mixin(value = {JavaMNA.class, NativeMNA.class}, remap = false)
public interface PgSolverStateAccessor {
    @Accessor("warmUpTicks") int wwpg$warmUpTicks();
    @Accessor("converged") void wwpg$setConverged(boolean converged);
}
