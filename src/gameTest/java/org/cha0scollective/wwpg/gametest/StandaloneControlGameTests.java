package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.relay.RelayDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlock;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class StandaloneControlGameTests {
    @GameTest(template="empty", timeoutTicks=110) public static void ceeCutoffSwitchControlsPgLoad(GameTestHelper h) { ceeSwitch(h,"cut_off_switch",false,false); }
    @GameTest(template="empty", timeoutTicks=110) public static void ceeDoubleSwitchControlsBothPgLines(GameTestHelper h) { ceeSwitch(h,"double_switch",true,false); }
    @GameTest(template="empty", timeoutTicks=110) public static void ceeEmergencyStopAndPlayerReset(GameTestHelper h) { ceeSwitch(h,"emergency_stop_button",false,true); }
    @GameTest(template="empty", timeoutTicks=90) public static void pgLvSwitchControlsCeeLoad(GameTestHelper h) { pgSwitch(h,"lv_switch",false); }
    @GameTest(template="empty", timeoutTicks=90) public static void pgMvSwitchControlsCeeLoad(GameTestHelper h) { pgSwitch(h,"mv_switch",false); }
    @GameTest(template="empty", timeoutTicks=90) public static void pgButtonReleasesCeeLoad(GameTestHelper h) { pgSwitch(h,"lv_button",true); }

    private static void ceeSwitch(GameTestHelper h, String id, boolean twoLines, boolean emergency) {
        var a=new BlockPos(1,2,1);var c=new BlockPos(3,2,1);var b=new BlockPos(5,2,1);
        for(var q:new BlockPos[]{a,c,b})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(c,BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("electroenergetics",id)));
        h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var control=h.absolutePos(c);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(100);
            wire(h,source,0,control,0);wire(h,control,twoLines?2:1,load,0);
            if(twoLines){wire(h,source,1,control,1);wire(h,control,3,load,1);}else wire(h,source,1,load,1);
        });
        h.runAtTickTime(20,()->{near(h,pgVoltage(h,load),10,id+" native initial closed state");useCee(h,control,false);});
        h.runAtTickTime(40,()->{near(h,pgVoltage(h,load),0,id+" after player use");useCee(h,control,emergency);});
        h.runAtTickTime(65,()->{near(h,pgVoltage(h,load),10,id+" restored state");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void pgSwitch(GameTestHelper h,String id,boolean button){
        var a=new BlockPos(1,2,1);var c=new BlockPos(3,2,1);var b=new BlockPos(5,2,1);
        for(var q:new BlockPos[]{a,c,b})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(c,BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("powergrid",id)));h.setBlock(b,CEEBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var control=h.absolutePos(c);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{
            DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=10;
            wire(h,source,1,control,0);wire(h,control,1,load,0);wire(h,source,0,load,1);
        });
        h.runAtTickTime(20,()->{near(h,ceeVoltage(h,load),0,id+" open");usePg(h,control);});
        h.runAtTickTime(23,()->{near(h,ceeVoltage(h,load),10,id+" closed");if(!button)usePg(h,control);});
        h.runAtTickTime(40,()->{near(h,ceeVoltage(h,load),0,id+" reopened");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void ceeRelayCoilControlsPgFactoryLoad(GameTestHelper h){
        var a=new BlockPos(1,2,1);var c=new BlockPos(3,2,1);var b=new BlockPos(5,2,1);var coil=new BlockPos(3,2,4);
        for(var q:new BlockPos[]{a,c,b,coil})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(coil,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(c,CEEBlocks.RELAY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var drive=h.absolutePos(coil);var control=h.absolutePos(c);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(10);
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(drive)).setValue(0);
            ((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(100);
            wire(h,source,0,control,2);wire(h,control,3,load,0);wire(h,source,1,load,1);
            wire(h,drive,0,control,0);wire(h,drive,1,control,1);
        });
        h.runAtTickTime(20,()->{near(h,pgVoltage(h,load),0,"Relay unpowered");((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(drive)).setValue(6);});
        h.runAtTickTime(40,()->{near(h,pgVoltage(h,load),10,"Relay coil closes factory load");h.assertTrue(DevicesSavedData.load(h.getLevel()).getDevice(control,RelayDevice.class).closed,"Relay did not consume solved coil voltage");((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(drive)).setValue(0);});
        h.runAtTickTime(65,()->{near(h,pgVoltage(h,load),0,"Relay coil release");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void ceeRedstoneRelayUsesRealWorldInput(GameTestHelper h){
        var a=new BlockPos(1,2,1);var c=new BlockPos(3,2,1);var b=new BlockPos(5,2,1);
        for(var q:new BlockPos[]{a,c,b})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(c,CEEBlocks.REDSTONE_RELAY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var control=h.absolutePos(c);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(10);((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(100);wire(h,source,0,control,0);wire(h,control,1,load,0);wire(h,source,1,load,1);});
        h.runAtTickTime(20,()->{near(h,pgVoltage(h,load),0,"Redstone relay off");h.setBlock(c.north(),Blocks.REDSTONE_BLOCK);});
        h.runAtTickTime(40,()->{near(h,pgVoltage(h,load),10,"Redstone relay powered");h.setBlock(c.north(),Blocks.AIR);});
        h.runAtTickTime(65,()->{near(h,pgVoltage(h,load),0,"Redstone relay released");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void useCee(GameTestHelper h,BlockPos pos,boolean shift){
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(shift);
        var result=h.getLevel().getBlockState(pos).useItemOn(ItemStack.EMPTY,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        h.assertTrue(result.consumesAction(),"Native CEE control rejected player use");
    }
    private static void usePg(GameTestHelper h,BlockPos pos){
        var state=h.getLevel().getBlockState(pos);var block=(SwitchBlock)state.getBlock();var player=h.makeMockPlayer(GameType.SURVIVAL);
        var result=block.use(state,h.getLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        h.assertTrue(result.consumesAction(),"Native PG control rejected player use");
    }
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb){WiringGameTests.connect(h,a,pa,b,pb,false);}
    private static void near(GameTestHelper h,double value,double expected,String label){BoardComponentGameTests.near(h,value,expected,0.1,label);}
    private static double pgVoltage(GameTestHelper h,BlockPos p){return new BlockWireEndpoint(p,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(p,1).getNode(h.getLevel()).getVoltage();}
    private static double ceeVoltage(GameTestHelper h,BlockPos p){return InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(p,0,1);}
}
