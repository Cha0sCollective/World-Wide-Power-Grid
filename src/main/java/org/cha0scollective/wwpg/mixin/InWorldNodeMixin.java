package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InWorldNode.class, remap = false)
public abstract class InWorldNodeMixin {
    @Inject(method = "closestNode(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;FLnet/minecraft/world/phys/Vec3;)Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;", at = @At("HEAD"), cancellable = true)
    private static void wwpg$pgSelection(Level level, BlockPos pos, BlockState state, float distance, Vec3 clicked, CallbackInfoReturnable<InWorldNode> cir) {
        if (Terminals.nativePg(level, pos) != null) cir.setReturnValue(Terminals.closestPg(level, pos, clicked, distance));
    }
    @Inject(method = "getPosition(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void wwpg$pgPosition(Level level, CallbackInfoReturnable<Vec3> cir) {
        var node = (InWorldNode) (Object) this;
        var p = Terminals.pgLocalPosition(level, node.sourcePos(), node.id());
        if (p != null) cir.setReturnValue(p.add(node.sourcePos().getX(), node.sourcePos().getY(), node.sourcePos().getZ()));
    }
    @Inject(method = "getPosition(Lnet/minecraft/world/level/Level;F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void wwpg$pgInterpolatedPosition(Level level, float partialTicks, CallbackInfoReturnable<Vec3> cir) { wwpg$pgPosition(level, cir); }
    @Inject(method = "getLocalPosition", at = @At("HEAD"), cancellable = true)
    private void wwpg$pgLocalPosition(Level level, CallbackInfoReturnable<Vec3> cir) {
        var node = (InWorldNode) (Object) this;
        var p = Terminals.pgLocalPosition(level, node.sourcePos(), node.id());
        if (p != null) cir.setReturnValue(p);
    }
}
