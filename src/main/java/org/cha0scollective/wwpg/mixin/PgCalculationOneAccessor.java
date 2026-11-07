package org.cha0scollective.wwpg.mixin;
import org.patryk3211.powergrid.electricity.sim.calculation.Precalculated1;
import org.patryk3211.powergrid.electricity.sim.calculation.IStamped;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Precalculated1.class,remap=false)
public interface PgCalculationOneAccessor {
    @Accessor("dependency") IStamped wwpg$dependency();
}
