package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.gauge.ElectricGaugeMovementBehaviour;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.createmod.catnip.render.SuperByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = ElectricGaugeMovementBehaviour.class, remap = false)
abstract class CeeTrainGaugeRenderMixin {
    @WrapWithCondition(method = "renderInContraption",
        slice = @Slice(from = @At(value = "FIELD", target = "Lcom/simibubi/create/AllPartialModels;GAUGE_DIAL:Ldev/engine_room/flywheel/lib/model/baked/PartialModel;")),
        at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/render/SuperByteBuffer;renderInto(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V", ordinal = 0))
    private boolean wwpg$showNeedleWhenAvailable(SuperByteBuffer dial, PoseStack pose, VertexConsumer vertices,
                                                MovementContext context) {
        return Float.isFinite(context.data.getFloat("DialTarget"))
            && Float.isFinite(context.data.getFloat("PrevDialState"))
            && Float.isFinite(context.data.getFloat("DialState"));
    }
}
