package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.potentiometer.PotentiometerDevice;
import com.george_vi.electroenergetics.content.transmission_distribution.hv_switch.HVSwitchBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.sf6_breaker.SF6BreakerBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class CeeControlGameTests {
    @GameTest(template="empty",timeoutTicks=130) public static void ceePotentiometerShaftAdjustsPgLoad(GameTestHelper h){potentiometer(h,false);}
    @GameTest(template="empty",timeoutTicks=130) public static void ceeRedstonePotentiometerAdjustsPgLoad(GameTestHelper h){potentiometer(h,true);}
    private static void potentiometer(GameTestHelper h,boolean redstone){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);var drive=b.above();
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,redstone?CEEBlocks.REDSTONE_POTENTIOMETER.get():CEEBlocks.POTENTIOMETER.get());h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100_000);
            ((SmartBlockEntity)level.getBlockEntity(control)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(100_000);
            wire(h,s,0,control,0);wire(h,s,1,control,2);wire(h,control,1,load,0);wire(h,control,2,load,1);});
        h.runAtTickTime(20,()->{near(h,voltage(h,load),10,.05,"Potentiometer native initial position");
            if(redstone)h.setBlock(b.north(),Blocks.REDSTONE_BLOCK);
            else{h.setBlock(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));h.runAfterDelay(2,()->((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(-64));}});
        h.runAtTickTime(45,()->{var device=DevicesSavedData.load(level).getDevice(control,PotentiometerDevice.class);double p=device.progress;
            h.assertTrue(redstone?p>.99:p>.1&&p<.5,"Native input did not move potentiometer: "+p);
            double lower=Math.max(.001,(1-p)*device.resistance),upper=Math.max(.001,p*device.resistance),parallel=1/(1/lower+1/100_000d);
            h.assertTrue(voltage(h,load)<9,"Moving potentiometer did not reduce output");
            if(redstone)h.setBlock(b.north(),Blocks.AIR);else((SmartBlockEntity)h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(0);
            if(!redstone)h.runAfterDelay(10,()->{
                var held=DevicesSavedData.load(level).getDevice(control,PotentiometerDevice.class);double lo=Math.max(.001,(1-held.progress)*held.resistance),hi=Math.max(.001,held.progress*held.resistance),eq=1/(1/lo+1/100_000d);
                near(h,voltage(h,load),10*eq/(hi+eq),.05,"Loaded divider after native shaft settles");});
        });
        h.runAtTickTime(70,()->{if(redstone)near(h,voltage(h,load),10,.05,"Redstone release restores native divider");
            var be=(SmartBlockEntity)level.getBlockEntity(control);var nbt=be.saveWithoutMetadata(level.registryAccess());double before=voltage(h,load);be.loadWithComponents(nbt,level.registryAccess());
            h.runAfterDelay(10,()->{near(h,voltage(h,load),before,.05,"Potentiometer configuration survives NBT reload");DynamicGameTests.audit(h);h.succeed();});});
    }

    @GameTest(template="empty",timeoutTicks=300) public static void ceeHvSwitchConnectorAndPlayerControl(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var target=b.south(2);var c=new BlockPos(6,2,3);
        for(var p:new BlockPos[]{a,b,target,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(target,CEEBlocks.CONNECTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        h.setBlock(b,CEEBlocks.HV_SWITCH.getDefaultState().setValue(HVSwitchBlock.FACING,Direction.SOUTH));h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(a);var control=h.absolutePos(b);var t=h.absolutePos(target);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);((ResistorBlockEntity)level.getBlockEntity(load)).setValue(1000);
            wire(h,s,0,control,0);wire(h,t,0,load,0);wire(h,s,1,load,1);});
        h.runAtTickTime(20,()->{near(h,voltage(h,load),0,.001,"HV switch open");use(h,control);});
        h.runAtTickTime(130,()->{near(h,voltage(h,load),10,.05,"HV switch closes onto native long connector");use(h,control);});
        h.runAtTickTime(250,()->{near(h,voltage(h,load),0,.001,"HV switch reopens");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",timeoutTicks=120) public static void ceeSf6BreakerUsesBothBlocksAndRedstone(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,3);var c=new BlockPos(6,2,3);
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
        var state=CEEBlocks.SF6_BREAKER.getDefaultState();h.setBlock(b,state.setValue(SF6BreakerBlock.BASE,true));h.setBlock(b.above(),state.setValue(SF6BreakerBlock.BASE,false));
        var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            wire(h,s,0,control,0);wire(h,control.above(),0,load,0);wire(h,s,1,load,1);});
        h.runAtTickTime(20,()->{near(h,voltage(h,load),10,.05,"SF6 native initial closed state");h.setBlock(b.north(),Blocks.REDSTONE_BLOCK);});
        h.runAtTickTime(50,()->{near(h,voltage(h,load),0,.001,"SF6 redstone opens both-part bridge");h.setBlock(b.north(),Blocks.AIR);});
        h.runAtTickTime(80,()->{near(h,voltage(h,load),10,.05,"SF6 redstone release restores load");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void use(GameTestHelper h,BlockPos pos){var p=h.makeMockPlayer(GameType.SURVIVAL);h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(ItemStack.EMPTY,h.getLevel(),p,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)).consumesAction(),"Native player control rejected");}
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb){WiringGameTests.connect(h,a,pa,b,pb,false);}
    private static double voltage(GameTestHelper h,BlockPos pos){return new BlockWireEndpoint(pos,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(pos,1).getNode(h.getLevel()).getVoltage();}
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
