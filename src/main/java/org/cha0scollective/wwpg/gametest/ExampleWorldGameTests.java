package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** A small saved world built through the same pinned native workflows as acceptance. */
@GameTestHolder("wwpg_example")
@PrefixGameTestTemplate(false)
public final class ExampleWorldGameTests {
    @GameTest(template="empty",timeoutTicks=650) public static void buildAndVerifyMixedExampleWorld(GameTestHelper h){
        var level=h.getLevel();level.setChunkForced(0,0,true);level.setChunkForced(0,1,true);level.getChunk(0,0);level.getChunk(0,1);
        var s=new BlockPos(8,64,8);var r=s.east(2);var p=s.east(4);var b=s.east(6);var load=b.south(3);
        var factory=new BlockPos(8,64,24);var lamp=factory.east(3);var heater=factory.east(6);var pump=factory.offset(3,0,4);var input=pump.west(4);var output=pump.east(4);
        boolean verify=System.getProperty("wwpg.test.restartPhase","SETUP").equals("VERIFY");
        if(!verify){
        for(int x=3;x<=20;x++)for(int z=3;z<=33;z++)level.setBlockAndUpdate(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState());
        for(var pos:new BlockPos[]{s,r,p,b,load,factory,lamp,heater,pump,input,output})level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(s,CEEBlocks.CREATIVE_BATTERY.getDefaultState());level.setBlockAndUpdate(r,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        level.setBlockAndUpdate(p,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.NORTH));level.setBlockAndUpdate(b,ModdedBlocks.CIRCUIT_BOARD.getDefaultState());level.setBlockAndUpdate(load,CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
        level.setBlockAndUpdate(factory,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());level.setBlockAndUpdate(lamp,CEEBlocks.BULB.getDefaultState());level.setBlockAndUpdate(heater,CEEBlocks.RESISTIVE_HEATER.getDefaultState());
        level.setBlockAndUpdate(pump,CEEBlocks.ELECTRIC_PUMP.getDefaultState().setValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING,Direction.EAST));
        level.setBlockAndUpdate(input,AllBlocks.FLUID_TANK.getDefaultState());level.setBlockAndUpdate(output,AllBlocks.FLUID_TANK.getDefaultState());
        for(int dx=-3;dx<=3;dx++){if(dx==0)continue;level.setBlockAndUpdate(pump.offset(dx,0,0),AllBlocks.FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.DOWN,false).setValue(BlockStateProperties.UP,false).setValue(BlockStateProperties.NORTH,false).setValue(BlockStateProperties.SOUTH,false).setValue(BlockStateProperties.EAST,true).setValue(BlockStateProperties.WEST,true));}
        h.runAtTickTime(10,()->{
            ((SmartBlockEntity)level.getBlockEntity(s)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(20_000);((SmartBlockEntity)level.getBlockEntity(r)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(36);((SmartBlockEntity)level.getBlockEntity(load)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(1_000_000);
            var panel=(ElectricalPanelBlockEntity)level.getBlockEntity(p);
            var control=CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get().createNew(p,CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get().mode.getNodesFor(p,ElectricalPanelSlot.THIRD_LEFT),level,ElectricalPanelSlot.THIRD_LEFT,Direction.NORTH,level.registryAccess());control.label="Factory enable";((CutOffSwitchPanelAttachment)control).isClosed=true;
            var meter=CEEPanelAttachmentTypes.VOLTMETER.get().createNew(p,CEEPanelAttachmentTypes.VOLTMETER.get().mode.getNodesFor(p,ElectricalPanelSlot.THIRD_CENTERED),level,ElectricalPanelSlot.THIRD_CENTERED,Direction.NORTH,level.registryAccess());meter.label="Stored voltage";
            panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()]=control;panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]=meter;panel.attachmentUpdate();
            ((CircuitBoardBlockEntity)level.getBlockEntity(b)).setSchematic(ElectronicsPersistenceGameTests.capacitorBoard());
            wire(h,s,1,r,0,true);wire(h,r,1,p,control.nodes[0].id(),false);wire(h,p,control.nodes[1].id(),b,0,true);wire(h,s,0,b,1,false);wire(h,b,0,load,0,false);wire(h,b,1,load,1,true);wire(h,b,0,p,meter.nodes[0].id(),true);wire(h,b,1,p,meter.nodes[1].id(),false);
            // PG's high-range scroll indices encode (index - 250) * 100 volts.
            ((SmartBlockEntity)level.getBlockEntity(factory)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(253);
            for(var pos:new BlockPos[]{lamp,heater,pump})for(int port=0;port<2;port++)wire(h,factory,port,pos,port,true);
            var tank=level.getCapability(Capabilities.FluidHandler.BLOCK,input,Direction.EAST);h.assertTrue(tank!=null&&tank.fill(new FluidStack(Fluids.WATER,8000),IFluidHandler.FluidAction.EXECUTE)==8000,"Example water tank failed");
        });
        }else{
            h.assertTrue(level.getBlockState(s).is(CEEBlocks.CREATIVE_BATTERY.get())&&level.getBlockState(factory).is(ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()),"Example source blocks were not saved");
            h.runAtTickTime(30,()->{var board=(CircuitBoardBlockEntity)level.getBlockEntity(b);var capacitor=board.getComponentsStream().filter(c->c.component==org.patryk3211.powergrid.circuits.components.Components.CAPACITOR.get()).findFirst().orElseThrow();h.assertTrue(((org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire)capacitor.wires.getFirst()).capacitorVoltage()>19,"Example restart lost stored energy");});
        }
        h.startSequence().thenIdle(240).thenWaitUntil(()->{
            double v=new BlockWireEndpoint(b,0).getNode(level).getVoltage()-new BlockWireEndpoint(b,1).getNode(level).getVoltage();BoardComponentGameTests.near(h,v,20*1000/1010d,.2,"Example stored voltage");
            h.assertTrue(level.getBlockState(lamp).getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)>0,"Example mixed lamp is unpowered");
            var heaterEntity=(com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity)level.getBlockEntity(heater);
            h.assertTrue(heaterEntity.heat>.1,"Example heater is cold: voltage="+heaterEntity.voltage+", source="+((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)level.getBlockEntity(factory)).getValue()+", entities="+level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(factory)));
            var tank=level.getCapability(Capabilities.FluidHandler.BLOCK,output,Direction.EAST);h.assertTrue(tank!=null&&tank.getFluidInTank(0).getAmount()>0,"Example pump did not transfer water");
        }).thenExecute(()->{level.setDefaultSpawnPos(new BlockPos(8,64,16),180);level.getServer().getWorldData().setGameType(net.minecraft.world.level.GameType.CREATIVE);DynamicGameTests.audit(h);level.getDataStorage().save();level.getChunkSource().save(true);}).thenSucceed();
    }
    private static void wire(GameTestHelper h,BlockPos a,int ta,BlockPos b,int tb,boolean pg){WiringGameTests.connect(h,a,ta,b,tb,pg);}
}
