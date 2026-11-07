package org.cha0scollective.wwpg.mixin;
import org.patryk3211.powergrid.electricity.sim.calculation.Precalculated;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Precalculated.class,remap=false)
public interface PgCalculationAccessor {
    @Accessor("value") Object wwpg$value();
}
