package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEntity;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class HandheldMeterGameTests {
    @GameTest(template="empty", timeoutTicks=100)
    public static void multimeterReadsCeeAndMixedTerminals(GameTestHelper h) {
        circuit(h, true);
        var source=h.absolutePos(new BlockPos(1,2,1)); var load=h.absolutePos(new BlockPos(4,2,1));
        var player=h.makeMockPlayer(GameType.CREATIVE); player.setPos(source.getCenter());
        var stack=ModdedItems.MULTIMETER.asStack(); var meter=(MultimeterItem)stack.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        h.runAtTickTime(20,()->{
            probe(h,player,source,1); probe(h,player,source,0);
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),10,.05,"Handheld CEE terminal voltage");
            probe(h,player,source,0); probe(h,player,load,1);
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),10,.05,"Handheld mixed terminal voltage");
            DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=20;
        });
        h.runAtTickTime(40,()->{
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),20,.05,"Handheld live voltage update");
            h.getLevel().setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());
        });
        h.runAtTickTime(55,()->{
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),0,.00001,"Removed terminal cleared handheld reading");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void multimeterReadsCeeWireAndClearsCutConnection(GameTestHelper h) {
        circuit(h, false);
        var source=h.absolutePos(new BlockPos(1,2,1)); var load=h.absolutePos(new BlockPos(4,2,1));
        var player=h.makeMockPlayer(GameType.CREATIVE); player.setPos(source.getCenter());
        var stack=ModdedItems.MULTIMETER.asStack(); var meter=(MultimeterItem)stack.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var point=new NodeConnectionPoint(new InWorldNode(1,source),new InWorldNode(0,load),.5f);
        h.runAtTickTime(20,()->{
            h.assertTrue(com.george_vi.electroenergetics.CEEWireInteractionBehaviours.CLAMP_METER.get().isActiveFor(stack,player),"PG meter cannot target CEE wires");
            com.george_vi.electroenergetics.CEEWireInteractionBehaviours.CLAMP_METER.get().interactWire(point,h.getLevel(),player,stack);
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),1,.03,"Handheld CEE wire current");
            DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=20;
        });
        h.runAtTickTime(40,()->{
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),2,.05,"Handheld wire current update");
            InfrastructureSavedData.load(h.getLevel()).removeConnection(point.connection());
        });
        h.runAtTickTime(55,()->{
            meter.inventoryTick(stack,h.getLevel(),player,0,true);
            h.assertTrue(!stack.has(CEEDataComponents.NODE_CONNECTION),"Cut wire retained meter selection");
            BoardComponentGameTests.near(h,meter.getMeasurement(h.getLevel(),stack),0,.00001,"Cut wire cleared handheld reading");
            DynamicGameTests.audit(h);h.succeed();
        });
    }

    @GameTest(template="empty", timeoutTicks=100)
    public static void clampAttachesToPgWireAndReleases(GameTestHelper h) {
        circuit(h, true);
        var source=h.absolutePos(new BlockPos(1,2,1));
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(source.getCenter());
        var stack=CEEItems.CLAMP_METER.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        h.runAtTickTime(25,()->{
            var wire=h.getLevel().getEntitiesOfClass(WireEntity.class,new net.minecraft.world.phys.AABB(source).inflate(6)).getFirst();
            h.assertTrue(wire.interact(player,InteractionHand.MAIN_HAND).consumesAction(),"CEE clamp rejected PG wire interaction");
            h.assertTrue(player.isUsingItem()&&stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().hasUUID(HandheldMeters.PG_WIRE),"CEE clamp did not retain PG wire identity");
            BoardComponentGameTests.near(h,wire.measuredCurrent(),1,.03,"Clamped native PG current");
            stack.getItem().onUseTick(h.getLevel(),player,stack,9000);
            BoardComponentGameTests.near(h,stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getFloat(HandheldMeters.CURRENT),1,.03,"Clamp synchronized server current");
            player.releaseUsingItem();
            h.assertTrue(!stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().contains(HandheldMeters.PG_WIRE),"Released clamp retained old wire");
            DynamicGameTests.audit(h);h.succeed();
        });
    }

    private static void circuit(GameTestHelper h,boolean pgWire) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);
        h.setBlock(a.below(),Blocks.STONE);h.setBlock(b.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{
            DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=10;
            ((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(10);
            for(int i=0;i<2;i++)WiringGameTests.connect(h,source,1-i,load,i,pgWire);
        });
    }
    private static void probe(GameTestHelper h,net.minecraft.world.entity.player.Player player,BlockPos pos,int terminal) {
        var endpoint=new BlockWireEndpoint(pos,terminal);
        var result=player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(endpoint.getExactPosition(h.getLevel()),Direction.UP,pos,false)));
        h.assertTrue(result.consumesAction(),"Multimeter rejected terminal "+pos+":"+terminal);
    }
}
