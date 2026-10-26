package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.content.energy_meter.TriPolarEnergyMeterDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.kinetics.base.TunedBlock;
import org.patryk3211.powergrid.kinetics.rheostat.RheostatBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class AdditionalFactoryGameTests {
    @GameTest(template="empty",timeoutTicks=90) public static void pgCurrentSourceDrivesAndReversesCeeResistor(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);floor(h,a,b);
        h.setBlock(a,ModdedBlocks.CREATIVE_CURRENT_SOURCE.get());h.setBlock(b,CEEBlocks.RESISTOR.get());var s=h.absolutePos(a);var l=h.absolutePos(b);
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)h.getBlockEntity(a)).setValue(.1f);DevicesSavedData.load(h.getLevel()).getDevice(l,ResistorDevice.class).properties=ElectricalProperties.resistor(100);wire(h,s,0,l,0);wire(h,s,1,l,1);});
        h.runAtTickTime(25,()->{near(h,ceeVoltage(h,l),10,.03,"PG current source into native CEE resistor");((CreativeSourceBlockEntity)h.getBlockEntity(a)).setValue(-.2f);});
        h.runAtTickTime(45,()->{var result=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;var positive=result.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,l),null);var negative=result.getVoltages(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,l),null);near(h,positive[positive.length-1]-negative[negative.length-1],-20,.03,"Reversed current source signed history");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=90) public static void ceeDrivesConfiguredPgPowerResistor(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);floor(h,a,b);h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.RESISTOR.get());var s=h.absolutePos(a);var l=h.absolutePos(b);
        h.runAtTickTime(5,()->{DevicesSavedData.load(h.getLevel()).getDevice(s,CreativeBatteryDevice.class).voltage=10;((SmartBlockEntity)h.getBlockEntity(b)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(27);wire(h,s,1,l,0);wire(h,s,0,l,1);});
        h.runAtTickTime(25,()->{near(h,((ResistorBlockEntity)h.getBlockEntity(b)).getValue(),100,.001,"Native power resistor configuration");near(h,pgVoltage(h,l),10,.03,"Power resistor voltage");((SmartBlockEntity)h.getBlockEntity(b)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(28);});
        h.runAtTickTime(45,()->{near(h,((ResistorBlockEntity)h.getBlockEntity(b)).getValue(),200,.001,"Changed native resistance");near(h,pgVoltage(h,l),10,.03,"Configured power resistor remains powered");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=90) public static void ceeStandaloneMomentaryPulsesPgLoad(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,1);var c=new BlockPos(5,2,1);floor(h,a,b,c);h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,CEEBlocks.MOMENTARY_SWITCH.get());h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)h.getBlockEntity(a)).setValue(10);((ResistorBlockEntity)h.getBlockEntity(c)).setValue(100);wire(h,s,0,control,0);wire(h,control,1,load,0);wire(h,s,1,load,1);});
        h.runAtTickTime(20,()->{near(h,pgVoltage(h,load),0,.01,"Momentary initially open");var player=h.makeMockPlayer(GameType.SURVIVAL);h.assertTrue(h.getLevel().getBlockState(control).useItemOn(ItemStack.EMPTY,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(control.getCenter(),Direction.UP,control,false)).consumesAction(),"Native momentary interaction rejected");});
        h.runAtTickTime(22,()->near(h,pgVoltage(h,load),10,.05,"Momentary pulse powers PG load"));
        h.runAtTickTime(40,()->{near(h,pgVoltage(h,load),0,.01,"Momentary released");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=130) public static void pgRheostatShaftDividesCeePower(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);var drive=b.above();floor(h,a,b,c);h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.RHEOSTAT.getDefaultState().setValue(TunedBlock.HORIZONTAL_FACING,Direction.SOUTH));h.setBlock(c,CEEBlocks.CREATIVE_RESISTOR.get());var s=h.absolutePos(a);var r=h.absolutePos(b);var l=h.absolutePos(c);double[] initial={0};
        h.runAtTickTime(5,()->{DevicesSavedData.load(h.getLevel()).getDevice(s,CreativeBatteryDevice.class).voltage=20;DevicesSavedData.load(h.getLevel()).getDevice(l,ResistorDevice.class).properties=ElectricalProperties.resistor(1000);wire(h,s,1,r,0);wire(h,s,0,r,2);wire(h,r,1,l,0);wire(h,r,2,l,1);});
        h.runAtTickTime(25,()->{initial[0]=ceeVoltage(h,l);h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));h.runAfterDelay(2,()->((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(32));});
        h.runAtTickTime(50,()->{((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(0);h.runAfterDelay(10,()->{var be=(RheostatBlockEntity)h.getBlockEntity(b);double ratio=be.getRatio();double total=be.resistance();double lower=1/(1/(ratio*total)+1/1000d);double expected=20*lower/((1-ratio)*total+lower);near(h,ceeVoltage(h,l),expected,.08,"Loaded native rheostat divider");h.assertTrue(Math.abs(ceeVoltage(h,l)-initial[0])>1,"Shaft did not adjust rheostat");var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());be.loadWithComponents(tag,h.getLevel().registryAccess());h.runAfterDelay(10,()->{near(h,ceeVoltage(h,l),expected,.08,"Saved rheostat arm");DynamicGameTests.audit(h);h.succeed();});});});
    }
    @GameTest(template="empty",timeoutTicks=110) public static void ceeStandaloneThreePoleMeterReadsPgLoads(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(1,2,4);var m=new BlockPos(3,2,2);var c=new BlockPos(5,2,1);var d=new BlockPos(5,2,4);floor(h,a,b,m,c,d);h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(m,CEEBlocks.TRI_POLAR_ENERGY_METER.get());h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());h.setBlock(d,ModdedBlocks.CREATIVE_RESISTOR.get());var p=h.absolutePos(m);double[] energy={0};
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)h.getBlockEntity(a)).setValue(10);((CreativeSourceBlockEntity)h.getBlockEntity(b)).setValue(-10);for(var q:new BlockPos[]{c,d})((ResistorBlockEntity)h.getBlockEntity(q)).setValue(10);wire(h,h.absolutePos(a),0,p,0);wire(h,h.absolutePos(b),0,p,2);wire(h,h.absolutePos(a),1,p,1);wire(h,h.absolutePos(b),1,p,1);wire(h,p,3,h.absolutePos(c),0);wire(h,p,5,h.absolutePos(d),0);wire(h,p,4,h.absolutePos(c),1);wire(h,p,4,h.absolutePos(d),1);});
        h.runAtTickTime(30,()->{near(h,pgVoltage(h,h.absolutePos(c)),10,.1,"First metered line including contact loss");near(h,pgVoltage(h,h.absolutePos(d)),-10,.1,"Second metered line including contact loss");energy[0]=DevicesSavedData.load(h.getLevel()).getDevice(p,TriPolarEnergyMeterDevice.class).totalEnergy;});
        h.runAtTickTime(70,()->{double delta=DevicesSavedData.load(h.getLevel()).getDevice(p,TriPolarEnergyMeterDevice.class).totalEnergy-energy[0];near(h,delta,20*40/72_000_000d,.0000001,"Three-pole signed consumed energy");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void floor(GameTestHelper h,BlockPos...positions){for(var p:positions)h.setBlock(p.below(),Blocks.STONE);}
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb){WiringGameTests.connect(h,a,pa,b,pb,false);}
    private static double ceeVoltage(GameTestHelper h,BlockPos p){return InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(p,0,1);}
    private static double pgVoltage(GameTestHelper h,BlockPos p){return new BlockWireEndpoint(p,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(p,1).getNode(h.getLevel()).getVoltage();}
    private static void near(GameTestHelper h,double actual,double expected,double tolerance,String label){BoardComponentGameTests.near(h,actual,expected,tolerance,label);}
}
