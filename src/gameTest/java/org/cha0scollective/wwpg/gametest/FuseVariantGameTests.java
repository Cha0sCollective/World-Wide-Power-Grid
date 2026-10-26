package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.fuse.FuseDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class FuseVariantGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,2,1),FUSE=new BlockPos(4,2,1),LOAD=new BlockPos(7,2,1);
    @GameTest(template="empty",timeoutTicks=260)
    public static void nativeFuseFailureAndRepeatedRepairRetainBothWireSystems(GameTestHelper h) {
        for(var p:new BlockPos[]{SOURCE,FUSE,LOAD}) h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(SOURCE,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(FUSE,CEEBlocks.FUSE.get());h.setBlock(LOAD,ModdedBlocks.CREATIVE_RESISTOR.get());
        var expected=new HashSet<WorldNetworks.PartId>();
        h.runAtTickTime(5,()->{
            ShowroomTools.pgSource(h,h.absolutePos(SOURCE),20);ShowroomTools.resistance(h,h.absolutePos(LOAD),1000);fuse(h).setAmperage=1;
            WiringGameTests.connect(h,h.absolutePos(SOURCE),0,h.absolutePos(FUSE),0,true);
            WiringGameTests.connect(h,h.absolutePos(FUSE),1,h.absolutePos(LOAD),0,false);
            WiringGameTests.connect(h,h.absolutePos(SOURCE),1,h.absolutePos(LOAD),1,true);
        });
        h.runAtTickTime(25,()->{
            expected.addAll(owners(h));h.assertTrue(expected.size()==1,"Fuse fixture must have one PG fuse wire");check(h,20);ShowroomTools.resistance(h,h.absolutePos(LOAD),1);
        });
        h.runAtTickTime(60,()->{
            h.assertTrue(fuse(h).isBroken&&CEEBlocks.BROKEN_FUSE.has(h.getBlockState(FUSE)),"Solved overload did not create the native broken fuse");check(h,0);retain(h,expected,"First failure");
            ShowroomTools.resistance(h,h.absolutePos(LOAD),1000);repair(h);
        });
        h.runAtTickTime(90,()->{check(h,20);retain(h,expected,"First repair");ShowroomTools.resistance(h,h.absolutePos(LOAD),1);});
        h.runAtTickTime(130,()->{h.assertTrue(fuse(h).isBroken,"Second overload did not trip");retain(h,expected,"Second failure");ShowroomTools.resistance(h,h.absolutePos(LOAD),1000);repair(h);});
        h.runAtTickTime(160,()->{check(h,20);retain(h,expected,"Second repair");h.setBlock(FUSE,Blocks.AIR);});
        h.runAtTickTime(185,()->{
            check(h,0);h.assertTrue(owners(h).isEmpty(),"Actual fuse removal retained a PG wire");
            h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnections(new InWorldNode(1,h.absolutePos(FUSE))).isEmpty(),"Actual fuse removal retained a CEE wire");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    private static FuseDevice fuse(GameTestHelper h) {return DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(FUSE),FuseDevice.class);}
    private static Set<WorldNetworks.PartId> owners(GameTestHelper h) {
        var ids=new HashSet<WorldNetworks.PartId>();
        var wires=GlobalElectricNetworks.getWorldNetworks(h.getLevel()).findConnectedWires(new BlockWireEndpoint(h.absolutePos(FUSE),0));
        if(wires!=null)for(var wire:wires)ids.add(wire.persistentOwnerId);
        return ids;
    }
    private static void retain(GameTestHelper h,Set<WorldNetworks.PartId> expected,String phase) {
        h.assertTrue(expected.equals(owners(h)),phase+" removed or replaced the native PG wire identity");
        h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnections(new InWorldNode(1,h.absolutePos(FUSE))).size()==1,phase+" removed or duplicated the CEE wire");
        h.assertTrue(fuse(h).setAmperage==1,phase+" changed the native rating");
    }
    private static void check(GameTestHelper h,double expected) {ShowroomTools.near(h,ShowroomTools.volts(h,h.absolutePos(LOAD),0,1),expected,.03,"Native fuse load");}
    private static void repair(GameTestHelper h) {
        var item=CEEItems.COPPER_WIRE.asStack();var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,item);var pos=h.absolutePos(FUSE);
        h.assertTrue(h.getBlockState(FUSE).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)).consumesAction()&&item.isEmpty(),"Native fuse repair did not consume copper wire");
    }
}
