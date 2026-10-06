package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorBlockEntity;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Fixed coordinates deliberately survive the separate GameTest server processes. */
@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class PersistenceGameTests {
    private static final BlockPos CEE_SOURCE = new BlockPos(1024, 64, 1024);
    private static final BlockPos PG_LOAD = CEE_SOURCE.offset(3, 0, 0);
    private static final BlockPos CAPACITOR = CEE_SOURCE.offset(6, 0, 0);
    private static final BlockPos PG_SOURCE = CEE_SOURCE.offset(0, 0, 4);
    private static final BlockPos CEE_LOAD = CEE_SOURCE.offset(3, 0, 4);

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void chunkReloadRebindsMixedEndpoints(GameTestHelper h) {
        var level = h.getLevel();
        var source = new BlockPos(2048, 64, 2048); var load = source.offset(3, 0, 0);
        level.setChunkForced(128, 128, true); level.getChunk(128, 128);
        h.runAtTickTime(5, () -> {
            for (var p : new BlockPos[] {source, load}) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
            }
        });
        h.runAtTickTime(10, () -> {
            level.setBlockAndUpdate(source, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            level.setBlockAndUpdate(load, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        });
        Object[] oldEndpoint = new Object[1];
        h.runAtTickTime(15, () -> {
            DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class).voltage = 10;
            ((ResistorBlockEntity) level.getBlockEntity(load)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(35);
            for (int i = 0; i < 2; ++i) WiringGameTests.connect(h, source, 1 - i, load, i, true);
        });
        h.runAtTickTime(35, () -> {
            close(h, pgVoltage(h, load), 10, "Before chunk unload");
            oldEndpoint[0] = new BlockWireEndpoint(load, 0).getNode(level);
            level.getDataStorage().save(); level.getChunkSource().save(true);
            level.setChunkForced(128, 128, false);
        });
        h.runAtTickTime(180, () -> {
            h.assertTrue(level.getChunkSource().getChunkNow(128, 128) == null, "The fixture chunk never actually unloaded: "
                    + level.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(128, 128)));
            level.setChunkForced(128, 128, true); level.getChunk(128, 128);
        });
        h.runAtTickTime(220, () -> {
            close(h, pgVoltage(h, load), 10, "After chunk reload");
            h.assertTrue(oldEndpoint[0] != new BlockWireEndpoint(load, 0).getNode(level), "Chunk reload did not rebuild the native PG endpoint");
            DynamicGameTests.audit(h);
            level.setChunkForced(128, 128, false);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void restartPreservesBothWireSystemsAndCapacitor(GameTestHelper h) {
        var level = h.getLevel();
        level.setChunkForced(64, 64, true);
        level.getChunk(64, 64);
        boolean verify = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
        if (verify) {
            h.assertTrue(level.getBlockState(CEE_SOURCE).is(CEEBlocks.CREATIVE_BATTERY.get()), "Restart fixture is missing: run SETUP first");
            h.assertTrue(level.getBlockState(PG_SOURCE).is(ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()), "Restart lost PG source");
            var cap = DevicesSavedData.load(level).getDevice(CAPACITOR, CapacitorDevice.class);
            h.assertTrue(cap != null && cap.lastVoltage > 9.5, "Restart lost capacitor history: voltage=" + (cap == null ? "missing" : cap.lastVoltage)
                    + ", capacitance=" + (cap == null ? "missing" : cap.capacitance) + ", state=" + level.getBlockState(CAPACITOR));
        } else {
            // Let saved wire entities finish loading before rebuilding this reserved fixture.
            h.runAtTickTime(5, () -> {
                for (var p : new BlockPos[] {CEE_SOURCE, PG_LOAD, CAPACITOR, PG_SOURCE, CEE_LOAD}) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
                }
            });
            h.runAtTickTime(10, () -> {
                level.setBlockAndUpdate(CEE_SOURCE, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
                level.setBlockAndUpdate(PG_LOAD, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
                level.setBlockAndUpdate(CAPACITOR, CEEBlocks.CAPACITOR.getDefaultState());
                level.setBlockAndUpdate(PG_SOURCE, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
                level.setBlockAndUpdate(CEE_LOAD, CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
            });
            h.runAtTickTime(15, () -> {
                DevicesSavedData.load(level).getDevice(CEE_SOURCE, CreativeBatteryDevice.class).voltage = 10;
                ((CapacitorBlockEntity) level.getBlockEntity(CAPACITOR)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(100_000_000);
                DevicesSavedData.load(level).getDevice(CEE_LOAD, ResistorDevice.class).properties = ElectricalProperties.resistor(13);
                ((CreativeSourceBlockEntity) level.getBlockEntity(PG_SOURCE)).setValue(17);
                // The native value behavior owns persistence; setValue(double) only edits the live wire.
                ((ResistorBlockEntity) level.getBlockEntity(PG_LOAD)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(35);
                for (int i = 0; i < 2; ++i) {
                    WiringGameTests.connect(h, CEE_SOURCE, 1 - i, PG_LOAD, i, true);
                    WiringGameTests.connect(h, CEE_SOURCE, 1 - i, CAPACITOR, i, true);
                    WiringGameTests.connect(h, PG_SOURCE, i, CEE_LOAD, i, false);
                }
            });
        }
        Object[] identity = new Object[1];
        h.runAtTickTime(35, () -> {
            double wireResistance = 0;
            for (int i = 0; i < 2; ++i) {
                final int port = i;
                var endpoint = new BlockWireEndpoint(CEE_SOURCE, 1 - i);
                var connections = IElectric.getAt(level, CEE_SOURCE).getBehaviour(level, CEE_SOURCE, level.getBlockState(CEE_SOURCE)).getConnections().get(endpoint);
                h.assertTrue(connections != null, "Restart lost PG wire entities");
                var wire = connections.stream().filter(w -> w.isConnectedTo(PG_LOAD, port)).findFirst().orElse(null);
                h.assertTrue(wire != null, "Restart lost PG load connection");
                wireResistance += wire.getResistance();
            }
            double resistance = ((ResistorBlockEntity) level.getBlockEntity(PG_LOAD)).getValue();
            h.assertTrue(Math.abs(resistance - 9) < 0.001, "Restart lost the native PG resistor setting");
            close(h, pgVoltage(h, PG_LOAD), 10 * resistance / (resistance + wireResistance + 0.001), "Persisted PG wire resistance");
            close(h, InfrastructureSavedData.load(level).ticker.lastResults.getVoltageAt(CEE_LOAD, 0, 1), 17, "Persisted CEE wires");
            var cap = DevicesSavedData.load(level).getDevice(CAPACITOR, CapacitorDevice.class);
            close(h, cap.lastVoltage, 10, "Capacitor charge history");
            identity[0] = Bridges.get(level).branchIdentity(new InWorldNode(0, CEE_LOAD), new InWorldNode(1, CEE_LOAD));
            h.assertTrue(identity[0] != null, "Restored load has no compatibility stamp");
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(identity[0] == Bridges.get(level).branchIdentity(new InWorldNode(0, CEE_LOAD), new InWorldNode(1, CEE_LOAD)),
                    "Restored topology duplicated PG stamps on unchanged ticks");
            DynamicGameTests.audit(h);
            level.getDataStorage().save();
            level.getChunkSource().save(true);
            h.succeed();
        });
    }

    private static double pgVoltage(GameTestHelper h, BlockPos pos) {
        return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
    }
    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) < 0.15, what + ": expected " + expected + ", got " + actual);
    }
}
