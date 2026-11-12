package org.cha0scollective.wwpg.benchmark.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import org.cha0scollective.wwpg.benchmark.NativeWork;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe actual JNI arguments and callbacks; every original executes once. */
@Mixin(value = NativeMNA.class, remap = false)
public abstract class NativeWorkMixin {
    @WrapOperation(method = "singleTick()V", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/sim/solver/NativeMNA;singleTick(JII)Ljava/nio/ByteBuffer;"), require = 1)
    private ByteBuffer nativeCall(NativeMNA owner, long pointer, int iterations, int commands,
                                  Operation<ByteBuffer> original) {
        var frame = NativeWork.begin(owner, iterations, commands);
        boolean returned = false;
        ByteBuffer result = null;
        try {
            result = original.call(owner, pointer, iterations, commands);
            returned = true;
            return result;
        } finally {
            NativeWork.end(frame, returned, result);
        }
    }

    @WrapMethod(method = "runIterHooks(ILjava/nio/ByteBuffer;)I")
    private int iteration(int iteration, ByteBuffer state, Operation<Integer> original) {
        int commands = original.call(iteration, state);
        NativeWork.iteration(this, iteration, commands);
        return commands;
    }

    @WrapMethod(method = "runAddResidual(Ljava/nio/ByteBuffer;)V")
    private void residual(ByteBuffer values, Operation<Void> original) {
        original.call(values);
        NativeWork.residual(this);
    }
}
