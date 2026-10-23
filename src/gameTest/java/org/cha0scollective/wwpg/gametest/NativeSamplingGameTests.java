package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.TickContext;
import org.cha0scollective.wwpg.api.VoltageMeasurement;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.extension.NativeApiReadings;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Powered native wires exercise reading ownership; manual callbacks test the sampling barrier only. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class NativeSamplingGameTests {
    private static boolean previousSplitting;
    private static Runnable cleanup = () -> { };
    @BeforeBatch(batch = "wwpg_native_sampling")
    public static void configure(ServerLevel level) {
        DynamicGameTests.substeps(level);
        previousSplitting = ModdedConfigs.server().electricity.solver.splittingTransmissionLines.get();
        cleanup = () -> { };
    }

    @AfterBatch(batch = "wwpg_native_sampling")
    public static void restore(ServerLevel level) {
        try { cleanup.run(); }
        finally {
            cleanup = () -> { };
            ModdedConfigs.server().electricity.solver.splittingTransmissionLines.set(previousSplitting);
        }
    }

    @GameTest(template = "empty", batch = "wwpg_native_sampling", timeoutTicks = 160)
    public static void middleReadingsFollowBothNetworkOwnersAndRejectIncompleteFrames(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var middle = new BlockPos(4, 2, 1);
        var load = new BlockPos(7, 2, 1);
        var background = new BlockPos(1, 2, 6);
        var backgroundLoad = new BlockPos(4, 2, 6);
        for (var pos : new BlockPos[]{source, middle, load, background, backgroundLoad}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(middle, ModdedBlocks.WIRE_CONNECTOR.get());
        h.setBlock(load, ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(background, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(backgroundLoad, ModdedBlocks.CREATIVE_RESISTOR.get());
        var a = h.absolutePos(source); var m = h.absolutePos(middle); var b = h.absolutePos(load);
        var bg = h.absolutePos(background); var bgLoad = h.absolutePos(backgroundLoad);
        h.startSequence().thenIdle(5).thenExecute(() -> {
            ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(a)).setValue(20);
            ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(bg)).setValue(7);
            ((ResistorBlockEntity) h.getLevel().getBlockEntity(b)).setValue(100);
            ((ResistorBlockEntity) h.getLevel().getBlockEntity(bgLoad)).setValue(100);
            WiringGameTests.connect(h, a, 0, m, 0, true);
            WiringGameTests.connect(h, m, 0, b, 0, true);
            WiringGameTests.connect(h, a, 1, b, 1, true);
            WiringGameTests.connect(h, bg, 0, bgLoad, 0, true);
            WiringGameTests.connect(h, bg, 1, bgLoad, 1, true);
            var positions = java.util.List.of(a, m, b, bg, bgLoad);
            var entities = positions.stream().map(h.getLevel()::getBlockEntity).toList();
            var owners = positions.stream()
                    .map(pos -> IElectric.getAt(h.getLevel(), pos).getBehaviour(h.getLevel(), pos, h.getLevel().getBlockState(pos)))
                    .toList();
            boolean[] cleaned = {false};
            cleanup = () -> {
                if (cleaned[0]) return;
                owners.forEach(owner -> owner.breakConnections());
                for (int i = 0; i < positions.size(); i++)
                    if (h.getLevel().getBlockEntity(positions.get(i)) == entities.get(i))
                        h.getLevel().setBlockAndUpdate(positions.get(i), Blocks.AIR.defaultBlockState());
                cleaned[0] = true;
            };
        }).thenIdle(25).thenExecute(() -> {
            powered(h, a, bgLoad);
            var line = middleLine(h, m);
            h.assertTrue(line.getNode1().getNetwork() == line.getNode2().getNetwork(), "Fixture did not exercise one network owner");
            replay(h, m, bgLoad, false, false);
            var held = valid(h, m);
            double heldVoltage = held.voltage().orElseThrow().lastVoltageVolts();
            NativeApiReadings.invalidate(h.getLevel(), line.getNode1().getNetwork());
            h.assertTrue(read(h, m).status() == MeasurementStatus.SOLVE_FAILED, "Late rejection left a valid mid-line waveform");
            valid(h, bgLoad);
            BoardComponentGameTests.near(h, held.voltage().orElseThrow().lastVoltageVolts(), heldVoltage, 1e-12,
                    "Previously returned snapshot changed after rejection");
            replay(h, m, bgLoad, true, false);
            var world = GlobalElectricNetworks.getWorldNetworks(h.getLevel());
            var shared = line.getNode1().getNetwork();
            // Short native wires are below PG's weak-coupling threshold. Give this
            // fixture explicit resistive segments and enable PG's native splitting
            // option for this batch. The after-batch hook restores that option.
            ModdedConfigs.server().electricity.solver.splittingTransmissionLines.set(true);
            double resistance = Math.max(1, 2 * ModdedConfigs.server().electricity.solver.transmissionLineThreshold.getF());
            for (var cable : java.util.List.copyOf(world.transmissionLines.values())) {
                if (cable.getNode1().getNetwork() != shared) continue;
                double oldResistance = cable.getResistance();
                for (var segment : cable.segments) segment.setResistance(resistance * segment.getResistance() / oldResistance);
                cable.setResistance(resistance);
                h.assertTrue(org.patryk3211.powergrid.electricity.WorldNetworks.canWeakCouple(cable), "Fixture wire cannot use native weak coupling");
            }
            world.scheduleIslandDiscovery(shared);
        }).thenIdle(25).thenExecute(() -> {
            powered(h, a, bgLoad);
            var line = middleLine(h, m);
            h.assertTrue(line.getNode1().getNetwork() != line.getNode2().getNetwork(), "Native discovery did not exercise two network owners");
            replay(h, m, bgLoad, false, true);
            h.assertTrue(read(h, m).status() == MeasurementStatus.NOT_READY, "Missing final endpoint substep became a valid waveform");
            valid(h, bgLoad);
            replay(h, m, bgLoad, false, false);
            replay(h, m, bgLoad, true, false);
            var held = valid(h, m);
            double heldVoltage = held.voltage().orElseThrow().lastVoltageVolts();
            NativeApiReadings.invalidate(h.getLevel(), line.getNode2().getNetwork());
            h.assertTrue(read(h, m).status() == MeasurementStatus.SOLVE_FAILED, "Second endpoint rejection was lost");
            valid(h, bgLoad);
            BoardComponentGameTests.near(h, held.voltage().orElseThrow().lastVoltageVolts(), heldVoltage, 1e-12,
                    "Published voltage aliased the rejected frame");
        }).thenIdle(3).thenExecute(() -> {
            powered(h, a, bgLoad);
            var result = valid(h, m).voltage().orElseThrow();
            var node = new BlockWireEndpoint(m, 0).getNode(h.getLevel());
            BoardComponentGameTests.near(h, result.lastVoltageVolts(), middleLine(h, m).voltageFor(node), 1e-9,
                    "Next tick did not restore native mid-line readings");
            DynamicGameTests.audit(h);
            // Retire the temporary weak-coupled wires before structures are
            // cleared or the global splitting option is restored for other tests.
            cleanup.run();
        }).thenIdle(3).thenExecute(() -> {
            var world = GlobalElectricNetworks.getWorldNetworks(h.getLevel());
            for (var pos : java.util.List.of(a, m, b, bg, bgLoad)) {
                h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "Temporary native device was not retired");
                for (int id = 0; id < 2; id++)
                    h.assertTrue(!world.globalExternalNodes.containsKey(new BlockWireEndpoint(pos, id)),
                            "Temporary native terminal survived fixture teardown");
            }
        }).thenSucceed();
    }

    private static void powered(GameTestHelper h, BlockPos source, BlockPos backgroundLoad) {
        var supply = ElectricalMeasurements.readVoltageBetween(h.getLevel(), source, 0, source, 1);
        var load = ElectricalMeasurements.readVoltageBetween(h.getLevel(), backgroundLoad, 0, backgroundLoad, 1);
        h.assertTrue(supply.status() == MeasurementStatus.VALID && load.status() == MeasurementStatus.VALID,
                "Real native circuits were not ready: " + supply.status() + "/" + load.status());
        BoardComponentGameTests.near(h, supply.voltage().orElseThrow().lastVoltageVolts(), 20, .02, "Native powered source");
        BoardComponentGameTests.near(h, load.voltage().orElseThrow().lastVoltageVolts(), 7, .02, "Unrelated powered load");
    }

    private static TransmissionLine middleLine(GameTestHelper h, BlockPos pos) {
        var node = new BlockWireEndpoint(pos, 0).getNode(h.getLevel());
        var line = GlobalElectricNetworks.getWorldNetworks(h.getLevel()).findLineMiddle(node);
        h.assertTrue(node.getNetwork() == null && line != null, "Fixture did not exercise an interpolated native terminal");
        return line;
    }

    private static VoltageMeasurement read(GameTestHelper h, BlockPos pos) {
        return ElectricalMeasurements.readTerminal(h.getLevel(), pos, 0);
    }

    private static VoltageMeasurement valid(GameTestHelper h, BlockPos pos) {
        var result = read(h, pos);
        h.assertTrue(result.status() == MeasurementStatus.VALID, "Native terminal unavailable: " + result.status() + " " + result.detail());
        return result;
    }

    private static void replay(GameTestHelper h, BlockPos middle, BlockPos backgroundLoad, boolean reverse, boolean incomplete) {
        var level = h.getLevel();
        var line = middleLine(h, middle);
        var first = line.getNode1().getNetwork(); var second = line.getNode2().getNetwork();
        ElectricalNetwork background = new BlockWireEndpoint(backgroundLoad, 0).getNode(level).getNetwork();
        h.assertTrue(background != first && background != second, "Background circuit shares the tested owner");
        NativeApiReadings.prepare(level, new TickContext(level.getGameTime(), .05, 16));
        for (int step = 0; step < 16; step++) {
            if (reverse) {
                if (!incomplete || step != 15) NativeApiReadings.after(level, second);
                if (first != second) NativeApiReadings.after(level, first);
            } else {
                NativeApiReadings.after(level, first);
                if (first != second && (!incomplete || step != 15)) NativeApiReadings.after(level, second);
            }
            NativeApiReadings.after(level, background);
        }
        NativeApiReadings.finish(level);
        if (!incomplete) {
            var node = new BlockWireEndpoint(middle, 0).getNode(level);
            var measured = valid(h, middle).voltage().orElseThrow();
            double nativeVoltage = line.voltageFor(node);
            BoardComponentGameTests.near(h, measured.lastVoltageVolts(), nativeVoltage, 1e-9, "Last native interpolation");
            BoardComponentGameTests.near(h, measured.meanVoltageVolts(), nativeVoltage, 1e-9, "Mean native interpolation");
            BoardComponentGameTests.near(h, measured.rmsVoltageVolts(), Math.abs(nativeVoltage), 1e-9, "RMS native interpolation");
        }
    }
}
