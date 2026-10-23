package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.collections.ModdedBlocks;

/** The same saved stationary circuits run first upstream, then with the release bridge. */
@GameTestHolder("wwpg_reference")
@PrefixGameTestTemplate(false)
public final class ReferenceWorldGameTests {
    @net.minecraft.gametest.framework.BeforeBatch(batch="reference")
    public static void configureNativeReferenceBackend(net.minecraft.server.level.ServerLevel level) {
        DynamicGameTests.substeps(level);
        com.george_vi.electroenergetics.config.CEEConfigs.server().simulationConfig.microTicks.set(16);
        if (Boolean.getBoolean("wwpg.test.tightReferencePrecision")) {
            var solver = org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.solver;
            solver.solverAbsolutePrecision.set(1e-10); solver.solverAbsoluteMinimumPrecision.set(1e-10);
            org.patryk3211.powergrid.electricity.GlobalElectricNetworks.configsReloaded();
        }
    }
    private static final BlockPos CEE_SOURCE=new BlockPos(8,64,8),CEE_LOAD=CEE_SOURCE.offset(3,0,0),CAP=CEE_SOURCE.offset(0,0,3);
    private static final BlockPos PG_SOURCE=new BlockPos(8,64,24),PG_BOARD=PG_SOURCE.offset(3,0,0),PG_LOAD=PG_SOURCE.offset(0,0,3);
    @GameTest(template="empty",batch="reference",timeoutTicks=120) public static void nativeCeeStationaryWorldRemainsFunctional(GameTestHelper h){
        var level=h.getLevel();level.setChunkForced(0,0,true);level.getChunk(0,0);boolean upstream=Boolean.getBoolean("wwpg.test.upstreamReference");
        if(upstream){
            for(var p:new BlockPos[]{CEE_SOURCE,CEE_LOAD,CAP}){level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());}
            level.setBlockAndUpdate(CEE_SOURCE,CEEBlocks.CREATIVE_BATTERY.getDefaultState());level.setBlockAndUpdate(CEE_LOAD,CEEBlocks.CREATIVE_RESISTOR.getDefaultState());level.setBlockAndUpdate(CAP,CEEBlocks.CAPACITOR.getDefaultState());
            h.runAtTickTime(10,()->{((SmartBlockEntity)level.getBlockEntity(CEE_SOURCE)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(10_000);
                ((SmartBlockEntity)level.getBlockEntity(CEE_LOAD)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(100_000);
                ((SmartBlockEntity)level.getBlockEntity(CAP)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(100_000_000);
                for(var p:new BlockPos[]{CEE_LOAD,CAP})for(int port=0;port<2;++port)WiringGameTests.connect(h,CEE_SOURCE,1-port,p,port,false);});
        }
        h.runAtTickTime(80,()->{var results=InfrastructureSavedData.load(level).ticker.lastResults;
            h.assertTrue(results!=null,"Pinned native CEE circuit produced no result");near(h,results.getVoltageAt(CEE_LOAD,0,1),10,.1,"Saved CEE reference voltage");near(h,Math.abs(results.getCurrentThrough(CEE_LOAD,0,1)),.1,.001,"Saved CEE reference current");
            near(h,DevicesSavedData.load(level).getDevice(CAP,CapacitorDevice.class).lastVoltage,10,.1,"Saved CEE capacitor state");finish(h,upstream);});
    }
    @GameTest(template="empty",batch="reference",timeoutTicks=120) public static void nativePgStationaryWorldRemainsFunctional(GameTestHelper h){
        var level=h.getLevel();level.setChunkForced(0,1,true);level.getChunk(0,1);boolean upstream=Boolean.getBoolean("wwpg.test.upstreamReference");
        if(upstream){
            for(var p:new BlockPos[]{PG_SOURCE,PG_LOAD,PG_BOARD}){level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());}
            level.setBlockAndUpdate(PG_SOURCE,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());level.setBlockAndUpdate(PG_LOAD,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());level.setBlockAndUpdate(PG_BOARD,ModdedBlocks.CIRCUIT_BOARD.getDefaultState());
            h.runAtTickTime(10,()->{((SmartBlockEntity)level.getBlockEntity(PG_SOURCE)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(17);((ResistorBlockEntity)level.getBlockEntity(PG_LOAD)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(35);
                ((CircuitBoardBlockEntity)level.getBlockEntity(PG_BOARD)).setSchematic(BoardGameTests.diodeBoard(1000));
                for(var p:new BlockPos[]{PG_LOAD,PG_BOARD})for(int port=0;port<2;++port)WiringGameTests.connect(h,PG_SOURCE,port,p,port,true);});
        }
        h.runAtTickTime(80,()->{double v=new BlockWireEndpoint(PG_LOAD,0).getNode(level).getVoltage()-new BlockWireEndpoint(PG_LOAD,1).getNode(level).getVoltage();near(h,v,17,.1,"Saved PG reference voltage");
            var board=(CircuitBoardBlockEntity)level.getBlockEntity(PG_BOARD);h.assertTrue(board.getComponentsStream().count()==4,"Saved PG board lost its native components");
            var diode=board.getComponentsStream().filter(c->c.component==org.patryk3211.powergrid.circuits.components.Components.DIODE.get()).findFirst().orElseThrow(()->new IllegalStateException("Native reference lost diode component"));
            // The native diode board uses terminal 1 as its forward input.
            // Native board builds store simple components in shared wires rather
            // than the component's optional special-wire list.
            var resistor=board.getComponentsStream().filter(c->c.component==org.patryk3211.powergrid.circuits.components.Components.RESISTOR.get()).findFirst().orElseThrow();
            var n0=board.getBaked().getNode(new org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.Node(resistor,0));
            var n1=board.getBaked().getNode(new org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.Node(resistor,1));
            h.assertTrue(Math.abs(n0.getVoltage()-n1.getVoltage())/1000<.000001,"Reverse-biased native PG reference diode conducted");finish(h,upstream);});
    }
    private static void finish(GameTestHelper h,boolean upstream){
        var expected=org.patryk3211.powergrid.config.CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend","NATIVE"));
        var active=org.patryk3211.powergrid.electricity.GlobalElectricNetworks.getWorldNetworks(h.getLevel()).subnetworks.stream()
                .filter(n->!n.isEmpty()).map(n->((org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor)n).wwpg$solver().type()).toList();
        h.assertTrue(!active.isEmpty() && active.stream().allMatch(type->type==expected),"Native reference backend differs: "+active+" expected "+expected);
        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("NATIVE_REFERENCE_BACKEND: requested={}, active={}, upstream={}",expected,active,upstream);
        if(upstream)h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).backendCounts().isEmpty(),"Reference jar unexpectedly activated the WWPG bridge");
        else DynamicGameTests.audit(h);
        h.getLevel().getDataStorage().save();h.getLevel().getChunkSource().save(true);h.succeed();
    }
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
