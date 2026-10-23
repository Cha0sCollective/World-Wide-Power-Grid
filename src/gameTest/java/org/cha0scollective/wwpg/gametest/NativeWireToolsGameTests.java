package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.detached_nodes.DetachedNodeHelper;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class NativeWireToolsGameTests {
    @GameTest(template="empty",timeoutTicks=85)
    public static void nativeLinemansStickWithOffhandClampMeasuresPgWire(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,3,1));var load=source.east(4);
        for(var p:new BlockPos[]{source,load})h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());h.getLevel().setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        var player=h.makeMockPlayer(GameType.SURVIVAL);var stick=CEEItems.LINEMANS_STICK.asStack();
        h.runAtTickTime(5,()->{ShowroomTools.scroll(h,source,10000);ShowroomTools.resistance(h,load,10);WiringGameTests.connect(h,source,1,load,0,true);WiringGameTests.connect(h,source,0,load,1,false);});
        h.runAtTickTime(25,()->{
            var wire=org.patryk3211.powergrid.electricity.base.IElectric.getAt(h.getLevel(),source).getBehaviour(h.getLevel(),source,h.getLevel().getBlockState(source))
                    .getConnections().get(new BlockWireEndpoint(source,1)).stream().findFirst().orElseThrow();
            player.setItemInHand(InteractionHand.MAIN_HAND,stick);player.setItemInHand(InteractionHand.OFF_HAND,CEEItems.CLAMP_METER.asStack());
            h.assertTrue(wire.interact(player,InteractionHand.MAIN_HAND).consumesAction()&&player.isUsingItem(),"Native lineman's stick with offhand clamp rejected a PG wire");
            stick.getItem().onUseTick(h.getLevel(),player,stick,9000);
            var data=stick.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            h.assertTrue(data.hasUUID(org.cha0scollective.wwpg.equipment.HandheldMeters.PG_WIRE),"Lineman clamp did not retain native PG wire identity");
            BoardComponentGameTests.near(h,data.getFloat(org.cha0scollective.wwpg.equipment.HandheldMeters.CURRENT),Math.abs(wire.measuredCurrent()),.00001,"Lineman clamp solved current");
            h.assertTrue(data.getFloat(org.cha0scollective.wwpg.equipment.HandheldMeters.CURRENT)>.9,"Lineman clamp measured no actual current");
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);stick.getItem().onUseTick(h.getLevel(),player,stick,8999);
            h.assertTrue(!player.isUsingItem()&&!stick.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains(org.cha0scollective.wwpg.equipment.HandheldMeters.PG_WIRE),"Detached offhand clamp retained stale metering");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void nativeHangingInsulatorSpoolRecognizesFreshPgTerminal(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,3,1));var load=source.east(3);
        for(var p:new BlockPos[]{source,load})h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        h.getLevel().setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        var sourceNode=new InWorldNode(0,source);var loadNode=new InWorldNode(0,load);
        var player=h.makeMockPlayer(GameType.SURVIVAL);var hanging=CEEItems.HANGING_GLASS_INSULATION.asStack(2);
        InWorldNode[] loose={null};
        h.runAtTickTime(5,()->{
            ShowroomTools.pgSource(h,source,10);ShowroomTools.resistance(h,load,1000);
            var sd=InfrastructureSavedData.load(h.getLevel());
            h.assertTrue(!sd.hasNode(sourceNode),"Control must begin with a fresh PG terminal");
            player.setItemInHand(InteractionHand.MAIN_HAND,hanging);player.setItemInHand(InteractionHand.OFF_HAND,Items.BLUE_DYE.getDefaultInstance());
            h.assertTrue(hanging.getItem().useOn(context(h,player,sourceNode)).consumesAction(),"Native hanging insulator item rejected a fresh PG terminal");
            var connections=sd.getConnections(sourceNode);
            h.assertTrue(connections.size()==1,"Hanging spool did not create exactly one native wire");
            var connection=connections.iterator().next();loose[0]=connection.node1().equals(sourceNode)?connection.node2():connection.node1();
            var wire=sd.getConnectionData(connection);
            h.assertTrue(DetachedNodeHelper.isDetached(loose[0])&&wire.length==4&&wire.wireType()==CEEWireTypes.COLORED_GLASS_INSULATOR[DyeColor.BLUE.ordinal()].get(),
                    "Native loose endpoint, fixed length or offhand color changed");
            h.assertTrue(hanging.getCount()==1&&player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get())==0,"Native hanging item cost changed");
            var spool=CEEItems.WIRE_SPOOL.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,spool);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            ((WireSpoolItem)spool.getItem()).interactDetachedNode(loose[0],player,h.getLevel(),spool);
            h.assertTrue(spool.getItem().useOn(context(h,player,loadNode)).consumesAction()&&spool.isEmpty(),"Native spool could not continue the loose wire onto a PG terminal");
            WiringGameTests.connect(h,source,1,load,1,true);
        });
        h.runAtTickTime(35,()->{
            var sd=InfrastructureSavedData.load(h.getLevel());
            h.assertTrue(sd.hasNode(loose[0])&&sd.isConnected(sourceNode,loose[0])&&sd.isConnected(loose[0],loadNode),"Loose endpoint or native junction disappeared");
            // A glass insulator is intentionally almost nonconductive in CEE 1.1.3.
            var first=sd.getConnectionData(new InWorldNodeConnection(sourceNode,loose[0]));
            var second=sd.getConnectionData(new InWorldNodeConnection(loose[0],loadNode));
            double expected=10*1000/(1000+first.getResistance()+second.getResistance());
            BoardComponentGameTests.near(h,new BlockWireEndpoint(load,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(load,1).getNode(h.getLevel()).getVoltage(),expected,.00001,"Native hanging insulator electrical equation");
            var empty=CEEItems.EMPTY_SPOOL.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,empty);
            CEEWireInteractionBehaviours.EMPTY_SPOOL.get().interactWire(new NodeConnectionPoint(sourceNode,loose[0],.5f),h.getLevel(),player,empty);
            h.assertTrue(empty.isEmpty()&&!sd.isConnected(sourceNode,loose[0]),"Native loose wire recovery failed");
        });
        h.runAtTickTime(55,()->{
            var sd=InfrastructureSavedData.load(h.getLevel());sd.removeConnectionNoDrops(new InWorldNodeConnection(loose[0],loadNode));sd.removeNode(loose[0]);
            DynamicGameTests.audit(h);h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=90)
    public static void nativeLinemansStickInstallsAndReturnsOffhandAttachments(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,3,1));var load=source.east(8);
        for(var p:new BlockPos[]{source,load})h.getLevel().setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        h.getLevel().setBlockAndUpdate(load,CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
        var connection=new InWorldNodeConnection(new InWorldNode(0,source),new InWorldNode(0,load));
        var player=h.makeMockPlayer(GameType.SURVIVAL);var stick=CEEItems.LINEMANS_STICK.asStack();
        var handler=CEEWireInteractionBehaviours.LINEMANS_STICK.get();Object[] identity={null};
        h.runAtTickTime(5,()->{ShowroomTools.pgSource(h,source,40);WiringGameTests.connect(h,source,0,load,0,false);WiringGameTests.connect(h,source,1,load,1,true);});
        h.runAtTickTime(20,()->{
            identity[0]=org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).branchIdentity(connection.node1(),connection.node2());
            player.setItemInHand(InteractionHand.MAIN_HAND,stick);h.assertTrue(handler.isActiveFor(stick,player),"Native stick handler was not active");
            for(int index=0;index<2;index++) {
                var item=index==0?new ItemStack(Items.RED_BANNER,2):CEEItems.WIRE_DAMPER.asStack(2);
                player.setItemInHand(InteractionHand.OFF_HAND,item);
                handler.interactWire(new NodeConnectionPoint(connection.node1(),connection.node2(),index==0?.25f:.75f),h.getLevel(),player,stick);
                h.assertTrue(item.getCount()==1,"Native stick did not consume one offhand attachment");
            }
            h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnectionData(connection).attachments().size()==2,"Stick did not install both native attachments");
        });
        h.runAtTickTime(35,()->{
            player.setShiftKeyDown(true);
            for(int index=0;index<2;index++) {
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                handler.interactWire(new NodeConnectionPoint(connection.node1(),connection.node2(),index==0?.25f:.75f),h.getLevel(),player,stick);
                h.assertTrue(player.getOffhandItem().getCount()==1&&player.getOffhandItem().is(index==0?Items.RED_BANNER:CEEItems.WIRE_DAMPER.get()),"Shift removal did not return the native attachment to the offhand");
            }
            player.setShiftKeyDown(false);
            h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnectionData(connection).attachments().isEmpty(),"Stick left stale decorations");
            h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).branchIdentity(connection.node1(),connection.node2())==identity[0],"Stick edits rebuilt unchanged electrical topology");
            BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),40,.04,"Decorated mixed wire supply");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    private static UseOnContext context(GameTestHelper h,net.minecraft.world.entity.player.Player player,InWorldNode node) {
        return new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(node.getPosition(h.getLevel()),Direction.UP,node.sourcePos(),false));
    }
}
