package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CEEPanelAttachmentTypes;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.cha0scollective.wwpg.compat.sable.SableTerminalPicking;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.collections.ModdedDataComponents;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in public Companion API fixtures; ordinary test launches need no Sable classes. */
@GameTestHolder("wwpg_sable_targeting")
@PrefixGameTestTemplate(false)
public final class SableTerminalPickingGameTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void translatedRotatedScaledAndInterpolatedPins(GameTestHelper h) {Checks.pins(h);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void sublevelPanelsRetainCoversReachOcclusionAndWireIds(GameTestHelper h) {Checks.panels(h);}
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeOwnersIndependentFramesAndLiveReplacement(GameTestHelper h) {Checks.owners(h);}

    // Kept behind the selected test methods so discovery is safe without the optional API.
    private static final class Checks {
        static void pins(GameTestHelper h) {
            try(var fixture=new Fixture(h,0)) {
                fixture.block(CEEBlocks.CAPACITOR.get().defaultBlockState());
                var point=fixture.pin(0);var from=point.add(0,2,0);var to=point.add(0,-2,0);
                for(double angle:new double[]{0,Math.PI/2,Math.PI/4}) for(double scale:new double[]{.5,1,2}) {
                    var pose=fixture.pose(angle,scale);var frame=new Frame(fixture.pos,pose);
                    for(boolean storageEye:new boolean[]{false,true}) {
                        var worldEye=pose.transformPosition(from);var worldTarget=pose.transformPosition(point);
                        aim(fixture.player,storageEye?from:worldEye,storageEye?point:worldTarget);
                        var api=companion(storageEye?from:worldEye,List.of(frame));
                        var hit=rayHit(fixture.player,storageEye?from:worldEye,4*scale,api,pose,fixture.pos,point,Direction.UP);
                        var result=SableTerminalPicking.pick(fixture.player,4*scale,1,hit,api);
                        h.assertTrue(result!=hit,"CEE pin was not refined from the native hit");
                        fixture.selected(result,0,"Translated/rotated/scaled owner or pin changed");
                        if(!storageEye) {
                            var miss=BlockHitResult.miss(pose.transformPosition(to),Direction.DOWN,
                                    BlockPos.containing(pose.transformPosition(to)));
                            fixture.selected(SableTerminalPicking.pick(fixture.player,4*scale,1,miss,api),0,
                                    "Native body miss lost an overhanging sublevel pin");
                        }
                    }
                }
                var rendered=fixture.pose(.6,1);var frame=new ClientFrame(fixture.pos,fixture.pose(0,1),rendered);
                var eye=rendered.transformPosition(from);aim(fixture.player,eye,rendered.transformPosition(point));
                var clientApi=companion(eye,List.of(frame));
                var clientHit=rayHit(fixture.player,eye,4,clientApi,rendered,fixture.pos,point,Direction.UP);
                var clientResult=SableTerminalPicking.pick(fixture.player,4,.35f,clientHit,clientApi);
                h.assertTrue(clientResult!=clientHit,"Render-pose check retained the unrefined hit");
                fixture.selected(clientResult,0,
                        "Client selection used logical rather than interpolated render pose");
                h.assertTrue(frame.partial==.35f,"Partial-tick render pose was not requested");
            }
            h.succeed();
        }

