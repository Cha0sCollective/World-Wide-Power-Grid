package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.extension.NativeApiReadings;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;

/** Real PG probe clicks and inventory synchronization, separate from general acceptance. */
@GameTestHolder("wwpg_meter_sampling")
@PrefixGameTestTemplate(false)
public final class NativeHandheldGameTests {
    @BeforeBatch(batch = "wwpg_handheld_sampling")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 110)
    public static void meterKeepsInterpolatedConnectorVoltage(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1); var middle = new BlockPos(4, 2, 1); var load = new BlockPos(7, 2, 1);
        place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, middle, ModdedBlocks.WIRE_CONNECTOR.get()); place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        var a = h.absolutePos(source); var m = h.absolutePos(middle); var b = h.absolutePos(load);
        var probe = new Probe(h, a);
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(20);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(100);
            WiringGameTests.connect(h, a, 0, m, 0, true); WiringGameTests.connect(h, m, 0, b, 0, true);
            WiringGameTests.connect(h, a, 1, b, 1, true);
        });
        h.runAtTickTime(30, () -> {
            var node = new BlockWireEndpoint(m, 0).getNode(h.getLevel());
            h.assertTrue(node.getNetwork() == null && GlobalElectricNetworks.getWorldNetworks(h.getLevel()).findLineMiddle(node) != null,
                    "Fixture did not exercise an interpolated wire connector");
            probe.click(m, 0); probe.click(a, 1); probe.matches(m, 0, a, 1, 19);
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(35);
        });
        h.runAtTickTime(50, () -> probe.matches(m, 0, a, 1, 34));
        h.runAtTickTime(60, () -> {
            var line = GlobalElectricNetworks.getWorldNetworks(h.getLevel()).findLineMiddle(new BlockWireEndpoint(m, 0).getNode(h.getLevel()));
            NativeApiReadings.invalidate(h.getLevel(), line.getNode1().getNetwork());
            probe.unavailable();
        });
        h.runAtTickTime(65, () -> probe.matches(m, 0, a, 1, 34));
        h.runAtTickTime(80, () -> { DynamicGameTests.audit(h); h.succeed(); });
    }

    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 130)
    public static void meterReadsBatteryThroughDeviceConnector(GameTestHelper h) {
        var battery = new BlockPos(2, 2, 2); var connector = battery.above(); var load = new BlockPos(6, 2, 2);
        place(h, battery, ModdedBlocks.BATTERY.get()); place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(connector, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
        var c = h.absolutePos(connector); var b = h.absolutePos(load); var probe = new Probe(h, c);
        h.runAtTickTime(5, () -> {
            var entity = (BatteryBlockEntity) h.getBlockEntity(battery); entity.setEnergy(entity.getCapacity() * .8);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            WiringGameTests.connect(h, c, 0, b, 0, true); WiringGameTests.connect(h, c, 1, b, 1, true);
        });
        h.runAtTickTime(30, () -> {
            probe.click(c, 0); probe.click(c, 1); probe.matches(c, 0, c, 1, 10);
            ((BatteryBlockEntity) h.getBlockEntity(battery)).setEnergy(((BatteryBlockEntity) h.getBlockEntity(battery)).getCapacity() * .2);
        });
        h.runAtTickTime(50, () -> {
            probe.matches(c, 0, c, 1, 5);
            // A terminal on a live proxy still belongs to the battery's solve.
            NativeApiReadings.invalidate(h.getLevel(), new BlockWireEndpoint(c, 0).getNode(h.getLevel()).getNetwork());
            probe.unavailable();
        });
        h.runAtTickTime(55, () -> probe.matches(c, 0, c, 1, 5));
        h.runAtTickTime(65, () -> {
            h.getLevel().setBlockAndUpdate(h.absolutePos(battery), Blocks.AIR.defaultBlockState());
            h.assertTrue(ElectricalMeasurements.readTerminal(h.getLevel(), c, 0).status() != MeasurementStatus.VALID,
                    "Connector retained its removed battery's accepted snapshot");
            probe.removed();
        });
        h.runAtTickTime(85, () -> { DynamicGameTests.audit(h); h.succeed(); });
    }

    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 140)
    public static void meterReadsRemoteMultiblockBatteryTerminal(GameTestHelper h) {
        var origin = new BlockPos(1, 2, 4); var connector = origin.offset(2, 3, 2); var load = new BlockPos(7, 2, 6);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++)
            h.setBlock(origin.offset(x, y, z), ModdedBlocks.BATTERY.get());
        h.setBlock(connector, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
        place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        var c = h.absolutePos(connector); var b = h.absolutePos(load); var probe = new Probe(h, c);
        h.runAtTickTime(15, () -> {
            var part = (org.patryk3211.powergrid.electricity.battery.MultiBlockBatteryEntity) h.getBlockEntity(origin);
            var controller = part.getControllerBE();
            h.assertTrue(controller != null && controller.getSize() == 27, "Fixture battery did not form a 3x3x3 bank");
            controller.setEnergy(controller.getCapacity() * .8);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            WiringGameTests.connect(h, c, 0, b, 0, true); WiringGameTests.connect(h, c, 1, b, 1, true);
        });
        h.runAtTickTime(40, () -> { probe.click(c, 0); probe.click(c, 1); probe.matches(c, 0, c, 1, 10); });
        h.runAtTickTime(50, () -> {
            // PG refreshes the physical connector's aliases when its owning
            // multiblock is restored or its behaviour is replaced.
            var entity = (org.patryk3211.powergrid.electricity.base.ElectricBlockEntity) h.getBlockEntity(connector);
            ((org.patryk3211.powergrid.electricity.base.ProxyElectricBehaviour) entity.getElectricBehaviour()).refreshEndpoints();
        });
        h.runAtTickTime(60, () -> {
            var world = GlobalElectricNetworks.getWorldNetworks(h.getLevel());
            int nodes = world.globalExternalNodes.size(), circuits = world.subnetworks.size();
            for (int i = 0; i < 10; i++) probe.matches(c, 0, c, 1, 10);
            h.assertTrue(nodes == world.globalExternalNodes.size() && circuits == world.subnetworks.size(),
                    "Reading a restored proxy altered the native graph");
            var reversed = new Probe(h, c); reversed.click(c, 1); reversed.click(c, 0); reversed.matches(c, 1, c, 0, 10);
        });
        h.runAtTickTime(65, () -> {
            NativeApiReadings.invalidate(h.getLevel(), new BlockWireEndpoint(c, 0).getNode(h.getLevel()).getNetwork());
            probe.unavailable();
        });
        h.runAtTickTime(70, () -> probe.matches(c, 0, c, 1, 10));
        h.runAtTickTime(80, () -> { DynamicGameTests.audit(h); h.succeed(); });
    }

    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 20)
    public static void readingFreshConnectorDoesNotInitializeOrDestroyIt(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2); h.setBlock(pos.below(), Blocks.STONE);
        h.runAtTickTime(5, () -> {
            h.setBlock(pos, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
            var absolute = h.absolutePos(pos); var entity = h.getBlockEntity(pos);
            var world = GlobalElectricNetworks.getWorldNetworks(h.getLevel());
            int nodes = world.globalExternalNodes.size(), circuits = world.subnetworks.size();
            for (int i = 0; i < 10; i++)
                h.assertTrue(ElectricalMeasurements.readTerminal(h.getLevel(), absolute, 0).status() != MeasurementStatus.VALID,
                        "Unprepared connector exposed a reading");
            h.assertTrue(h.getBlockEntity(pos) == entity && nodes == world.globalExternalNodes.size()
                    && circuits == world.subnetworks.size(), "Read-only query initialized or retired a connector");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 360)
    public static void generatorConnectorProbesFollowPoweredCommutator(GameTestHelper h) {
        var brush = new BlockPos(3, 2, 2); var rotor = brush.south(); var clutch = rotor.south(); var drive = clutch.south();
        var coil = brush.above(2); var field = new BlockPos(1, 2, 1); var load = new BlockPos(6, 2, 6);
        var positive = new BlockPos(5, 2, 2); var negative = new BlockPos(7, 2, 2); var gauge = new BlockPos(7, 2, 5);
        h.setBlock(brush, ModdedBlocks.GENERATOR_COMMUTATOR.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(rotor, ModdedBlocks.GENERATOR_LARGE_INDUCTION_ROTOR.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Z));
        h.setBlock(clutch, ModdedBlocks.GENERATOR_CLUTCH.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.SOUTH));
        h.setBlock(drive, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH));
        StationaryGeneratorWorkflowGameTests.installCoil(h, coil, Direction.SOUTH, Direction.Axis.Y);
        h.setBlock(coil.above(), ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
        place(h, field, com.george_vi.electroenergetics.CEEBlocks.CREATIVE_BATTERY.get());
        place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get()); place(h, gauge, ModdedBlocks.VOLTAGE_METER.get());
        place(h, positive, ModdedBlocks.WIRE_CONNECTOR.get()); place(h, negative, ModdedBlocks.WIRE_CONNECTOR.get());
        var p = h.absolutePos(positive); var n = h.absolutePos(negative); var a = h.absolutePos(brush); var b = h.absolutePos(load);
        var probe = new Probe(h, p); double[] first = {0};
        h.runAtTickTime(10, () -> {
            StationaryEquipmentGameTests.source(h, field, 5);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            generatorSpeed(h, drive, 64);
            WiringGameTests.connect(h, h.absolutePos(field), 1, h.absolutePos(coil.above()), 0, true);
            WiringGameTests.connect(h, h.absolutePos(field), 0, h.absolutePos(coil.above()), 1, false);
            WiringGameTests.connect(h, a, 0, p, 0, true); WiringGameTests.connect(h, p, 0, b, 0, true);
            WiringGameTests.connect(h, a, 1, n, 0, true); WiringGameTests.connect(h, n, 0, b, 1, true);
            WiringGameTests.connect(h, b, 0, h.absolutePos(gauge), 0, true);
            WiringGameTests.connect(h, b, 1, h.absolutePos(gauge), 1, true);
        });
        h.runAtTickTime(130, () -> {
            h.assertTrue(new BlockWireEndpoint(p, 0).getNode(h.getLevel()).getNetwork() == null,
                    "Generator fixture did not retain compressed connector nodes");
            first[0] = ((org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity) h.getBlockEntity(gauge)).getValue();
            h.assertTrue(Math.abs(first[0]) > .1, "Real commutator did not power the gauge: " + first[0]);
            probe.click(p, 0); probe.click(n, 0); probe.matches(p, 0, n, 0, .1);
            BoardComponentGameTests.near(h, probe.meter.getMeasurement(h.getLevel(), probe.stack), first[0], .05, "Generator meter/gauge agreement");
            generatorSpeed(h, drive, 128);
        });
        h.runAtTickTime(240, () -> {
            probe.matches(p, 0, n, 0, Math.abs(first[0]) * 1.7);
            StationaryEquipmentGameTests.source(h, field, 0);
        });
        h.runAtTickTime(330, () -> {
            probe.matches(p, 0, n, 0, -1);
            h.assertTrue(Math.abs(probe.meter.getMeasurement(h.getLevel(), probe.stack)) < Math.abs(first[0]) * .05,
                    "Meter retained voltage after excitation stopped");
            h.setBlock(drive, Blocks.AIR); DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static void generatorSpeed(GameTestHelper h, BlockPos drive, int speed) {
        ((com.simibubi.create.foundation.blockEntity.SmartBlockEntity) h.getBlockEntity(drive)).getBehaviour(
                com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(speed);
    }

    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 100)
    public static void existingMixedTerminalMeter(GameTestHelper h) { HandheldMeterGameTests.multimeterReadsCeeAndMixedTerminals(h); }
    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 100)
    public static void existingCeeWireMeter(GameTestHelper h) { HandheldMeterGameTests.multimeterReadsCeeWireAndClearsCutConnection(h); }
    @GameTest(template = "empty", batch = "wwpg_handheld_sampling", timeoutTicks = 100)
    public static void existingPgWireClamp(GameTestHelper h) { HandheldMeterGameTests.clampAttachesToPgWireAndReleases(h); }

    private static void place(GameTestHelper h, BlockPos pos, net.minecraft.world.level.block.Block block) {
        h.setBlock(pos.below(), Blocks.STONE); h.setBlock(pos, block);
    }

    private static final class Probe {
        private final GameTestHelper h;
        private final Player player;
        private final ItemStack stack = ModdedItems.MULTIMETER.asStack();
        private final MultimeterItem meter = (MultimeterItem) stack.getItem();
        Probe(GameTestHelper h, BlockPos pos) {
            this.h = h; player = h.makeMockPlayer(GameType.CREATIVE); player.setPos(pos.getCenter());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        void click(BlockPos pos, int id) {
            var endpoint = new BlockWireEndpoint(pos, id);
            var result = meter.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(endpoint.getExactPosition(h.getLevel()), Direction.UP, pos, false)));
            h.assertTrue(result.consumesAction(), "PG meter rejected connector probe");
        }
        void matches(BlockPos a, int ia, BlockPos b, int ib, double minimum) {
            var result = ElectricalMeasurements.readVoltageBetween(h.getLevel(), a, ia, b, ib);
            h.assertTrue(result.status() == MeasurementStatus.VALID, "Probe snapshot unavailable: " + result.status() + " " + result.detail());
            double expected = result.voltage().orElseThrow().lastVoltageVolts();
            h.assertTrue(Math.abs(expected) > minimum, "Fixture is not powered: " + expected);
            meter.inventoryTick(stack, h.getLevel(), player, 0, true);
            BoardComponentGameTests.near(h, meter.getMeasurement(h.getLevel(), stack), expected, .002, "Persistent PG connector meter voltage");
        }
        void unavailable() {
            meter.inventoryTick(stack, h.getLevel(), player, 0, true);
            h.assertTrue(Float.isNaN(meter.getMeasurement(h.getLevel(), stack)), "Unavailable solve retained a numerical meter reading");
        }
        void removed() {
            meter.inventoryTick(stack, h.getLevel(), player, 0, true);
            var data = MultimeterItem.getModeData(stack);
            h.assertTrue(Float.isNaN(meter.getMeasurement(h.getLevel(), stack)) || !data.contains("Pos") && !data.contains("Neg"),
                    "Removed battery retained an available selection");
        }
    }
}
