package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.fuse.FuseHolderBlockEntity;
import com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock;
import com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlockEntity;
import com.george_vi.electroenergetics.content.synchroscope.SynchroscopeBlockEntity;
import com.george_vi.electroenergetics.content.transmission_distribution.current_transformer.CurrentTransformerBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.current_transformer.CurrentTransformerBlockEntity;
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
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class CeeAuxiliaryGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,2,1),DEVICE=new BlockPos(3,2,3),LOAD=new BlockPos(6,2,3);
    @GameTest(template="empty",timeoutTicks=100) public static void pgPowersBothCeeIndicatorChannels(GameTestHelper h){
        place(h,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,2));
        h.runAtTickTime(5,()->{source(h,70);for(int channel=0;channel<2;channel++)for(int port=0;port<2;port++)wire(h,SOURCE,port,DEVICE,2*channel+port);});
        h.runAtTickTime(25,()->{var tag=h.getBlockEntity(DEVICE).saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(tag.getFloat("FirstLight")>.99&&tag.getFloat("SecondLight")>.99,"PG source did not illuminate both CEE channels");source(h,35);});
        h.runAtTickTime(45,()->{var tag=h.getBlockEntity(DEVICE).saveWithoutMetadata(h.getLevel().registryAccess());near(h,tag.getFloat("FirstLight"),.5,.03,"Indicator solved brightness");near(h,tag.getFloat("SecondLight"),.5,.03,"Second indicator solved brightness");source(h,0);});
        h.runAtTickTime(65,()->{var tag=h.getBlockEntity(DEVICE).saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(tag.getFloat("FirstLight")==0&&tag.getFloat("SecondLight")==0,"Unpowered CEE indicators remained on");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=120) public static void ceeRedstoneVariacControlsMixedFactory(GameTestHelper h){
        place(h,CEEBlocks.REDSTONE_VARIAC.getDefaultState());
        h.runAtTickTime(5,()->{source(h,20);((ResistorBlockEntity)h.getBlockEntity(LOAD)).setValue(1000);((SmartBlockEntity)h.getBlockEntity(DEVICE)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(8);
            wire(h,SOURCE,0,DEVICE,0);wire(h,SOURCE,1,DEVICE,1);wire(h,DEVICE,2,LOAD,0);wire(h,DEVICE,1,LOAD,1);});
        h.runAtTickTime(25,()->{near(h,voltage(h,LOAD),0,.01,"Redstone variac open");h.setBlock(DEVICE.north(),Blocks.REDSTONE_BLOCK);});
        h.runAtTickTime(55,()->{near(h,voltage(h,LOAD),20,.05,"Native redstone variac powered");h.setBlock(DEVICE.north(),Blocks.AIR);});
        h.runAtTickTime(90,()->{near(h,voltage(h,LOAD),0,.01,"Native redstone variac release");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void ceeCurrentTransformerMapsInternalWindingNodes(GameTestHelper h){
        place(h,CEEBlocks.CURRENT_TRANSFORMER.getDefaultState().setValue(CurrentTransformerBlock.TOP,true).setValue(CurrentTransformerBlock.BOTTOM,true));
        h.runAtTickTime(5,()->{source(h,20);((ResistorBlockEntity)h.getBlockEntity(LOAD)).setValue(10);((CurrentTransformerBlockEntity)h.getBlockEntity(DEVICE)).scaling.setValue(4);
            wire(h,SOURCE,0,DEVICE,0);wire(h,SOURCE,1,DEVICE,1);wire(h,DEVICE,2,LOAD,0);wire(h,DEVICE,3,LOAD,1);});
        h.runAtTickTime(25,()->{near(h,voltage(h,LOAD),2/(1+.01/10+.01/1000),.03,"Current transformer native 10:1 setting");((CurrentTransformerBlockEntity)h.getBlockEntity(DEVICE)).scaling.setValue(3);});
        h.runAtTickTime(50,()->{near(h,voltage(h,LOAD),20/(1+.02/10),.03,"Current transformer 1:1 reconfiguration");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=170) public static void ceeHeldFuseNativeInsertionTripsMixedLoad(GameTestHelper h){
        place(h,CEEBlocks.FUSE_HOLDER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        h.runAtTickTime(5,()->{source(h,20);((ResistorBlockEntity)h.getBlockEntity(LOAD)).setValue(1000);
            var pos=h.absolutePos(DEVICE);var player=h.makeMockPlayer(GameType.SURVIVAL);var fuse=CEEBlocks.FUSE.asStack();fuse.set(CEEDataComponents.FUSE_AMPERAGE,1);player.setItemInHand(InteractionHand.MAIN_HAND,fuse);
            var click=pos.getCenter().add(.25,.5,0);var result=h.getLevel().getBlockState(pos).useItemOn(fuse,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(click,Direction.UP,pos,false));
            h.assertTrue(result.consumesAction()&&fuse.isEmpty(),"Native held fuse insertion failed");
            var tag=h.getBlockEntity(DEVICE).saveWithoutMetadata(h.getLevel().registryAccess());boolean first=tag.contains("FirstID");int in=first?1:0,out=first?3:2;
            wire(h,SOURCE,0,DEVICE,in);wire(h,DEVICE,out,LOAD,0);wire(h,SOURCE,1,LOAD,1);});
        h.runAtTickTime(50,()->{near(h,voltage(h,LOAD),20,.05,"Inserted fuse conducts to mixed load");((ResistorBlockEntity)h.getBlockEntity(LOAD)).setValue(1);});
        h.runAtTickTime(140,()->{var tag=h.getBlockEntity(DEVICE).saveWithoutMetadata(h.getLevel().registryAccess());var held=tag.getCompound(tag.contains("FirstID")?"FirstData":"SecondData");h.assertTrue(held.getBoolean("Broken"),"Held CEE fuse did not trip on PG current");near(h,voltage(h,LOAD),0,.001,"Held fuse isolates mixed load");DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=150) public static void ceeSynchroscopeReceivesMixedThreePhaseHistory(GameTestHelper h){
        GenerationGameTests.assemble(h,true);var meter=new BlockPos(6,2,5);h.setBlock(meter.below(),Blocks.STONE);h.setBlock(meter,CEEBlocks.SYNCHROSCOPE.get());
        var brushes=new BlockPos(2,2,2);
        for(int phase=0;phase<3;phase++){var p=new BlockPos(2+phase*2,2,7);h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,ModdedBlocks.CREATIVE_RESISTOR.get());}
        h.runAtTickTime(5,()->{GenerationGameTests.speed(h,128);for(int phase=0;phase<3;phase++){
            var p=new BlockPos(2+phase*2,2,7);((ResistorBlockEntity)h.getBlockEntity(p)).setValue(1000);wire(h,brushes,phase+1,p,0);wire(h,brushes,0,p,1);
            wire(h,brushes,phase+1,meter,phase);wire(h,brushes,phase+1,meter,phase+3);}});
        h.runAtTickTime(100,()->{var be=(SynchroscopeBlockEntity)h.getBlockEntity(meter);near(h,be.saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("PhaseOffset"),0,.5,"Equal three-phase circuits remain synchronized");h.assertTrue(be.validConnection,"Synchroscope lost matching phase order");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void place(GameTestHelper h,net.minecraft.world.level.block.state.BlockState state){for(var p:new BlockPos[]{SOURCE,DEVICE,LOAD})h.setBlock(p.below(),Blocks.STONE);h.setBlock(SOURCE,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(DEVICE,state);h.setBlock(LOAD,ModdedBlocks.CREATIVE_RESISTOR.get());}
    private static void source(GameTestHelper h,float v){((CreativeSourceBlockEntity)h.getBlockEntity(SOURCE)).setValue(v);}
    private static void wire(GameTestHelper h,BlockPos a,int ta,BlockPos b,int tb){WiringGameTests.connect(h,h.absolutePos(a),ta,h.absolutePos(b),tb,true);}
    private static double voltage(GameTestHelper h,BlockPos p){var a=new BlockWireEndpoint(h.absolutePos(p),0).getNode(h.getLevel());var b=new BlockWireEndpoint(h.absolutePos(p),1).getNode(h.getLevel());return a.getVoltage()-b.getVoltage();}
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
