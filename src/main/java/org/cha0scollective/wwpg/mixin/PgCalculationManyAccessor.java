package org.cha0scollective.wwpg.mixin;
import org.patryk3211.powergrid.electricity.sim.calculation.PrecalculatedN;
import org.patryk3211.powergrid.electricity.sim.calculation.IStamped;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=PrecalculatedN.class,remap=false)
public interface PgCalculationManyAccessor {
    @Accessor("dependencies") IStamped[] wwpg$dependencies();
}
