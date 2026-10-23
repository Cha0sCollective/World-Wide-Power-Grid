package org.cha0scollective.wwpg.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.contactor.ContactorBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;

/** A temporarily unavailable coil reading must not act like an accepted loss of power. */
@GameTestHolder("wwpg_contactors")
@PrefixGameTestTemplate(false)
public final class ContactorRestorationGameTests {
    @BeforeBatch(batch="contactor_restore")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="contactor_restore_mixed")
    public static void configureMixed(ServerLevel level) { DynamicGameTests.substeps(level); }
    @BeforeBatch(batch="contactor_restore_off")
    public static void configureOff(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty", batch="contactor_restore", timeoutTicks=180)
    public static void poweredContactorStaysClosedAcrossRepeatedRestoration(GameTestHelper h) {
        var supply = new BlockPos(1,2,1);
        var coil = new BlockPos(1,2,4);
        var contactor = new BlockPos(4,2,4);
        var load = new BlockPos(7,2,4);
        for (var p : List.of(supply,coil,contactor,load)) h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(supply,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(coil,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(contactor,ModdedBlocks.CONTACTOR.get());
        h.setBlock(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(contactor.above(),ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        var level = h.getLevel();
        var s = h.absolutePos(supply); var c = h.absolutePos(coil);
        var control = h.absolutePos(contactor); var l = h.absolutePos(load);
        int[] pausedTicks = {0};
        h.runAtTickTime(5,()-> {
            ((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);
            ((CreativeSourceBlockEntity)level.getBlockEntity(c)).setValue(30);
            ((ResistorBlockEntity)level.getBlockEntity(l)).setValue(100);
            for (int terminal=0; terminal<2; terminal++) {
                WiringGameTests.connect(h,c,terminal,control.above(),terminal,true);
                WiringGameTests.connect(h,s,terminal,control,2+2*terminal,true);
                WiringGameTests.connect(h,control,3+2*terminal,l,terminal,true);
            }
        });
        for (int tick=35; tick<=130; tick++) {
            final int sample = tick;
            h.runAtTickTime(tick,()-> {
                var device = (ContactorBlockEntity)level.getBlockEntity(control);
                if (RestorationCoordinator.pausedAt(level,control)) {
                    pausedTicks[0]++;
                    var coilWire = coilWire(device);
                    h.assertTrue(!coilWire.isConverged(),"Paused coil validated its masked current as an accepted reading");
                    h.assertTrue(coilWire.current()==0,"Paused coil exposed live current");
                }
                // Check every tick, including while outputs are deliberately masked.
                h.assertTrue(device.getSignal()==15,"Steady powered contactor opened at tick "+sample
                        +" (restoring="+RestorationCoordinator.pausedAt(level,control)+")");
                if (sample>=45 && sample<=54 || sample>=80 && sample<=89 || sample>=115 && sample<=124)
                    RestorationCoordinator.restoring(level,new ChunkPos(c));
            });
        }
        h.runAtTickTime(135,()-> {
            h.assertTrue(pausedTicks[0]>=6,"Fixture never exercised paused coil readings");
            var reading = org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.readVoltageBetween(level,l,0,l,1);
            h.assertTrue(reading.voltage().isPresent(),"Load did not recover its accepted voltage");
            BoardComponentGameTests.near(h,reading.voltage().orElseThrow().meanVoltageVolts(),10,.1,"Restored contactor load");
            ((CreativeSourceBlockEntity)level.getBlockEntity(c)).setValue(0);
        });
        h.runAtTickTime(145,()-> {
            h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(control)).getSignal()==0,
                    "Contactor failed to release after accepted coil power removal");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template="empty", batch="contactor_restore_mixed", timeoutTicks=180)
    public static void restoredCeePoweredCoilAndLinkedContactorHoldUntilAcceptedRelease(GameTestHelper h) {
        var drive = new BlockPos(1,2,4); var control = new BlockPos(4,2,4); var linked = control.south();
        for (var p : List.of(drive,control,linked)) h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(drive,com.george_vi.electroenergetics.CEEBlocks.CREATIVE_BATTERY.get());
        for (var p : List.of(control,linked)) h.setBlock(p,ModdedBlocks.CONTACTOR.get());
        h.setBlock(control.above(),ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        var level = h.getLevel(); var d = h.absolutePos(drive);
        var c = h.absolutePos(control); var other = h.absolutePos(linked);
        int[] pausedTicks = {0};
        h.runAtTickTime(5,()-> {
            mixedSource(level,d,30);
            for (int terminal=0; terminal<2; terminal++)
                WiringGameTests.connect(h,d,terminal,c.above(),terminal,false);
        });
        h.runAtTickTime(35,()-> {
            var device = (ContactorBlockEntity)level.getBlockEntity(c);
            h.assertTrue(device.getSignal()==15,"Mixed coil never closed");
            var saved = device.saveWithoutMetadata(level.registryAccess());
            h.assertTrue(saved.getBoolean("State"),"Closed contactor did not save its state");
            // Exercise the same saved-state reader and rebuilt internals, retaining
            // the physical wires. The queued restoration then owns readiness.
            device.loadWithComponents(saved,level.registryAccess());
            device.getElectricBehaviour().rebuildCircuit(false);
            RestorationCoordinator.restoring(level,new ChunkPos(d));
        });
        for (int tick=36; tick<=54; tick++) {
            final int sample=tick;
            h.runAtTickTime(tick,()-> {
                if (RestorationCoordinator.pausedAt(level,c)) pausedTicks[0]++;
                h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(c)).getSignal()==15,
                        "Restored CEE-powered contactor opened at tick "+sample);
                h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(other)).getSignal()==15,
                        "Linked contactor lost its mechanical hold at tick "+sample);
                if (sample==38) mixedSource(level,d,0);
                if (sample<54) RestorationCoordinator.restoring(level,new ChunkPos(d));
            });
        }
        h.runAtTickTime(65,()-> {
            h.assertTrue(pausedTicks[0]>=6,"Restored mixed coil did not pause");
            for (var p : List.of(c,other)) h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(p)).getSignal()==0,
                    "Contact held after accepted loss of coil power");
            mixedSource(level,d,30);
        });
        for (int tick=80; tick<=95; tick++) h.runAtTickTime(tick,()-> {
            for (var p : List.of(c,other)) h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(p)).getSignal()==15,
                    "Contact failed to reclose stably after restored mixed supply");
        });
        h.runAtTickTime(100,()-> { DynamicGameTests.audit(h); h.succeed(); });
    }

    @GameTest(template="empty", batch="contactor_restore_off", timeoutTicks=100)
    public static void unpoweredCoilCannotCloseUntilRestorationAcceptsNewSupply(GameTestHelper h) {
        var drive=new BlockPos(1,2,4); var control=new BlockPos(4,2,4);
        for (var p : List.of(drive,control)) h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(drive,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(control,ModdedBlocks.CONTACTOR.get());
        h.setBlock(control.above(),ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        var level=h.getLevel(); var d=h.absolutePos(drive); var c=h.absolutePos(control);
        int[] pausedTicks={0};
        h.runAtTickTime(5,()-> {
            ((CreativeSourceBlockEntity)level.getBlockEntity(d)).setValue(0);
            for (int terminal=0; terminal<2; terminal++) WiringGameTests.connect(h,d,terminal,c.above(),terminal,true);
        });
        h.runAtTickTime(30,()->RestorationCoordinator.restoring(level,new ChunkPos(d)));
        for (int tick=31; tick<=48; tick++) {
            final int sample=tick;
            h.runAtTickTime(tick,()-> {
                h.assertTrue(RestorationCoordinator.pausedAt(level,c),"Open coil did not pause"); pausedTicks[0]++;
                h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(c)).getSignal()==0,
                        "Contact closed before new coil supply was solved");
                if (sample==32) ((CreativeSourceBlockEntity)level.getBlockEntity(d)).setValue(30);
                RestorationCoordinator.restoring(level,new ChunkPos(d));
            });
        }
        h.runAtTickTime(65,()-> {
            h.assertTrue(pausedTicks[0]==18,"Open-coil pause coverage incomplete");
            h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(c)).getSignal()==15,
                    "Contactor failed to close after accepting the new coil supply");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static org.patryk3211.powergrid.electricity.sim.AbstractElectricWire coilWire(ContactorBlockEntity device) {
        var nodes=device.getElectricBehaviour().getExternalNodes();
        return ((org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor)nodes.get(0).getNetwork()).wwpg$wires().stream()
                .filter(wire->wire.getNode1()==nodes.get(0) && wire.getNode2()==nodes.get(1)).findFirst().orElseThrow();
    }

    private static void mixedSource(ServerLevel level,BlockPos pos,double voltage) {
        com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level)
                .getDevice(pos,com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class).voltage=voltage;
    }
}
