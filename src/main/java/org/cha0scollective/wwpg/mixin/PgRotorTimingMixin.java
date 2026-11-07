package org.cha0scollective.wwpg.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.cha0scollective.wwpg.bridge.PgGeneratorTiming;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorBehaviour;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.objectweb.asm.Opcodes;
import java.util.function.Consumer;

/** Retain PG's normal block tick; only an already-integrated physics section is skipped. */
@Mixin(value=RotorBehaviour.class,remap=false)
public abstract class PgRotorTimingMixin implements PgGeneratorTiming.Advanced {
    @Unique private int wwpg$advancedTick=Integer.MIN_VALUE;
    public int wwpg$advancedTick(){return wwpg$advancedTick;}
    public void wwpg$advancedTick(int tick){wwpg$advancedTick=tick;}
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lorg/patryk3211/powergrid/kinetics/generator/rotor/RotorBehaviour;angularVelocity:F",opcode=Opcodes.PUTFIELD,ordinal=0))
    private void wwpg$oneSpeedAdvance(RotorBehaviour rotor,float value,Operation<Void> original){if(!PgGeneratorTiming.advanced(rotor))original.call(rotor,value);}
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lorg/patryk3211/powergrid/kinetics/generator/rotor/RotorBehaviour;angularVelocity:F",opcode=Opcodes.PUTFIELD,ordinal=1))
    private void wwpg$oneLowSpeedCutoff(RotorBehaviour rotor,float value,Operation<Void> original){if(!PgGeneratorTiming.advanced(rotor))original.call(rotor,value);}
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lorg/patryk3211/powergrid/kinetics/generator/rotor/RotorBehaviour;totalForce:F",opcode=Opcodes.PUTFIELD))
    private void wwpg$keepNextTickForces(RotorBehaviour rotor,float value,Operation<Void> original){if(!PgGeneratorTiming.advanced(rotor))original.call(rotor,value);}
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lorg/patryk3211/powergrid/kinetics/generator/rotor/RotorBehaviour;prevForce:F",opcode=Opcodes.PUTFIELD))
    private void wwpg$keepAcceptedForce(RotorBehaviour rotor,float value,Operation<Void> original){if(!PgGeneratorTiming.advanced(rotor))original.call(rotor,value);}
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lorg/patryk3211/powergrid/kinetics/generator/rotor/RotorBehaviour;forEachSegment(Ljava/util/function/Consumer;)V"))
    private void wwpg$oneClutchAdvance(RotorBehaviour rotor,Consumer<RotorBehaviour> action,Operation<Void> original){if(!PgGeneratorTiming.advanced(rotor))original.call(rotor,action);}
}
