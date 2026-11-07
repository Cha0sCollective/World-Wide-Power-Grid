package org.cha0scollective.wwpg.mixin;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.InductionRotorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=InductionRotorBlockEntity.class,remap=false)
public interface PgRotorFieldAccessor {
    @Accessor("fieldMultiplier") float wwpg$multiplier();
}
