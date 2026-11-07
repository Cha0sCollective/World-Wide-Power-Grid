package org.cha0scollective.wwpg.mixin;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=RotorBehaviour.class,remap=false)
public interface PgRotorAccess {
    @Accessor("angularVelocity") void wwpg$speed(float value);
    @Accessor("totalForce") float wwpg$force();
    @Accessor("forceSupplier") RotorBehaviour.IForceSource wwpg$drive();
}
