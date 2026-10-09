package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class StationaryEndpointGameTests {
    @GameTest(template="empty",timeoutTicks=90) public static void ceePowerCrossesPgCordJunction(GameTestHelper h){cord(h,"cord_junction");}
    @GameTest(template="empty",timeoutTicks=90) public static void ceePowerCrossesPgCeilingCordJunction(GameTestHelper h){cord(h,"ceiling_tile_junction");}
    private static void cord(GameTestHelper h,String id){
        var a=new BlockPos(1,2,1);var j=new BlockPos(3,2,3);var b=new BlockPos(6,2,1);for(var p:new BlockPos[]{a,j,b})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(j,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("powergrid:"+id)));h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());var s=h.absolutePos(a);var junction=h.absolutePos(j);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{DevicesSavedData.load(h.getLevel()).getDevice(s,CreativeBatteryDevice.class).voltage=10;((ResistorBlockEntity)h.getBlockEntity(b)).setValue(100);NativeInteractions.connectCord(h,s,1,0,junction);NativeInteractions.connectJunctionToSplit(h,junction,load);});
        h.runAtTickTime(30,()->{BoardComponentGameTests.near(h,new BlockWireEndpoint(load,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(load,1).getNode(h.getLevel()).getVoltage(),10,.05,"Native mixed cord "+id);h.setBlock(j,Blocks.AIR);});
        h.runAtTickTime(50,()->{BoardComponentGameTests.near(h,new BlockWireEndpoint(load,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(load,1).getNode(h.getLevel()).getVoltage(),0,.001,"Removed cord junction");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=330) public static void stationaryConnectorFamiliesRetainMixedTerminalWiring(GameTestHelper h){
        var a=new BlockPos(1,2,1);var j=new BlockPos(3,2,3);var b=new BlockPos(6,2,1);for(var p:new BlockPos[]{a,j,b})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());var source=h.absolutePos(a);var junction=h.absolutePos(j);var load=h.absolutePos(b);
        var ids=new String[]{"electroenergetics:connector","electroenergetics:double_connector","electroenergetics:triple_connector","electroenergetics:quad_connector","electroenergetics:duplex_wire_termination","powergrid:wire_connector","powergrid:heavy_wire_connector","powergrid:ceiling_tile_connector"};
        h.runAtTickTime(5,()->{DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=10;((ResistorBlockEntity)h.getBlockEntity(b)).setValue(100);WiringGameTests.connect(h,source,0,load,1,false);});
        for(int i=0;i<ids.length;i++){String id=ids[i];int tick=10+30*i;
            h.runAtTickTime(tick,()->{h.setBlock(j,Blocks.AIR);h.setBlock(j,BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)));});
            h.runAtTickTime(tick+5,()->{var state=h.getLevel().getBlockState(junction);int port=state.getBlock() instanceof com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock<?> device?device.getNodePositions(h.getLevel(),junction,state).keySet().stream().filter(n->device.isNodeAccessible(h.getLevel(),junction,state,n)).mapToInt(Integer::intValue).min().orElseThrow():0;try{WiringGameTests.connect(h,source,1,junction,port,true);WiringGameTests.connect(h,junction,port,load,0,false);}catch(RuntimeException error){h.fail("Connector "+id+": "+state+": "+error.getMessage());}});
            h.runAtTickTime(tick+20,()->{double v=new BlockWireEndpoint(load,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(load,1).getNode(h.getLevel()).getVoltage();BoardComponentGameTests.near(h,v,10,.05,"Connector "+id);});
        }
        h.runAtTickTime(315,()->{h.setBlock(j,Blocks.AIR);h.runAfterDelay(5,()->{double v=new BlockWireEndpoint(load,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(load,1).getNode(h.getLevel()).getVoltage();BoardComponentGameTests.near(h,v,0,.001,"Removed connector leaves no ghost power");DynamicGameTests.audit(h);h.succeed();});});
    }
}
