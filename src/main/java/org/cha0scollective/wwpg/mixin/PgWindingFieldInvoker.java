package org.cha0scollective.wwpg.mixin;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=WindingBlockEntity.class,remap=false)
public interface PgWindingFieldInvoker {
    @Invoker("fieldStrength") static double wwpg$field(double current) { throw new AssertionError(); }
}
