package org.cha0scollective.wwpg.gametest;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;

/** Real native PG storage must not consume retained readings from a rejected tick. */
final class PgStorageFailureFixture {
    private PgStorageFailureFixture() { }

    private static void place(GameTestHelper h,BlockPos pos,net.minecraft.world.level.block.Block block) {
        h.setBlock(pos.below(),Blocks.STONE); h.setBlock(pos,block);
    }
    private static void wire(GameTestHelper h,BlockPos source,BlockPos load) {
        WiringGameTests.connect(h,source,0,load,0,true);
        WiringGameTests.connect(h,source,1,load,1,false);
    }

    static void run(GameTestHelper h) {
        var battery=new BlockPos(5,2,1); var batteryLoad=new BlockPos(7,2,1);
        var inverter=new BlockPos(4,2,4); var control=new BlockPos(1,2,4); var inverterLoad=new BlockPos(7,2,4);
        var healthy=new BlockPos(5,2,7); var healthyLoad=new BlockPos(7,2,7);
        for(var pos : new BlockPos[]{battery,healthy}) {
            place(h,pos,ModdedBlocks.BATTERY.get());
            h.setBlock(pos.above(),ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(
                    org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING,Direction.DOWN));
        }
        for(var pos : new BlockPos[]{batteryLoad,inverterLoad,healthyLoad}) place(h,pos,ModdedBlocks.CREATIVE_RESISTOR.get());
        place(h,control,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(inverter.below(),Blocks.STONE);
        h.setBlock(inverter,ModdedBlocks.FE_INVERTER.getDefaultState().setValue(
                org.patryk3211.powergrid.electricity.febridge.FEInverterBlock.FACING,Direction.UP));
        var level=h.getLevel(); var b=h.absolutePos(battery); var i=h.absolutePos(inverter); var good=h.absolutePos(healthy);
        double[] initial={0,0},held={0,0}; int[] fe={0}; float[] throttle={0};
        var owners=new java.util.HashSet<ElectricalNetwork>();
        var reject=new ISolverHook() {
            public Collection<IElectricNode> coupledNodes() { return List.of(); }
            public void startIteration(int iteration) { throw new IllegalStateException("Expected PG storage rejection fixture"); }
        };
        h.runAtTickTime(5,()->{
            for(var pos : new BlockPos[]{b,good}) {
                var entity=(BatteryBlockEntity)level.getBlockEntity(pos); entity.setEnergy(entity.getCapacity()*.5);
            }
            initial[0]=((BatteryBlockEntity)level.getBlockEntity(b)).getEnergy();
            initial[1]=((BatteryBlockEntity)level.getBlockEntity(good)).getEnergy();
            var energy=level.getCapability(Capabilities.EnergyStorage.BLOCK,i,Direction.UP);
            energy.receiveEnergy(energy.getMaxEnergyStored(),false);
            ((CreativeSourceBlockEntity)h.getBlockEntity(control)).setValue((float)(
                    org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.feInverterControlVoltage.get()*.5));
            for(var load : new BlockPos[]{batteryLoad,inverterLoad,healthyLoad})
                ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)h.getBlockEntity(load)).setValue(1000);
            wire(h,h.absolutePos(battery.above()),h.absolutePos(batteryLoad));
            wire(h,h.absolutePos(healthy.above()),h.absolutePos(healthyLoad)); wire(h,i,h.absolutePos(inverterLoad));
            WiringGameTests.connect(h,h.absolutePos(control),0,i,2,true);
            WiringGameTests.connect(h,h.absolutePos(control),1,i,1,false);
        });
        h.runAtTickTime(25,()->{
            h.assertTrue(((BatteryBlockEntity)level.getBlockEntity(b)).getEnergy()<initial[0],"Battery was not powered before rejection");
            for(var pos : new BlockPos[]{b,i}) {
                var behaviour=((ElectricBlockEntity)level.getBlockEntity(pos)).getElectricBehaviour();
                for(var node : behaviour.getInternalNodes()) if(node.getNetwork()!=null)owners.add(node.getNetwork());
                for(var node : behaviour.getExternalNodes()) if(node.getNetwork()!=null)owners.add(node.getNetwork());
            }
            h.assertTrue(!owners.isEmpty() && owners.stream().allMatch(ElectricalNetwork::isConverged),"Storage circuits were not ready");
            for(var network : owners)network.innerHooks.add(reject);
        });
        h.runAtTickTime(27,()->{
            h.assertTrue(owners.stream().noneMatch(ElectricalNetwork::isConverged),"Storage failure was not exercised");
            held[0]=((BatteryBlockEntity)level.getBlockEntity(b)).getEnergy();
            held[1]=((BatteryBlockEntity)level.getBlockEntity(good)).getEnergy();
            fe[0]=level.getCapability(Capabilities.EnergyStorage.BLOCK,i,Direction.UP).getEnergyStored();
            var entity=level.getBlockEntity(i); var saved=entity.saveWithoutMetadata(level.registryAccess());
            saved.putFloat("PrevThrottle",.375f); entity.loadWithComponents(saved,level.registryAccess()); throttle[0]=.375f;
            h.assertTrue(fe[0]>0,"Inverter had no FE to preserve");
        });
        h.runAtTickTime(31,()->{
            h.assertTrue(owners.stream().noneMatch(ElectricalNetwork::isConverged),"Rejected owners unexpectedly accepted");
            h.assertTrue(((BatteryBlockEntity)level.getBlockEntity(b)).getEnergy()==held[0],"Rejected battery consumed retained current");
            h.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK,i,Direction.UP).getEnergyStored()==fe[0],"Rejected inverter consumed FE");
            h.assertTrue(level.getBlockEntity(i).saveWithoutMetadata(level.registryAccess()).getFloat("PrevThrottle")==throttle[0],"Rejected inverter advanced filter history");
            h.assertTrue(((BatteryBlockEntity)level.getBlockEntity(good)).getEnergy()<held[1],"Healthy unrelated battery was held");
            for(var network : owners)network.innerHooks.remove(reject);
        });
        h.runAtTickTime(50,()->{
            h.assertTrue(owners.stream().allMatch(ElectricalNetwork::isConverged),"Storage circuits did not recover");
            h.assertTrue(((BatteryBlockEntity)level.getBlockEntity(b)).getEnergy()<held[0],"Battery did not resume discharge");
            h.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK,i,Direction.UP).getEnergyStored()<fe[0],"Inverter did not resume FE draw");
            h.succeed();
        });
    }
}
