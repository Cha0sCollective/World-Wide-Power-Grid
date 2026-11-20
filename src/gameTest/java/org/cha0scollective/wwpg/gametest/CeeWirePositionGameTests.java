package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.collections.ModdedBlocks;

/** Exercises the applied node mixin, with distinct native logical/render transforms. */
@GameTestHolder("wwpg_wire_positions")
@PrefixGameTestTemplate(false)
public final class CeeWirePositionGameTests {
    @GameTest(template="empty",timeoutTicks=40)
    public static void pgWireEndpointsUseNativeLogicalAndInterpolatedCoordinates(GameTestHelper h) {
        var relative=new BlockPos(2,2,2);
        h.setBlock(relative.below(),net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(relative,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(5,()->{
            var pos=h.absolutePos(relative);
            var local=Terminals.pgLocalPosition(h.getLevel(),pos,0);
            h.assertTrue(local!=null,"PG terminal geometry unavailable");
            for(double angle:new double[]{0,.6,Math.PI/2}) for(double scale:new double[]{.5,1,2}) {
                var node=new TransformedNode(pos,angle,scale);
                var logical=node.getPosition(h.getLevel());
                same(h,logical,node.project(local,false),"Wire interaction kept storage coordinates");
                h.assertTrue(node.logicalCalls==1&&node.renderCalls==0,"Logical conversion not delegated exactly once");
                float partial=.35f;
                var rendered=node.getPosition(h.getLevel(),partial);
                same(h,rendered,node.project(local,true).lerp(node.project(local,false),partial),
                        "Wire renderer lost the interpolated native conversion");
                h.assertTrue(node.logicalCalls==1&&node.renderCalls==1&&node.partial==partial,
                        "Render conversion lost its overload or partial tick");
                same(h,node.getLocalPosition(h.getLevel()),local,"Preview geometry was transformed prematurely");
                same(h,node.toGlobalPosNoSable(local,h.getLevel()),local.add(pos.getX(),pos.getY(),pos.getZ()),
                        "Create outline coordinates must remain in the storage frame");
                // A new pose must affect both paths immediately, without retained membership/geometry.
                node.shift=new Vec3(-8,3,12);
                same(h,node.getPosition(h.getLevel()),node.project(local,false),"Moving endpoint retained an old pose");
                same(h,node.getPosition(h.getLevel(),1),node.project(local,false),"Render endpoint retained an old pose");
            }
            h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void stationaryPgAndNativeCeeGeometryKeepTheirNativePaths(GameTestHelper h) {
        var pg=new BlockPos(2,2,2);var cee=new BlockPos(5,2,2);
        for(var p:new BlockPos[]{pg,cee}) h.setBlock(p.below(),net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(pg,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(cee,CEEBlocks.CAPACITOR.get());
        h.runAtTickTime(5,()->{
            var level=h.getLevel();var pos=h.absolutePos(pg);var local=Terminals.pgLocalPosition(level,pos,0);
            var node=new InWorldNode(0,pos);
            var expected=local.add(pos.getX(),pos.getY(),pos.getZ());
            same(h,node.getPosition(level),expected,"Stationary PG logical geometry changed");
            same(h,node.getPosition(level,.35f),expected,"Stationary PG render geometry changed");
            var nativeNode=new TransformedNode(h.absolutePos(cee),.6,1);
            var nativeLocal=nativeNode.getLocalPosition(level);
            h.assertTrue(nativeLocal!=null,"Native CEE geometry unavailable");
            same(h,nativeNode.getPosition(level),nativeNode.project(nativeLocal,false),"Native CEE logical path changed");
            same(h,nativeNode.getPosition(level,.35f),nativeNode.project(nativeLocal,true).lerp(nativeNode.project(nativeLocal,false),.35f),
                    "Native CEE render path changed");
            h.assertTrue(nativeNode.logicalCalls==1&&nativeNode.renderCalls==1,"Native CEE conversion was intercepted twice");
            h.succeed();
        });
    }

    private static void same(GameTestHelper h,Vec3 actual,Vec3 expected,String message) {
        h.assertTrue(actual!=null&&actual.distanceToSqr(expected)<1e-16,message+": "+actual+" expected "+expected);
    }

    /** Models CEE's conversion contract without replacing Sable's process-wide companion singleton. */
    private static final class TransformedNode extends InWorldNode {
        private final double angle,scale;
        private Vec3 shift=Vec3.ZERO;
        private int logicalCalls,renderCalls;
        private float partial;
        TransformedNode(BlockPos pos,double angle,double scale) {super(0,pos);this.angle=angle;this.scale=scale;}
        Vec3 project(Vec3 local,boolean previous) {
            var transformed=new Vec3(local.x*Math.cos(angle)-local.z*Math.sin(angle),local.y,
                    local.x*Math.sin(angle)+local.z*Math.cos(angle)).scale(scale);
            return transformed.add(100,70,-100).add(shift).add(previous?-4:0,0,0);
        }
        @Override public Vec3 toGlobalPos(Vec3 local,Level level) {logicalCalls++;return project(local,false);}
        @Override public Vec3 toGlobalPos(Vec3 local,Level level,float partialTicks) {
            renderCalls++;partial=partialTicks;return project(local,true).lerp(project(local,false),partialTicks);
        }
    }
}
