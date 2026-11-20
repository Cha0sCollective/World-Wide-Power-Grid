package org.cha0scollective.wwpg.compat.cee12.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.Position;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** CEE's hovered-node lookup only understands native CEE blocks, unlike its selected-node lookup. */
@Mixin(targets="com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour",remap=false)
abstract class WirePreviewClientMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",ordinal=1,
            target="Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;toGlobalPosNoSable(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/phys/Vec3;"),require=1)
    private static Vec3 wwpg$hoveredTerminalPosition(InWorldNode node,Vec3 local,Level level,Operation<Vec3> original) {
        // Use WWPG's existing PG/extension geometry. Leave detached-node cache lookup to CEE.
        if(local==null&&!DetachedNodeHelper.isDetached(node)) local=node.getLocalPosition(level);
        // Keep plot coordinates: Sable handles the preview transform and the range check.
        return original.call(node,local,level);
    }

    @WrapOperation(method="tick",at=@At(value="INVOKE",
            target="Ldev/ryanhcode/sable/companion/SableCompanion;distanceSquaredWithSubLevels(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/core/Position;)D"),require=1)
    private static double wwpg$skipUnavailablePosition(SableCompanion companion,Level level,Position from,Position to,
            Operation<Double> original) {
        // CEE 1.2 checks range before checking for an unavailable position. Let its normal cleanup run.
        if(from==null||to==null) return Double.POSITIVE_INFINITY;
        return original.call(companion,level,from,to);
    }
}
