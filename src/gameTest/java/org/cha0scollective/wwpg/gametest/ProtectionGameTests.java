package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.contactor.ContactorBlockEntity;
import org.patryk3211.powergrid.electricity.electricswitch.HvBreakerBlockEntity;
import org.patryk3211.powergrid.electricity.fuse.FuseHolderBlock;
import org.patryk3211.powergrid.electricity.fuse.FuseHolderBlockEntity;
import org.patryk3211.powergrid.electricity.fuse.FuseState;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class ProtectionGameTests {
    @GameTest(template="empty",timeoutTicks=180) public static void pgHvSwitchMechanicalAssemblyControlsCeeLoad(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(c,CEEBlocks.CREATIVE_RESISTOR.get());
        var state=ModdedBlocks.HV_SWITCH.getDefaultState().setValue(org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlock.PART,0).setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);
        h.setBlock(b,state);h.setBlock(b.south(),state.setValue(org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlock.PART,1));
        var block=ModdedBlocks.HV_SWITCH.get();Direction shaft=null;
        for(var direction:Direction.values())if(block.hasShaftTowards(h.getLevel(),h.absolutePos(b),state,direction)){shaft=direction;break;}
        h.assertTrue(shaft!=null,"HV switch has no shaft");var drive=b.relative(shaft);
        h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,shaft.getOpposite()));
        var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);
        h.runAtTickTime(5,()->{source(h,s,10);resistor(h,load,100);
            ((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(0);
            wire(h,s,1,control,0);wire(h,control.south(),1,load,0);wire(h,s,0,load,1);});
        h.runAtTickTime(20,()->{near(h,voltage(h,load),0,.001,"HV switch initially open");((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(64);});
        h.runAtTickTime(24,()->{var be=(org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlockEntity)h.getLevel().getBlockEntity(control);h.assertTrue(Math.abs(be.getSpeed())==64,"HV switch shaft is disconnected");
            if(be.getSpeed()<0)((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(-64);});
        h.runAtTickTime(50,()->{near(h,voltage(h,load),10,.1,"Native mechanical drive closes HV switch");var motor=((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE);motor.setValue(-motor.getValue());});
        h.runAtTickTime(90,()->{near(h,voltage(h,load),0,.001,"Reversed drive opens HV switch");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void pgFuseTripsAndAcceptsSurvivalRepair(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,1);var c=new BlockPos(5,2,1);
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.FUSE_HOLDER.get());h.setBlock(c,CEEBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(a);var fuse=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{
            source(h,s,10);resistor(h,load,100);
            ((SmartBlockEntity)level.getBlockEntity(fuse)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(1);
            wire(h,s,1,fuse,0);wire(h,fuse,1,load,0);wire(h,s,0,load,1);repair(h,fuse);
        });
        h.runAtTickTime(20,()->{near(h,voltage(h,load),10,.1,"Installed fuse powers CEE load");resistor(h,load,1);});
        h.runAtTickTime(35,()->{h.assertTrue(level.getBlockState(fuse).getValue(FuseHolderBlock.STATE)==FuseState.BLOWN,"PG fuse did not trip on actual mixed-network current");near(h,voltage(h,load),0,.001,"Blown fuse isolates load");
            h.assertTrue(((FuseHolderBlockEntity)level.getBlockEntity(fuse)).removeBlown(),"Native fuse removal failed");resistor(h,load,100);repair(h,fuse);});
        h.runAtTickTime(55,()->{near(h,voltage(h,load),10,.1,"Repaired fuse restores power");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void repair(GameTestHelper h,BlockPos pos){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var stack=ModdedItems.IRON_WIRE.asStack(2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var result=h.getLevel().getBlockState(pos).useItemOn(stack,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false));
        h.assertTrue(result.consumesAction()&&stack.getCount()==1,"Native fuse repair did not consume one iron wire");
    }

    @GameTest(template="empty",timeoutTicks=160) public static void pgContactorUsesCeeCoilPowerAndBothContacts(GameTestHelper h){
        var a=new BlockPos(1,2,1);var coil=new BlockPos(1,2,4);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);var connector=b.above();
        for(var p:new BlockPos[]{a,coil,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(coil,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.CONTACTOR.get());h.setBlock(c,CEEBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(connector,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
        var s=h.absolutePos(a);var drive=h.absolutePos(coil);var control=h.absolutePos(b);var load=h.absolutePos(c);var socket=h.absolutePos(connector);var level=h.getLevel();
        h.runAtTickTime(5,()->{source(h,s,10);source(h,drive,0);resistor(h,load,100);
            wire(h,s,1,control,2);wire(h,control,3,load,0);wire(h,s,0,control,4);wire(h,control,5,load,1);
            wire(h,drive,1,socket,0);wire(h,drive,0,socket,1);});
        h.runAtTickTime(20,()->{near(h,voltage(h,load),0,.001,"Contactor initially open");source(h,drive,30);});
        h.runAtTickTime(45,()->{near(h,voltage(h,load),10,.1,"CEE coil power closes both contactor poles");h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(control)).getSignal()==15,"Native contactor signal stale");source(h,drive,0);});
        h.runAtTickTime(70,()->{near(h,voltage(h,load),0,.001,"Contactor releases after coil power removal");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=240) public static void pgHvBreakerChargesClosesTripsAndRecovers(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.HV_BREAKER.get());h.setBlock(c,CEEBlocks.CREATIVE_RESISTOR.get());
        var state=h.getLevel().getBlockState(h.absolutePos(b));
        var block=(org.patryk3211.powergrid.electricity.electricswitch.HvBreakerBlock)state.getBlock();
        Direction shaft=null;for(var direction:Direction.values())if(block.hasShaftTowards(h.getLevel(),h.absolutePos(b),state,direction)){shaft=direction;break;}
        h.assertTrue(shaft!=null,"Breaker has no shaft");var drive=b.relative(shaft);
        h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,shaft.getOpposite()));
        var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{source(h,s,10);resistor(h,load,100);
            ((SmartBlockEntity)level.getBlockEntity(control)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(1);
            ((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(256);
            wire(h,s,1,control,0);wire(h,control,1,load,0);wire(h,s,0,load,1);});
        h.runAtTickTime(35,()->{h.assertTrue(((HvBreakerBlockEntity)level.getBlockEntity(control)).getSignal()==1,"Native breaker spring did not charge");h.setBlock(b.north(),Blocks.REDSTONE_BLOCK);});
        h.runAtTickTime(50,()->{near(h,voltage(h,load),10,.1,"Charged breaker closed by redstone");h.setBlock(b.north(),Blocks.AIR);resistor(h,load,1);});
        h.runAtTickTime(70,()->{near(h,voltage(h,load),0,.001,"Breaker trips mixed circuit overload");resistor(h,load,100);});
        h.runAtTickTime(100,()->{h.assertTrue(((HvBreakerBlockEntity)level.getBlockEntity(control)).getSignal()==1,"Tripped spring did not recharge");h.setBlock(b.north(),Blocks.REDSTONE_BLOCK);});
        h.runAtTickTime(120,()->{near(h,voltage(h,load),10,.1,"Recharged breaker restores repaired circuit");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void source(GameTestHelper h,BlockPos pos,double value){DevicesSavedData.load(h.getLevel()).getDevice(pos,CreativeBatteryDevice.class).voltage=value;}
    private static void resistor(GameTestHelper h,BlockPos pos,double value){DevicesSavedData.load(h.getLevel()).getDevice(pos,ResistorDevice.class).properties=ElectricalProperties.resistor(value);}
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb){WiringGameTests.connect(h,a,pa,b,pb,false);}
    private static double voltage(GameTestHelper h,BlockPos pos){return InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(pos,0,1);}
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
