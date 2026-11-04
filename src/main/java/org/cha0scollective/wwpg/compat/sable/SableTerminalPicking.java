package org.cha0scollective.wwpg.compat.sable;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.cha0scollective.wwpg.wiring.CeeTerminalPicking;

import java.util.LinkedHashSet;

/** Loaded only with Sable. Native hit identities stay in their owner's storage coordinates. */
public final class SableTerminalPicking {
    private SableTerminalPicking() {}

    public static HitResult pick(Player player,double reach,float partialTick,HitResult original) {
        return pick(player,reach,partialTick,original,SableCompanion.INSTANCE);
    }

    /** Internal explicit-companion entry point also permits deterministic pose/lifecycle fixtures. */
    public static HitResult pick(Player player,double reach,float partialTick,HitResult original,SableCompanion companion) {
        if (original.getType()==HitResult.Type.ENTITY || !(reach>0) || !Double.isFinite(reach)) return original;
        var level=player.level();
        // Match Sable's native Entity.pick, including interpolated eyes on moving assemblies.
        var from=companion.getEyePositionInterpolated(player,partialTick);
        var to=from.add(player.getViewVector(partialTick).scale(reach));
        from=companion.projectOutOfSubLevel(level,from);
        to=companion.projectOutOfSubLevel(level,to);
        if (!Double.isFinite(from.distanceToSqr(to))) return original;
        var owner=original instanceof BlockHitResult block && original.getType()==HitResult.Type.BLOCK
                ? companion.getContaining(level,block.getBlockPos()) : null;
        var ownerPose=pose(owner,partialTick);
        var limit=original.getType()==HitResult.Type.BLOCK ? global(ownerPose,original.getLocation()) : to;
        if (!CeeTerminalPicking.withinRay(from,to,limit)) return original;

        // Include nearby overhanging pins even if the native ray missed their block body.
        // No world geometry or membership is cached between picks.
        var frames=new LinkedHashSet<SubLevelAccess>();
        frames.add(owner);
        frames.add(null);
        var bounds=new BoundingBox3d(from,limit).expand(1);
        for (var sublevel:companion.getAllIntersecting(level,bounds)) frames.add(sublevel);

        HitResult nearest=original;double distance=Double.POSITIVE_INFINITY;
        for (var frame:frames) {
            var transform=frame==owner ? ownerPose : pose(frame,partialTick);
            var localFrom=local(transform,from);
            // Only the native owner's panel may expose its own recessed pins past its cover.
            var localTo=local(transform,frame==owner ? to : limit);
            HitResult nativeHit=frame==owner ? original : BlockHitResult.miss(localTo,Direction.UP,BlockPos.containing(localTo));
            if (!CeeTerminalPicking.withinRay(localFrom,localTo,localTo)) continue;
            var selected=CeeTerminalPicking.pick(player,localFrom,localTo,nativeHit);
            if (selected==nativeHit || selected.getType()!=HitResult.Type.BLOCK) continue;
            var point=global(transform,selected.getLocation());
            if (!CeeTerminalPicking.withinRay(from,to,point)) continue;
            double candidate=from.distanceToSqr(point);
            if (candidate<distance) {nearest=selected;distance=candidate;}
        }
        return nearest;
    }

    private static Pose3dc pose(SubLevelAccess sublevel,float partialTick) {
        return sublevel instanceof ClientSubLevelAccess client ? client.renderPose(partialTick)
                : sublevel==null ? null : sublevel.logicalPose();
    }
    private static Vec3 local(Pose3dc pose,Vec3 point) {return pose==null ? point : pose.transformPositionInverse(point);}
    private static Vec3 global(Pose3dc pose,Vec3 point) {return pose==null ? point : pose.transformPosition(point);}
}
