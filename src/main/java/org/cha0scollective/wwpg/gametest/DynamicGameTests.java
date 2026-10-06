package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.content.electronic_components.inductor.InductorDevice;
import com.george_vi.electroenergetics.content.accumulator.AccumulatorDevice;
import com.george_vi.electroenergetics.foundation.electrical_properties.AccumulatorProperties;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.SolverAudit;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class DynamicGameTests {
    @BeforeBatch(batch = "defaultBatch") public static void substeps(ServerLevel level) {
        ModdedConfigs.server().electricity.solver.multiTicks.set(16);
    }

    @GameTest(template = "empty", timeoutTicks = 80) public static void transformerRatioChanges(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var t = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, t, b}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(t, CEEBlocks.TRANSFORMER.get()); h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var transformer = h.absolutePos(t); var load = h.absolutePos(b); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(10);
            DevicesSavedData.load(level).getDevice(transformer, TransformerDevice.class).ratio = 2;
            WiringGameTests.connect(h, source, 0, transformer, 0, true);
            WiringGameTests.connect(h, source, 1, transformer, 1, true);
            WiringGameTests.connect(h, transformer, 2, load, 0, true);
            WiringGameTests.connect(h, transformer, 3, load, 1, true);
        });
        h.runAtTickTime(15, () -> {
            close(h, pgVoltage(h, load), 5 / 1.0125, 0.05, "Transformer step-down");
            DevicesSavedData.load(level).getDevice(transformer, TransformerDevice.class).ratio = 0.5;
        });
        h.runAtTickTime(25, () -> {
            close(h, pgVoltage(h, load), 20 / 1.05, 0.05, "Transformer step-up");
            DevicesSavedData.load(level).getDevice(transformer, TransformerDevice.class).ratio = -2;
        });
        h.runAtTickTime(35, () -> {
            close(h, pgVoltage(h, load), -5 / 1.0125, 0.05, "Transformer winding polarity");
            audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80) public static void acRmsIntoPgLoad(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
        h.setBlock(a.below(), Blocks.STONE); h.setBlock(b.below(), Blocks.STONE);
        h.setBlock(a, CEEBlocks.CREATIVE_BATTERY.get()); h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var load = h.absolutePos(b); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            var device = DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class);
            device.voltage = 10; device.acFrequency = 50;
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(10);
            // CEE wires ensure the load terminals enter the CEE RMS result history.
            WiringGameTests.connect(h, source, 1, load, 0, false);
            WiringGameTests.connect(h, source, 0, load, 1, false);
        });
        h.runAtTickTime(20, () -> {
            double rms = InfrastructureSavedData.load(level).ticker.lastResults.getVoltageAt(load, 0, 1);
            close(h, rms, 10 / Math.sqrt(2), 0.1, "AC RMS history");
            audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80) public static void capacitorChargesOnceAndDischarges(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var r = new BlockPos(3, 2, 1); var c = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, r, c}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(r, ModdedBlocks.CREATIVE_RESISTOR.get()); h.setBlock(c, CEEBlocks.CAPACITOR.get());
        var source = h.absolutePos(a); var resistor = h.absolutePos(r); var capacitor = h.absolutePos(c); var level = h.getLevel();
        double[] previous = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(resistor)).setValue(10);
            var device = DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class);
            device.capacitance = 0.1; device.lastVoltage = 0;
            WiringGameTests.connect(h, source, 0, resistor, 0, true);
            WiringGameTests.connect(h, resistor, 1, capacitor, 0, true);
            WiringGameTests.connect(h, capacitor, 1, source, 1, true);
        });
        h.runAtTickTime(10, () -> previous[0] = DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class).lastVoltage);
        h.runAtTickTime(20, () -> {
            var device = DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class);
            double decay = Math.pow(1 / (1 + (0.05 / 16) / 1.01), 160);
            close(h, device.lastVoltage, 10 + (previous[0] - 10) * decay, 0.1, "RC charging / exactly-once advancement");
            previous[0] = device.lastVoltage;
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(0);
        });
        h.runAtTickTime(35, () -> {
            double voltage = DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class).lastVoltage;
            h.assertTrue(voltage > 0 && voltage < previous[0] * 0.6, "Capacitor failed to discharge: " + voltage);
            audit(h); h.succeed();
        });
    }

    private static double pgVoltage(GameTestHelper h, BlockPos pos) {
        return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
    }

    @GameTest(template = "empty", timeoutTicks = 80) public static void inductorAdvancesOnceIntoPgResistor(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var r = new BlockPos(3, 2, 1); var l = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, r, l}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(r, ModdedBlocks.CREATIVE_RESISTOR.get()); h.setBlock(l, CEEBlocks.INDUCTOR.get());
        var source = h.absolutePos(a); var resistor = h.absolutePos(r); var inductor = h.absolutePos(l); var level = h.getLevel();
        double[] previous = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(resistor)).setValue(10);
            var device = DevicesSavedData.load(level).getDevice(inductor, InductorDevice.class);
            device.inductance = 1; device.lastCurrent = 0;
            WiringGameTests.connect(h, source, 0, resistor, 0, true);
            WiringGameTests.connect(h, resistor, 1, inductor, 0, true);
            WiringGameTests.connect(h, inductor, 1, source, 1, true);
        });
        h.runAtTickTime(10, () -> previous[0] = DevicesSavedData.load(level).getDevice(inductor, InductorDevice.class).lastCurrent);
        h.runAtTickTime(20, () -> {
            var device = DevicesSavedData.load(level).getDevice(inductor, InductorDevice.class);
            double decay = Math.pow(1 / (1 + (0.05 / 16) * 10.1), 160);
            close(h, device.lastCurrent, 10 / 10.1 + (previous[0] - 10 / 10.1) * decay, 0.03, "RL current / exactly-once advancement");
            previous[0] = device.lastCurrent;
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(0);
        });
        h.runAtTickTime(35, () -> {
            double current = DevicesSavedData.load(level).getDevice(inductor, InductorDevice.class).lastCurrent;
            h.assertTrue(current >= 0 && current < previous[0] * 0.02, "Inductor history failed to decay: " + current);
            audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90) public static void accumulatorChargesFromPgAndPowersPgLoad(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
        h.setBlock(a.below(), Blocks.STONE); h.setBlock(b.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(b, CEEBlocks.ACCUMULATOR.get());
        var source = h.absolutePos(a); var accumulator = h.absolutePos(b); var level = h.getLevel();
        double[] charge = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(26);
            var device = DevicesSavedData.load(level).getDevice(accumulator, AccumulatorDevice.class);
            device.cell1Charge = AccumulatorProperties.getNominalCharge() * 0.5;
            // GameTest actions run after prepare; seed the already-prepared history too.
            device.properties1.storedCharge = device.cell1Charge;
            charge[0] = device.cell1Charge;
            WiringGameTests.connect(h, source, 0, accumulator, 0, false);
            WiringGameTests.connect(h, source, 1, accumulator, 1, false);
        });
        h.runAtTickTime(25, () -> {
            var device = DevicesSavedData.load(level).getDevice(accumulator, AccumulatorDevice.class);
            h.assertTrue(device.cell1Charge > charge[0], "PG source failed to charge CEE accumulator: charge=" + device.cell1Charge
                    + ", initial=" + charge[0] + ", voltage=" + InfrastructureSavedData.load(level).ticker.lastResults.getVoltageAt(accumulator, 0, 1)
                    + ", source=" + pgVoltage(h, source));
            charge[0] = device.cell1Charge;
            h.setBlock(a, Blocks.AIR);
        });
        h.runAtTickTime(30, () -> {
            h.setBlock(a, ModdedBlocks.CREATIVE_RESISTOR.get());
        });
        h.runAtTickTime(35, () -> {
            ((ResistorBlockEntity) level.getBlockEntity(source)).setValue(10);
            WiringGameTests.connect(h, accumulator, 0, source, 0, false);
            WiringGameTests.connect(h, accumulator, 1, source, 1, false);
        });
        h.runAtTickTime(55, () -> {
            var device = DevicesSavedData.load(level).getDevice(accumulator, AccumulatorDevice.class);
            h.assertTrue(device.cell1Charge < charge[0], "CEE accumulator failed to discharge into PG load");
            h.assertTrue(pgVoltage(h, source) > 20, "PG load received no accumulator voltage");
            audit(h); h.succeed();
        });
    }
    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String name) {
        h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) < tolerance, name + " expected " + expected + ", got " + actual);
    }
    public static void audit(GameTestHelper h) {
        h.assertTrue(SolverAudit.ceeAttempts() == 0, "CEE attempted a second solve");
        var expected = CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE"));
        h.assertTrue(Bridges.get(h.getLevel()).backendCounts().getOrDefault(expected, 0L) > 0,
                "The requested PG backend never solved a network: " + expected);
    }
}
