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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlock;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlock;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class PgGenerationGameTests {
    @GameTest(template="empty",timeoutTicks=350)
    public static void ceeExcitesPgWindingAndGeneratorPowersCeeLoad(GameTestHelper h){
        var brush=new BlockPos(3,2,2);var rotor=brush.south();var clutch=rotor.south();var drive=clutch.south();
        var field=new BlockPos(1,2,1);var load=new BlockPos(6,2,2);var connector=new BlockPos(3,4,2);
        for(var q:new BlockPos[]{brush,rotor,clutch,drive,field,load})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(brush,ModdedBlocks.GENERATOR_COMMUTATOR.getDefaultState().setValue(CommutatorBlock.HORIZONTAL_FACING,Direction.NORTH));
        h.setBlock(rotor,ModdedBlocks.GENERATOR_INDUCTION_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z));
        h.setBlock(clutch,ModdedBlocks.GENERATOR_CLUTCH.getDefaultState().setValue(BlockStateProperties.FACING,Direction.SOUTH));
        h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.NORTH));
        for(int n=0;n<3;++n)h.setBlock(brush.above().south(n),ModdedBlocks.WINDING.getDefaultState()
                .setValue(WindingBlock.AXIS,Direction.Axis.Z).setValue(WindingBlock.ALONG_FIRST_AXIS,true).setValue(WindingBlock.PART,n));
        h.setBlock(connector,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        h.setBlock(field,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(load,CEEBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(brush);var supply=h.absolutePos(field);var output=h.absolutePos(load);var port=h.absolutePos(connector);double[] first=new double[1];
        h.runAtTickTime(10,()->{
            DevicesSavedData.load(h.getLevel()).getDevice(supply,CreativeBatteryDevice.class).voltage=5;
            ((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(64);
            wire(h,supply,1,port,0);wire(h,supply,0,port,1);wire(h,source,0,output,0);wire(h,source,1,output,1);
        });
        h.runAtTickTime(130,()->{
            var generator=(CommutatorBlockEntity)h.getLevel().getBlockEntity(source);first[0]=voltage(h,output);
            h.assertTrue(first[0]>0.1&&generator.getPower()>0,"PG generator did not power CEE: voltage="+first[0]+", power="+generator.getPower());
            ((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(128);
        });
        h.runAtTickTime(240,()->{
            h.assertTrue(voltage(h,output)>first[0]*1.7,"PG generation did not respond to native mechanical speed");
            DevicesSavedData.load(h.getLevel()).getDevice(supply,CreativeBatteryDevice.class).voltage=0;
        });
        h.runAtTickTime(320,()->{h.assertTrue(voltage(h,output)<first[0]*0.05,"PG winding retained excitation without CEE power");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void nativePgSolarPowersCeeAndRespondsToShade(GameTestHelper h){
        var panel=new BlockPos(3,2,2);var load=new BlockPos(6,2,2);var connector=panel.below();
        h.setBlock(panel,ModdedBlocks.SOLAR_PANEL.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        h.setBlock(connector,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.UP));
        h.setBlock(load.below(),Blocks.STONE);h.setBlock(load,CEEBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(connector);var output=h.absolutePos(load);double[] first=new double[1];
        h.runAtTickTime(5,()->{h.getLevel().setDayTime(6000);h.getLevel().setWeatherParameters(0,10000,false,false);wire(h,source,0,output,0);wire(h,source,1,output,1);});
        h.runAtTickTime(60,()->{
            first[0]=voltage(h,output);h.assertTrue(first[0]>5&&first[0]<30,"Native solar did not supply CEE load: "+first[0]);
            for(int x=-2;x<=2;++x)for(int z=-2;z<=2;++z)h.setBlock(panel.offset(x,3,z),Blocks.STONE);
        });
        h.runAtTickTime(150,()->{h.assertTrue(voltage(h,output)<first[0]*0.1,"Solar source ignored native shade");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb){WiringGameTests.connect(h,a,pa,b,pb,false);}
    private static double voltage(GameTestHelper h,BlockPos p){return InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(p,0,1);}
}