        static void panels(GameTestHelper h) {
            try(var fixture=new Fixture(h,1)) {
                fixture.block(CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(
                        ElectricalPanelBlock.FACING,Direction.NORTH));
                var gauge=PanelGameTests.insert(h,fixture.pos,
                        CEEPanelAttachmentTypes.VOLTMETER.get(),
                        ElectricalPanelSlot.THIRD_CENTERED);
                int id=gauge.nodes[0].id();var point=fixture.pin(id);var pose=fixture.pose(.8,1);
                var from=point.add(0,0,-2);var eye=pose.transformPosition(from);
                aim(fixture.player,eye,pose.transformPosition(point));
                var api=companion(eye,List.of(new Frame(fixture.pos,pose)));
                var front=new Vec3(point.x,point.y,fixture.pos.getZ());
                var original=rayHit(fixture.player,eye,4,api,pose,fixture.pos,front,Direction.NORTH);
                var selected=SableTerminalPicking.pick(fixture.player,4,1,original,api);
                fixture.selected(selected,id,"Recessed sublevel panel pin was not exposed");
                var end=eye.add(fixture.player.getViewVector(1).scale(1));
                var shortMiss=BlockHitResult.miss(end,Direction.UP,BlockPos.containing(end));
                h.assertTrue(SableTerminalPicking.pick(fixture.player,1,1,shortMiss,api)==shortMiss,
                        "Sublevel picking extended physical reach");
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
                fixture.player.setItemInHand(InteractionHand.OFF_HAND,ModdedItems.GOLDEN_WIRE.asStack());
                h.assertTrue(SableTerminalPicking.pick(fixture.player,4,1,original,api)==original,
                        "Offhand wire picked through a closed panel cover");
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.MULTIMETER.asStack());
                fixture.selected(SableTerminalPicking.pick(fixture.player,4,1,original,api),id,
                        "PG multimeter could not select the exposed panel pin");
                var obstruction=eye.add(fixture.player.getViewVector(1));
                var blocked=nativeHit(BlockPos.containing(obstruction),obstruction,Direction.NORTH);
                h.assertTrue(SableTerminalPicking.pick(fixture.player,4,1,blocked,api)==blocked,
                        "A different frame's obstruction was bypassed");
                // Dispatch the returned storage-space hit through PG's actual wire item.
                // Position the mock player in that same frame for vanilla server-side re-picking.
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.GOLDEN_WIRE.asStack());
                aim(fixture.player,from,point);
                var hit=(BlockHitResult)selected;
                WireItem.useOn(fixture.player,InteractionHand.MAIN_HAND,hit.getBlockPos(),hit.getDirection());
                var connection=fixture.player.getMainHandItem().get(ModdedDataComponents.CONNECTION_DATA.get());
                h.assertTrue(connection!=null&&new BlockWireEndpoint(fixture.pos,id).equals(connection.endpoint()),
                        "Native PG wire dispatch changed the storage owner or terminal ID");
            }
            h.succeed();
        }

        static void owners(GameTestHelper h) {
            try(var a=new Fixture(h,2);var b=new Fixture(h,3)) {
                a.block(CEEBlocks.CAPACITOR.get().defaultBlockState());
                b.block(CEEBlocks.CAPACITOR.get().defaultBlockState());
                var point=a.pin(0);var pose=a.pose(.4,1);var eye=pose.transformPosition(point.add(0,2,0));
                aim(a.player,eye,pose.transformPosition(point));
                var first=new Frame(a.pos,pose);
                var secondPose=new Pose3d(pose);
                secondPose.rotationPoint().set(b.pos.getX(),b.pos.getY(),b.pos.getZ());
                secondPose.position().add(pose.transformNormal(new Vec3(0,.5,0)).x,
                        pose.transformNormal(new Vec3(0,.5,0)).y,pose.transformNormal(new Vec3(0,.5,0)).z);
                var second=new Frame(b.pos,secondPose);var frames=new ArrayList<Frame>(List.of(first,second));
                var api=companion(eye,frames);var original=rayHit(a.player,eye,4,api,pose,a.pos,point,Direction.UP);
                b.selected(SableTerminalPicking.pick(a.player,4,1,original,api),0,
                        "Closer pin in an independent frame was not selected");
                frames.remove(second);
                a.selected(SableTerminalPicking.pick(a.player,4,1,original,api),0,
                        "Removed sublevel membership remained cached");
                a.block(Blocks.AIR.defaultBlockState());
                h.assertTrue(SableTerminalPicking.pick(a.player,4,1,original,api)==original,
                        "Removed CEE block retained a selectable pin");
                a.block(CEEBlocks.CAPACITOR.get().defaultBlockState());
                a.selected(SableTerminalPicking.pick(a.player,4,1,original,api),0,
                        "Replaced CEE block did not recover pin selection");
                a.block(ModdedBlocks.CREATIVE_RESISTOR.get().defaultBlockState());
                h.assertTrue(SableTerminalPicking.pick(a.player,4,1,original,api)==original,
                        "Native PG owner's original hit identity was replaced");
            }
            h.succeed();
        }

        private static class Frame implements SubLevelAccess {
            final BlockPos origin;
            final Pose3d pose;
            Frame(BlockPos origin,Pose3d pose){this.origin=origin;this.pose=pose;}
            boolean contains(double x,double z){return Math.abs(x-origin.getX())<12&&Math.abs(z-origin.getZ())<12;}
            public Pose3dc logicalPose(){return pose;}
            public Pose3dc lastPose(){return pose;}
            public BoundingBox3dc boundingBox(){return new BoundingBox3d();}
            public UUID getUniqueId(){return new UUID(origin.asLong(),0);}
            public String getName(){return "focused fixture";}
        }
        private static final class ClientFrame extends Frame implements ClientSubLevelAccess {
            final Pose3d rendered;float partial=-1;
            ClientFrame(BlockPos p,Pose3d logical,Pose3d rendered){super(p,logical);this.rendered=rendered;}
            public Pose3dc renderPose(){return rendered;}
            public Pose3dc renderPose(float tick){partial=tick;return rendered;}
        }
        private static SableCompanion companion(Vec3 eye,List<? extends Frame> frames) {
            return (SableCompanion)Proxy.newProxyInstance(
                    Checks.class.getClassLoader(),new Class<?>[]{SableCompanion.class},(proxy,method,args)->{
                switch(method.getName()) {
                    case "getEyePositionInterpolated":return eye;
                    case "getAllIntersecting":return frames;
                    case "getContaining": {
                        var p=(Vec3i)args[1];
                        return frames.stream().filter(f->f.contains(p.getX(),p.getZ())).findFirst().orElse(null);
                    }
                    case "projectOutOfSubLevel": {
                        var p=(Vec3)args[1];
                        var frame=frames.stream().filter(f->f.contains(p.x,p.z)).findFirst().orElse(null);
                        return frame==null?p:frame.logicalPose().transformPosition(p);
                    }
                    default:throw new AssertionError("Unexpected Companion operation: "+method);
                }
            });
        }
        private static final class Fixture implements AutoCloseable {
            final GameTestHelper h;final BlockPos pos;final Player player;
            Fixture(GameTestHelper h,int slot){
                this.h=h;pos=new BlockPos(2000003,80,2000003+slot*32);player=h.makeMockPlayer(GameType.SURVIVAL);
                var chunk=new ChunkPos(pos);h.getLevel().setChunkForced(chunk.x,chunk.z,true);h.getLevel().getChunkAt(pos);
                h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.GOLDEN_WIRE.asStack());
            }
            void block(BlockState state){h.getLevel().setBlockAndUpdate(pos,state);}
            Vec3 pin(int id){
                var terminal=Terminals.cee(h.getLevel(),pos).terminal(h.getLevel().getBlockState(pos),id);
                h.assertTrue(terminal!=null,"Fixture native pin is absent: "+id);
                return terminal.getOrigin().add(pos.getX(),pos.getY(),pos.getZ());
            }
            Pose3d pose(double angle,double scale){
                var world=Vec3.atCenterOf(h.absolutePos(new BlockPos(4,3,4)));
                return new Pose3d(new Vector3d(world.x,world.y,world.z),
                        new Quaterniond().rotateY(angle).rotateZ(angle*.5),new Vector3d(pos.getX(),pos.getY(),pos.getZ()),new Vector3d(scale));
            }
            void selected(HitResult result,int id,String message){
                h.assertTrue(result instanceof BlockHitResult hit&&result.getType()==HitResult.Type.BLOCK
                        &&hit.getBlockPos().equals(pos)&&Terminals.cee(h.getLevel(),pos).terminalIndexAt(
                                h.getLevel().getBlockState(pos),hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ()))==id,message+": "+result.getLocation());
            }
            public void close(){
                h.getLevel().removeBlock(pos,false);h.getLevel().removeBlock(pos.below(),false);
                var chunk=new ChunkPos(pos);h.getLevel().setChunkForced(chunk.x,chunk.z,false);
            }
        }
        private static BlockHitResult nativeHit(BlockPos pos,Vec3 point,Direction side){
            return new BlockHitResult(point,side,pos,false);
        }
        private static BlockHitResult rayHit(Player player,Vec3 rawEye,double reach,SableCompanion api,
                Pose3dc pose,BlockPos pos,Vec3 near,Direction side) {
            // Native clipping uses Minecraft's actual float/trig-table view ray.
            // A hand-constructed exact-angle hit need not lie on that ray.
            var from=api.projectOutOfSubLevel(player.level(),rawEye);
            var to=api.projectOutOfSubLevel(player.level(),rawEye.add(player.getViewVector(1).scale(reach)));
            var direction=to.subtract(from);
            double along=pose.transformPosition(near).subtract(from).dot(direction)/direction.lengthSqr();
            return nativeHit(pos,pose.transformPositionInverse(from.add(direction.scale(along))),side);
        }
        private static void aim(Player player,Vec3 eye,Vec3 target){
            var d=target.subtract(eye).normalize();player.setPos(eye.x,eye.y-player.getEyeHeight(),eye.z);
            player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
            player.setYHeadRot(player.getYRot());player.yHeadRotO=player.getYRot();player.setOldPosAndRot();
        }
    }
}
