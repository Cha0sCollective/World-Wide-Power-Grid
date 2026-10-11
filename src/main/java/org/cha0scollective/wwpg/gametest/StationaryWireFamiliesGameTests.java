package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import java.util.*;

@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class StationaryWireFamiliesGameTests {
    @GameTest(template="empty",timeoutTicks=1000)
    public static void allStationaryWireFamiliesRetainNativeRules(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,2,1));var load=source.east(4);
        for(var pos:new BlockPos[]{source,load})h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        h.getLevel().setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        var families=CEERegistries.WIRE_TYPE.stream().filter(w->w!=CEEWireTypes.DUPLEX.get()&&w!=CEEWireTypes.BUNDLE_CONDUCTOR.get()).toList();
        h.assertTrue(families.size()==57,"Pinned wire inventory changed: "+families.size());
        var connection=new InWorldNodeConnection(new InWorldNode(0,source),new InWorldNode(0,load));
        h.runAtTickTime(5,()->{ShowroomTools.pgSource(h,source,10);ShowroomTools.resistance(h,load,1000);WiringGameTests.connect(h,source,1,load,1,true);});
        for(int i=0;i<families.size();i++) {
            var type=families.get(i);int tick=10+15*i;
            var player=new net.minecraft.world.entity.player.Player(h.getLevel(),source,0,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WWPG-wire-family")) {
                @Override public boolean isCreative(){return false;}
                @Override public boolean isSpectator(){return false;}
            };
            h.runAtTickTime(tick,()->{
                var spool=new ItemStack(type.getSpooledItem(),2);player.setItemInHand(InteractionHand.MAIN_HAND,spool);
                var color=nativeColor(type);
                if(color!=null)player.setItemInHand(InteractionHand.OFF_HAND,DyeItem.byColor(color).getDefaultInstance());
                for(var node:new InWorldNode[]{connection.node1(),connection.node2()})h.assertTrue(spool.getItem().useOn(context(h,player,node)).consumesAction(),"Native spool rejected "+CEERegistries.WIRE_TYPE.getKey(type));
                var data=InfrastructureSavedData.load(h.getLevel()).getConnectionData(connection);
                h.assertTrue(data!=null&&data.wireType()==type&&spool.getCount()==1&&player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get())==1,
                        "Native wire identity, offhand dye or cost changed: "+CEERegistries.WIRE_TYPE.getKey(type)
                                +" actual="+(data==null?"missing":CEERegistries.WIRE_TYPE.getKey(data.wireType()))+", spools="+spool.getCount()
                                +", empty="+player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get()));
                for(var node:new InWorldNode[]{connection.node1(),connection.node2()})spool.getItem().useOn(context(h,player,node));
                h.assertTrue(spool.getCount()==1,"Duplicate family connection consumed wire");
            });
            h.runAtTickTime(tick+5,()->{
                var sd=InfrastructureSavedData.load(h.getLevel());var data=sd.getConnectionData(connection);
                var returnWire=IElectric.getAt(h.getLevel(),source).getBehaviour(h.getLevel(),source,h.getLevel().getBlockState(source))
                        .getConnections().get(new BlockWireEndpoint(source,1)).stream().filter(w->w.isConnectedTo(load,1)).findFirst().orElseThrow();
                double expected=type.isDecorative()?0:10*1000/(1000+type.getResistance()*data.length+returnWire.getResistance());
                BoardComponentGameTests.near(h,voltage(h,load),expected,Math.max(.00001,Math.abs(expected)*.001),"Native wire equation for "+CEERegistries.WIRE_TYPE.getKey(type));
                var empty=CEEItems.EMPTY_SPOOL.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,empty);
                CEEWireInteractionBehaviours.EMPTY_SPOOL.get().interactWire(new NodeConnectionPoint(connection.node1(),connection.node2(),.5f),h.getLevel(),player,empty);
                h.assertTrue(empty.isEmpty()&&!sd.isConnected(connection.node1(),connection.node2())&&player.getInventory().countItem(type.getSpooledItem())==1,"Native family recovery lost cost or type");
            });
            h.runAtTickTime(tick+10,()->BoardComponentGameTests.near(h,voltage(h,load),0,.00001,"Recovered family left ghost power"));
        }
        h.runAtTickTime(10+15*families.size(),()->{DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void pgIronWireUsesCeeTerminalsWithoutChangingNativePgRestrictions(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,2,1));var load=source.east(4);
        for(var pos:new BlockPos[]{source,load})h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());h.getLevel().setBlockAndUpdate(load,CEEBlocks.INDICATOR_BULB.getDefaultState());
        var player=h.makeMockPlayer(GameType.SURVIVAL);var wire=ModdedItems.IRON_WIRE.asStack(64);
        h.runAtTickTime(5,()->{
            ShowroomTools.scroll(h,source,10_000);WiringGameTests.connect(h,source,0,load,1,false);
            player.setItemInHand(InteractionHand.MAIN_HAND,wire);
            for(var endpoint:new BlockWireEndpoint[]{new BlockWireEndpoint(source,1),new BlockWireEndpoint(load,0)})
                h.assertTrue(IElectric.getAt(h.getLevel(),endpoint.getPos()).onWire(h.getLevel().getBlockState(endpoint.getPos()),
                        new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(endpoint.getExactPosition(h.getLevel()),Direction.UP,endpoint.getPos(),false))).consumesAction(),
                        "CEE terminals incorrectly inherit PG's light-wire-only restriction");
            h.assertTrue(wire.getCount()<64,"Iron placement consumed no native wire");
        });
        h.runAtTickTime(25,()->{
            BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),10,.01,"PG iron between CEE supply and load");
            var entity=IElectric.getAt(h.getLevel(),source).getBehaviour(h.getLevel(),source,h.getLevel().getBlockState(source)).getConnections().get(new BlockWireEndpoint(source,1)).stream().findFirst().orElseThrow();
            player.setItemInHand(InteractionHand.MAIN_HAND,ModdedItems.WIRE_CUTTER.asStack());h.assertTrue(entity.interact(player,InteractionHand.MAIN_HAND).consumesAction(),"Native iron cutter rejected its wire");
        });
        h.runAtTickTime(40,()->{BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),0,.00001,"Cut iron left ghost power");DynamicGameTests.audit(h);h.succeed();});
    }
    private static UseOnContext context(GameTestHelper h,net.minecraft.world.entity.player.Player player,InWorldNode node) {
        return new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(node.getPosition(h.getLevel()),Direction.UP,node.sourcePos(),false));
    }
    private static double voltage(GameTestHelper h,BlockPos load) {
        return new BlockWireEndpoint(load,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(load,1).getNode(h.getLevel()).getVoltage();
    }
    private static DyeColor nativeColor(WireType type) {
        // CEE 1.1.3's colored glass entries omit the getColor() field. Their
        // native registry arrays still supply the exact offhand dye mapping.
        for(var color:DyeColor.values()) {
            int index=color.ordinal();
            if(CEEWireTypes.COLORED_WIRES[index].get()==type||CEEWireTypes.COLORED_HEAVILY_INSULATED_WIRES[index].get()==type
                    ||CEEWireTypes.COLORED_GLASS_INSULATOR[index].get()==type)return color;
        }
        return null;
    }
}
