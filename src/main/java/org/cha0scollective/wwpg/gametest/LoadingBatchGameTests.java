package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity;
import com.george_vi.electroenergetics.content.electric_pump.ElectricPumpBlockEntity;
import com.george_vi.electroenergetics.content.converter.ConverterBlock;
import com.george_vi.electroenergetics.content.converter.ConverterDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

/** Real gameplay and stored state under queued restoration, independent of deadline unit checks. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class LoadingBatchGameTests {
    // Restoration belongs to chunks, so concurrent fixtures in neighboring
    // structures can pause one another. Each scenario owns its batch.
    @BeforeBatch(batch="wwpg_loading_graph") public static void configureGraph(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_charge") public static void configureCharge(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_outputs") public static void configureOutputs(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_converter") public static void configureConverter(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_orphan") public static void configureOrphan(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_native_storage") public static void configureNativeStorage(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="wwpg_loading_warmup") public static void configureWarmup(ServerLevel level) { DynamicGameTests.substeps(level); }
    @GameTest(template="empty", batch="wwpg_loading_warmup", timeoutTicks=80)
    public static void convergedWarmupHoldsCeeStorageWithoutFalseSolverErrors(GameTestHelper h) {
        var source=new BlockPos(1,2,1); var capacitor=new BlockPos(4,2,1);
        place(h,source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); place(h,capacitor,CEEBlocks.CAPACITOR.get());
        var level=h.getLevel(); var s=h.absolutePos(source); var c=h.absolutePos(capacitor);
        double[] saved={0}; float[] temperature={0};
        var diagnostics=new java.util.ArrayList<String>();
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);
            DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class).capacitance=.01;
            wire(h,s,c);
        });
        h.runAtTickTime(20,()->{
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,c,0,c,1).status()==MeasurementStatus.VALID,"Storage was not ready before warm-up");
            var device=DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class);
            saved[0]=device.lastVoltage; temperature[0]=device.temp;
            diagnostics.addAll(org.cha0scollective.wwpg.bridge.Bridges.get(level).diagnostics());
            var network=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(c,0).getNode(level).getNetwork();
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(30);
            network.warmUp(1);
        });
        h.runAtTickTime(21,()->{
            var device=DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class);
            // GameTest actions run before CEE's end-of-tick commit. Tick 20
            // still commits its already solved frame after the source edit.
            // Capture here, then hold the next complete frame as well.
            h.assertTrue(device.properties.lastVoltage==device.lastVoltage,"Warm-up retained uncommitted CEE numerical history");
            saved[0]=device.lastVoltage; temperature[0]=device.temp;
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,c,0,c,1).status()!=MeasurementStatus.VALID,"Warm-up frame exposed accepted gameplay measurements");
            h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(level).diagnostics().equals(diagnostics),"Converged warm-up was reported as a solver failure");
            new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(c,0).getNode(level).getNetwork().warmUp(1);
        });
        h.runAtTickTime(22,()->{
            var device=DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class);
            h.assertTrue(device.lastVoltage==saved[0] && device.properties.lastVoltage==saved[0] && device.temp==temperature[0],"Warm-up committed CEE charge or heating history");
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,c,0,c,1).status()!=MeasurementStatus.VALID,"Second warm-up frame exposed accepted gameplay measurements");
            h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(level).diagnostics().equals(diagnostics),"Second converged warm-up was reported as a solver failure");
        });
        h.runAtTickTime(35,()->{
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,c,0,c,1).status()==MeasurementStatus.VALID,"Storage failed to resume after warm-up");
            h.assertTrue(DevicesSavedData.load(level).getDevice(c,CapacitorDevice.class).lastVoltage>saved[0],"Storage did not respond to the changed source after warm-up");
            h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(level).diagnostics().equals(diagnostics),"Recovery introduced a solver error"); h.succeed();
        });
    }
    private static void place(GameTestHelper h, BlockPos relative, net.minecraft.world.level.block.Block block) {
        h.setBlock(relative.below(), Blocks.STONE); h.setBlock(relative, block);
    }
    private static void wire(GameTestHelper h, BlockPos source, BlockPos load) {
        WiringGameTests.connect(h, source, 0, load, 0, true);
        WiringGameTests.connect(h, source, 1, load, 1, false);
    }
    private static void request(GameTestHelper h, BlockPos position) { RestorationCoordinator.restoring(h.getLevel(), new ChunkPos(position)); }
    @GameTest(template="empty", batch="wwpg_loading_graph", timeoutTicks=20)
    public static void indexedDiscoveryPreservesWeakLinesGroundsAndPorts(GameTestHelper h) {
        // A local graph exercises partitioning without introducing blocks into the playable yard.
        var graph=new org.patryk3211.powergrid.electricity.sim.NetworkGraph();
        var network=new org.patryk3211.powergrid.electricity.sim.ElectricalNetwork(false);
        var a=new org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(new BlockPos(1,2,1)),0));
        var b=new org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(new BlockPos(3,2,1)),0));
        var c=new org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(new BlockPos(2,2,1)),0));
        for(var node:java.util.List.of(a,b,c)){network.addNode(node);graph.addNode(node);}
        var line=new org.patryk3211.powergrid.electricity.sim.special.TransmissionLine(1,1000,a,b,org.patryk3211.powergrid.electricity.GlobalElectricNetworks.getWorldNetworks(h.getLevel()));
        var groundA=new org.patryk3211.powergrid.electricity.sim.ElectricWire(100,a,null);var groundB=new org.patryk3211.powergrid.electricity.sim.ElectricWire(100,b,null);
        var branch=new org.patryk3211.powergrid.electricity.sim.ElectricWire(1,a,c);
        for(var wire:java.util.List.of(line,groundA,groundB,branch)){network.addWire(wire);graph.connect(wire.getNode1(),wire.getNode2(),wire);}
        var oldPort=new org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePort(a,0,line);network.addNode(oldPort);graph.couple(oldPort);
        var weak=new java.util.HashSet<org.patryk3211.powergrid.electricity.sim.special.TransmissionLine>();var ports=new java.util.HashSet<org.patryk3211.powergrid.electricity.sim.node.INode>();
        var split=org.cha0scollective.wwpg.bridge.IndexedIslandDiscovery.partition(graph,network,ignored->true,weak,ports);
        h.assertTrue(split.size()==2&&weak.contains(line)&&ports.contains(oldPort),"Weak coupling or obsolete port was lost");
        h.assertTrue(split.stream().anyMatch(part->part.contains(a)&&part.contains(c)&&part.contains(branch)&&part.contains(groundA)&&!part.contains(b)),"Strong branch and its ground were partitioned incorrectly");
        h.assertTrue(split.stream().anyMatch(part->part.contains(b)&&part.contains(groundB)&&!part.contains(a)),"Shared reference ground incorrectly joined weak components");
        var loop=new org.patryk3211.powergrid.electricity.sim.ElectricWire(1,c,b);network.addWire(loop);graph.connect(c,b,loop);weak.clear();ports.clear();
        var merged=org.cha0scollective.wwpg.bridge.IndexedIslandDiscovery.partition(graph,network,ignored->true,weak,ports);
        h.assertTrue(merged.size()==1&&weak.isEmpty()&&merged.getFirst().contains(line),"A strong loop failed to absorb its weak line");
        network.cleanup();h.succeed();
    }
    @GameTest(template="empty", batch="wwpg_loading_charge", timeoutTicks=1200)
    public static void restoringCircuitPreservesChargeAndLeavesOtherFactoryRunning(GameTestHelper h) {
        var source = new BlockPos(1,2,1); var capacitor = new BlockPos(4,2,1);
        place(h,source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); place(h,capacitor,CEEBlocks.CAPACITOR.get());
        var a=h.absolutePos(source);var cap=h.absolutePos(capacitor);var level=h.getLevel();
        var other=a.offset(96,0,0);var load=other.offset(3,0,0);var otherChunk=new ChunkPos(other);var loadChunk=new ChunkPos(load);
        level.setChunkForced(otherChunk.x,otherChunk.z,true);
        level.setChunkForced(loadChunk.x,loadChunk.z,true);
        level.setBlockAndUpdate(other.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(load.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(other,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());level.setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        double[] saved={0};int[] held={0},flushed={0},elapsed={-1},healthyFrames={0};boolean[] wired={false};
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(a)).setValue(10);
            ((CreativeSourceBlockEntity)level.getBlockEntity(other)).setValue(10);
            ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity)level.getBlockEntity(load)).setValue(1000);
            DevicesSavedData.load(level).getDevice(cap,CapacitorDevice.class).capacitance=.01;
            wire(h,a,cap);wire(h,other,load);
            wired[0]=true;
        });
        h.onEachTick(()->{
            if(!wired[0])return;
            if(elapsed[0]<0){
                var before=ElectricalMeasurements.readVoltageBetween(level,load,0,load,1);
                var charged=DevicesSavedData.load(level).getDevice(cap,CapacitorDevice.class);
                // One accepted frame can precede the remote chunk's final
                // endpoint/line reconstruction. Establish a settled factory
                // before testing that unrelated restoration leaves it running.
                boolean ready=level.getChunkSource().isPositionTicking(otherChunk.toLong()) && level.areEntitiesLoaded(otherChunk.toLong())
                        && level.getChunkSource().isPositionTicking(loadChunk.toLong()) && level.areEntitiesLoaded(loadChunk.toLong());
                if(!ready || before.status()!=MeasurementStatus.VALID || Math.abs(before.voltage().orElseThrow().meanVoltageVolts())<=9 || charged.lastVoltage<=9){healthyFrames[0]=0;return;}
                if(++healthyFrames[0]<10)return;
                saved[0]=charged.lastVoltage;elapsed[0]=0;
            }
            int sample=++elapsed[0];
            if(sample>=2&&sample<=24){
                boolean paused=RestorationCoordinator.pausedAt(level,cap);
                if(paused){held[0]++;var device=DevicesSavedData.load(level).getDevice(cap,CapacitorDevice.class);
                    h.assertTrue(device.lastVoltage==saved[0]&&device.properties.lastVoltage==saved[0],"Restoration advanced stored capacitor history");
                    h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,cap,0,cap,1).status()==MeasurementStatus.NOT_READY,"Restoring terminal exposed a valid stale measurement");
                }else{flushed[0]++;saved[0]=DevicesSavedData.load(level).getDevice(cap,CapacitorDevice.class).lastVoltage;}
                var unaffected=ElectricalMeasurements.readVoltageBetween(level,load,0,load,1);
                h.assertTrue(unaffected.status()==MeasurementStatus.VALID&&Math.abs(unaffected.voltage().orElseThrow().meanVoltageVolts())>9,"Unrelated factory stopped during restoration: "+unaffected);
            }
            if(sample<=24)request(h,a);
            if(sample!=45)return;
            h.assertTrue(held[0]>0&&flushed[0]>0,"Continuous restoration did not enforce a finite batching window");
            h.assertTrue(ElectricalMeasurements.readVoltageBetween(level,cap,0,cap,1).status()==MeasurementStatus.VALID,"Restored circuit did not resume");
            h.assertTrue(DevicesSavedData.load(level).getDevice(cap,CapacitorDevice.class).lastVoltage>9,"Restoration lost stored charge");
            level.setChunkForced(otherChunk.x,otherChunk.z,false);level.setChunkForced(loadChunk.x,loadChunk.z,false);h.succeed();
        });
    }
    @GameTest(template="empty", batch="wwpg_loading_outputs", timeoutTicks=100)
    public static void restoringCircuitCannotKeepLightHeatOrPumpPowered(GameTestHelper h) {
        var source=new BlockPos(1,2,1);var bulb=new BlockPos(3,2,1);var heater=new BlockPos(5,2,1);var pump=new BlockPos(3,2,4);
        place(h,source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());place(h,bulb,CEEBlocks.BULB.get());place(h,heater,CEEBlocks.RESISTIVE_HEATER.get());place(h,pump,CEEBlocks.ELECTRIC_PUMP.get());
        var a=h.absolutePos(source);var level=h.getLevel();
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(a)).setValue(300);for(var relative:new BlockPos[]{bulb,heater,pump})wire(h,a,h.absolutePos(relative));});
        h.runAtTickTime(35,()->{h.assertTrue(level.getBlockState(h.absolutePos(bulb)).getValue(BulbBlock.LIGHT)>0,"Lamp did not operate before restoration");});
        for(int tick=36;tick<=45;tick++)h.runAtTickTime(tick,()->request(h,a));
        h.runAtTickTime(44,()->{
            h.assertTrue(RestorationCoordinator.pausedAt(level,h.absolutePos(bulb)),"Fixture was not paused");
            h.assertTrue(level.getBlockState(h.absolutePos(bulb)).getValue(BulbBlock.LIGHT)==0,"Restoring lamp retained ghost power");
            h.assertTrue(((ResistiveHeaterBlockEntity)level.getBlockEntity(h.absolutePos(heater))).voltage==0,"Restoring heater retained voltage");
            h.assertTrue(((ElectricPumpBlockEntity)level.getBlockEntity(h.absolutePos(pump))).getSpeed()==0,"Restoring pump retained powered output");
        });
        h.runAtTickTime(75,()->{h.assertTrue(level.getBlockState(h.absolutePos(bulb)).getValue(BulbBlock.LIGHT)>0,"Lamp failed to resume after restoration");h.succeed();});
    }
    @GameTest(template="empty", batch="wwpg_loading_converter", timeoutTicks=100)
    public static void restorationDoesNotRepeatConverterFeTransfers(GameTestHelper h) {
        var source=new BlockPos(1,2,1);var converter=new BlockPos(4,2,1);var port=converter.below();
        place(h,source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(port,Blocks.BARREL);
        h.setBlock(converter,CEEBlocks.CONVERTER.getDefaultState().setValue(ConverterBlock.FACING,Direction.UP).setValue(ConverterBlock.SOURCE,false));
        var a=h.absolutePos(source);var c=h.absolutePos(converter);var level=h.getLevel();
        FixtureEnergyCapabilities.Storage[] receiver={null};long[] transfers={0};double[] stored={0};
        h.runAtTickTime(5,()->{receiver[0]=FixtureEnergyCapabilities.install(h.getBlockEntity(port),false,0);level.invalidateCapabilities(h.absolutePos(port));((CreativeSourceBlockEntity)level.getBlockEntity(a)).setValue(100);wire(h,a,c);});
        h.runAtTickTime(29,()->{h.assertTrue(receiver[0].received>0,"Converter did not operate before restoration");});
        for(int tick=30;tick<=40;tick++){final int sample=tick;h.runAtTickTime(tick,()->{
            var device=DevicesSavedData.load(level).getDevice(c,ConverterDevice.class);
            if(sample>31){h.assertTrue(RestorationCoordinator.pausedAt(level,c),"Converter did not enter restoration pause");h.assertTrue(receiver[0].received==transfers[0]&&device.storedEnergy==stored[0],"Paused converter advanced FE or electrical storage");}
            transfers[0]=receiver[0].received;stored[0]=device.storedEnergy;request(h,a);
        });}
        h.runAtTickTime(75,()->{h.assertTrue(receiver[0].received>transfers[0],"Converter did not resume after restoration");h.succeed();});
    }

    @GameTest(template="empty", batch="wwpg_loading_orphan", timeoutTicks=60)
    public static void unownedUnscheduledChunkCannotKeepRestorationActive(GameTestHelper h) {
        var missing = new ChunkPos(8192, 8192);
        h.runAtTickTime(5,()->RestorationCoordinator.restoring(h.getLevel(),missing));
        h.runAtTickTime(7,()->h.assertTrue(RestorationCoordinator.pending(h.getLevel(),missing),"Unscheduled chunk did not enter its loading window"));
        h.runAtTickTime(30,()->{
            h.assertTrue(!RestorationCoordinator.pending(h.getLevel(),missing),"Chunk without circuit endpoints kept restoration work active beyond twenty ticks");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(missing.x,missing.z)==null,"Restoration requested an unscheduled chunk");h.succeed();
        });
    }

    @GameTest(template="empty", batch="wwpg_loading_native_storage", timeoutTicks=100)
    public static void restorationPreservesNativeBatteryAndInverterHistory(GameTestHelper h) {
        var battery=new BlockPos(5,2,1);var terminal=battery.above();var batteryLoad=new BlockPos(7,2,1);
        var inverter=new BlockPos(4,2,4);var control=new BlockPos(1,2,4);var inverterLoad=new BlockPos(7,2,4);
        place(h,battery,ModdedBlocks.BATTERY.get());place(h,batteryLoad,CEEBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING,Direction.DOWN));
        place(h,control,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());place(h,inverterLoad,CEEBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(inverter.below(),Blocks.STONE);h.setBlock(inverter,ModdedBlocks.FE_INVERTER.getDefaultState().setValue(org.patryk3211.powergrid.electricity.febridge.FEInverterBlock.FACING,Direction.UP));
        var level=h.getLevel();var b=h.absolutePos(battery);var i=h.absolutePos(inverter);
        double[] initialBattery={0},heldBattery={0};int[] initialFe={0},heldFe={0};float[] history={0},controlVoltage={0};
        h.runAtTickTime(5,()->{
            var nativeBattery=(org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity)level.getBlockEntity(b);
            initialBattery[0]=nativeBattery.getCapacity()*.5;nativeBattery.setEnergy(initialBattery[0]);
            var fe=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,i,Direction.UP);
            initialFe[0]=fe.receiveEnergy(fe.getMaxEnergyStored(),false);
            ((CreativeSourceBlockEntity)h.getBlockEntity(control)).setValue((float)(org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.feInverterControlVoltage.get()*.5));
            for(var load:new BlockPos[]{batteryLoad,inverterLoad})StationaryEquipmentGameTests.resistor(h,load,1000);
            wire(h,h.absolutePos(terminal),h.absolutePos(batteryLoad));wire(h,i,h.absolutePos(inverterLoad));
            WiringGameTests.connect(h,h.absolutePos(control),0,i,2,true);WiringGameTests.connect(h,h.absolutePos(control),1,i,1,false);
        });
        h.runAtTickTime(29,()->{
            h.assertTrue(((org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity)level.getBlockEntity(b)).getEnergy()<initialBattery[0],"Native battery did not discharge before restoration");
            h.assertTrue(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,i,Direction.UP).getEnergyStored()<initialFe[0],"Native inverter did not consume FE before restoration");
        });
        for(int tick=30;tick<=40;tick++){final int sample=tick;h.runAtTickTime(tick,()->{
            var nativeBattery=(org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity)level.getBlockEntity(b);
            var entity=level.getBlockEntity(i);var saved=entity.saveWithoutMetadata(level.registryAccess());
            var fe=level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,i,Direction.UP);
            if(sample==31){
                h.assertTrue(RestorationCoordinator.pausedAt(level,i)&&RestorationCoordinator.pausedAt(level,b),"Native storage circuits did not pause");
                // An unfinished native filter must retain its exact state, rather than converge on a stale input.
                saved.putFloat("PrevThrottle",.375f);entity.loadWithComponents(saved,level.registryAccess());
                history[0]=.375f;controlVoltage[0]=saved.getFloat("ControlVoltage");heldFe[0]=fe.getEnergyStored();heldBattery[0]=nativeBattery.getEnergy();
            }else if(sample>31){
                h.assertTrue(nativeBattery.getEnergy()==heldBattery[0]&&fe.getEnergyStored()==heldFe[0],"Paused native storage advanced energy");
                h.assertTrue(saved.getFloat("PrevThrottle")==history[0]&&saved.getFloat("ControlVoltage")==controlVoltage[0],"Paused inverter advanced control history");
            }
            request(h,b);request(h,i);
        });}
        h.runAtTickTime(75,()->{
            h.assertTrue(((org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity)level.getBlockEntity(b)).getEnergy()<heldBattery[0],"Native battery failed to resume");
            h.assertTrue(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,i,Direction.UP).getEnergyStored()<heldFe[0],"Native inverter failed to resume");h.succeed();
        });
    }
}
