package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
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
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.config.ThermalValues;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.basinheater.BasinHeaterBlock;
import org.patryk3211.powergrid.electricity.basinheater.BasinHeaterBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.heater.HeaterBlockEntity;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;
import org.patryk3211.powergrid.kinetics.motor.ConstantSpeedMotorBlockEntity;
import org.patryk3211.powergrid.kinetics.servo.ServoBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class PgFactoryGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,2,1),LOAD=new BlockPos(4,2,1);
    @GameTest(template="empty",timeoutTicks=160) public static void ceePowersPgNativeLightFixtures(GameTestHelper h){
        var positions=new BlockPos[]{LOAD,LOAD.offset(0,0,3),LOAD.offset(0,0,6)};
        h.setBlock(SOURCE.below(),Blocks.STONE);h.setBlock(SOURCE,CEEBlocks.CREATIVE_BATTERY.get());
        var blocks=new net.minecraft.world.level.block.Block[]{ModdedBlocks.LIGHT_FIXTURE.get(),ModdedBlocks.CEILING_TILE_LAMP.get(),ModdedBlocks.FACTORY_LIGHT.get()};
        for(int i=0;i<positions.length;i++){h.setBlock(positions[i].below(),Blocks.STONE);h.setBlock(positions[i],blocks[i]);
            if(i==2)h.setBlock(positions[i].above(),ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));}
        h.runAtTickTime(5,()->{source(h,120);
            var player=h.makeMockPlayer(GameType.SURVIVAL);
            for(int i=0;i<positions.length;i++){
                var pos=h.absolutePos(positions[i]);var bulb=ModdedItems.LIGHT_BULB.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,bulb);
                var be=(AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(pos);
                h.assertTrue(be.replaceBulb(player,InteractionHand.MAIN_HAND,bulb)&&bulb.isEmpty(),"Native light installation/cost failed");
                for(int port=0;port<2;port++)WiringGameTests.connect(h,h.absolutePos(SOURCE),1-port,i==2?pos.above():pos,port,port==0);
            }});
        h.runAtTickTime(75,()->{for(var p:positions){var be=(AbstractLightFixtureBlockEntity)h.getBlockEntity(p);h.assertTrue(be.getPowerLevel()>0&&!be.getBulbState().isBurned(),"CEE supply did not illuminate "+p);}
            source(h,0);});
        h.runAtTickTime(140,()->{for(var p:positions)h.assertTrue(((AbstractLightFixtureBlockEntity)h.getBlockEntity(p)).getPowerLevel()==0,"Unpowered PG fixture retained light");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=220) public static void ceePowersPgNativeHeatingAndBasinState(GameTestHelper h){
        var basin=LOAD.offset(0,0,4);var source2=SOURCE.offset(0,0,4);
        for(var p:new BlockPos[]{SOURCE,source2,LOAD,basin})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(SOURCE,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(source2,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(LOAD,ModdedBlocks.HEATING_COIL.get());h.setBlock(basin,ModdedBlocks.BASIN_HEATER.get());
        for(var p:new BlockPos[]{LOAD,basin})h.setBlock(p.east(),ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.WEST));
        double[] hot=new double[2];
        h.runAtTickTime(5,()->{var coil=(HeaterBlockEntity)h.getBlockEntity(LOAD);var heater=(BasinHeaterBlockEntity)h.getBlockEntity(basin);
            source(h,Math.sqrt(coil.resistance()*ThermalValues.getPower(ModdedBlocks.HEATING_COIL.get())*.8));
            DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(source2),CreativeBatteryDevice.class).voltage=Math.sqrt(heater.resistance()*BasinHeaterBlockEntity.power()*1.25);
            for(int port=0;port<2;port++){WiringGameTests.connect(h,h.absolutePos(SOURCE),1-port,h.absolutePos(LOAD.east()),port,true);WiringGameTests.connect(h,h.absolutePos(source2),1-port,h.absolutePos(basin.east()),port,true);}});
        h.runAtTickTime(120,()->{hot[0]=((SmartBlockEntity)h.getBlockEntity(LOAD)).getBehaviour(ThermalBehaviour.TYPE).getTemperature();hot[1]=((SmartBlockEntity)h.getBlockEntity(basin)).getBehaviour(ThermalBehaviour.TYPE).getTemperature();
            h.assertTrue(hot[0]>200&&((HeaterBlockEntity)h.getBlockEntity(LOAD)).getState()!=HeaterBlockEntity.State.COLD,"CEE supply failed to heat PG coil: "+hot[0]);
            h.assertTrue(hot[1]>600&&h.getBlockState(basin).getValue(BasinHeaterBlock.HEAT_LEVEL).isAtLeast(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED),"CEE supply failed to heat PG basin: "+hot[1]);
            source(h,0);DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(source2),CreativeBatteryDevice.class).voltage=0;});
        h.runAtTickTime(190,()->{h.assertTrue(((SmartBlockEntity)h.getBlockEntity(LOAD)).getBehaviour(ThermalBehaviour.TYPE).getTemperature()<hot[0]&&((SmartBlockEntity)h.getBlockEntity(basin)).getBehaviour(ThermalBehaviour.TYPE).getTemperature()<hot[1],"PG heaters failed to cool after CEE power removal");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=145) public static void ceePowersPgConstantSpeedMotorConfiguration(GameTestHelper h){
        place(h,ModdedBlocks.CONSTANT_SPEED_MOTOR.get());float[] first={0};
        h.runAtTickTime(5,()->{source(h,100);for(int port=0;port<2;port++)WiringGameTests.connect(h,h.absolutePos(SOURCE),1-port,h.absolutePos(LOAD),port,true);});
        h.runAtTickTime(35,()->{var motor=(ConstantSpeedMotorBlockEntity)h.getBlockEntity(LOAD);first[0]=motor.getGeneratedSpeed();h.assertTrue(Math.abs(first[0])==16&&motor.calculateAddedStressCapacity()>0,"Native constant-speed motor produced no capacity");motor.getBehaviour(ScrollValueBehaviour.TYPE).setValue(32);});
        h.runAtTickTime(55,()->{h.assertTrue(Math.abs(((ConstantSpeedMotorBlockEntity)h.getBlockEntity(LOAD)).getGeneratedSpeed())==32,"Motor ignored its native speed setting");source(h,-100);});
        h.runAtTickTime(85,()->{h.assertTrue(((ConstantSpeedMotorBlockEntity)h.getBlockEntity(LOAD)).getGeneratedSpeed()*first[0]<0,"Native constant-speed motor lost polarity");source(h,0);});
        h.runAtTickTime(120,()->{h.assertTrue(((ConstantSpeedMotorBlockEntity)h.getBlockEntity(LOAD)).getGeneratedSpeed()==0,"Unpowered motor retained shaft output");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=200) public static void ceeControlsPgServoAngleAndSavedState(GameTestHelper h){
        place(h,ModdedBlocks.SERVO.get());var ctrl=SOURCE.offset(0,0,3);h.setBlock(ctrl.below(),Blocks.STONE);h.setBlock(ctrl,CEEBlocks.CREATIVE_BATTERY.get());
        h.runAtTickTime(5,()->{source(h,24);DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(ctrl),CreativeBatteryDevice.class).voltage=2.5;
            for(int port=0;port<2;port++)WiringGameTests.connect(h,h.absolutePos(SOURCE),1-port,h.absolutePos(LOAD),port,true);
            WiringGameTests.connect(h,h.absolutePos(ctrl),0,h.absolutePos(LOAD),1,false);WiringGameTests.connect(h,h.absolutePos(ctrl),1,h.absolutePos(LOAD),2,false);});
        h.runAtTickTime(90,()->{var be=(ServoBlockEntity)h.getBlockEntity(LOAD);var tag=be.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(tag.getInt("Angle")==180,"CEE control voltage did not set servo angle: "+tag.getInt("Angle"));be.loadWithComponents(tag,h.getLevel().registryAccess());DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(ctrl),CreativeBatteryDevice.class).voltage=-2.5;});
        h.runAtTickTime(180,()->{var be=(ServoBlockEntity)h.getBlockEntity(LOAD);h.assertTrue(be.saveWithFullMetadata(h.getLevel().registryAccess()).getInt("Angle")==-180,"CEE control polarity failed to reverse servo target");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void place(GameTestHelper h,net.minecraft.world.level.block.Block block){for(var p:new BlockPos[]{SOURCE,LOAD})h.setBlock(p.below(),Blocks.STONE);h.setBlock(SOURCE,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(LOAD,block);}
    private static void source(GameTestHelper h,double voltage){DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(SOURCE),CreativeBatteryDevice.class).voltage=voltage;}
}
