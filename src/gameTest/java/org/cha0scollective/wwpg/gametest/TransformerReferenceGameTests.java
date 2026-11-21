package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.OwnedWireRemoval;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Check stock CEE's signed node samples and turns ratio together. */
@GameTestHolder("wwpg_cee_transformer_reference")
@PrefixGameTestTemplate(false)
public final class TransformerReferenceGameTests {
    private static final String BATCH = "wwpg_cee_transformer_reference";
    @BeforeBatch(batch = BATCH)
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=130)
    public static void floatingGroundedAndReleasedSecondaryMatchesStock(GameTestHelper h) {
        var f = new Fixture(h);
        f.start();
        h.runAtTickTime(25, () -> {
            f.expect(0, 5000);
            f.wire(f.transformer, 3, f.ground, 0);
        });
        h.runAtTickTime(45, () -> {
            f.expect(-5000, 0);
            f.unwire(f.transformer, 3, f.ground, 0);
        });
        h.runAtTickTime(65, () -> {
            f.expect(0, 5000);
            h.succeed();
        });
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=130)
    public static void primaryEarthDoesNotRemoveFloatingSecondaryReference(GameTestHelper h) {
        var f = new Fixture(h);
        f.start();
        h.runAtTickTime(15, () -> f.wire(f.source, 0, f.ground, 0));
        h.runAtTickTime(35, () -> {
            f.expect(0, 5000);
            f.wire(f.transformer, 3, f.ground, 0);
        });
        h.runAtTickTime(55, () -> {
            f.expect(-5000, 0);
            f.unwire(f.transformer, 3, f.ground, 0);
        });
        h.runAtTickTime(75, () -> {
            f.expect(0, 5000);
            h.succeed();
        });
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=130)
    public static void nativePgBondJoinsWindingReferencesAndRemovalSeparatesThem(GameTestHelper h) {
        var f = new Fixture(h);
        f.start();
        final ElectricWire[] bond = {null};
        h.runAtTickTime(25, () -> {
            f.expect(0, 5000);
            bond[0] = GlobalElectricNetworks.makeSimpleConnection(h.getLevel(),
                    new BlockWireEndpoint(f.source, 0), new BlockWireEndpoint(f.transformer, 3), .001f);
        });
        h.runAtTickTime(45, () -> {
            f.expect(-5000, 0);
            OwnedWireRemoval.remove(bond[0], GlobalElectricNetworks.getWorldNetworks(h.getLevel()).globalGraph);
        });
        h.runAtTickTime(65, () -> {
            f.expect(0, 5000);
            h.succeed();
        });
    }

    @GameTest(template="empty",batch=BATCH,timeoutTicks=100)
    public static void cascadedIsolatedWindingsEachKeepStockReference(GameTestHelper h) {
        var f = new Fixture(h);
        f.start();
        var second = h.absolutePos(new BlockPos(7, 2, 2));
        h.getLevel().setBlockAndUpdate(second.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(second, CEEBlocks.TRANSFORMER.getDefaultState());
        h.runAtTickTime(8, () -> {
            DevicesSavedData.load(h.getLevel()).getDevice(second, TransformerDevice.class).ratio = 2.5;
            f.wire(f.transformer, 2, second, 0);
            f.wire(f.transformer, 3, second, 1);
        });
        h.runAtTickTime(35, () -> {
            f.expect(0, 5000);
            f.expectSamples(second, 2, 0, "Cascaded secondary reference");
            f.expectSamples(second, 3, 2000, "Cascaded secondary voltage");
            h.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos source, transformer, ground;

        Fixture(GameTestHelper h) {
            this.h = h;
            source = h.absolutePos(new BlockPos(1, 2, 1));
            transformer = h.absolutePos(new BlockPos(4, 2, 3));
            ground = h.absolutePos(new BlockPos(7, 1, 4));
        }

        void start() {
            for (var pos : new BlockPos[]{source, transformer, ground})
                h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(source, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            h.getLevel().setBlockAndUpdate(transformer, CEEBlocks.TRANSFORMER.getDefaultState());
            h.getLevel().setBlockAndUpdate(ground, CEEBlocks.GROUND_ROD.getDefaultState());
            h.runAtTickTime(5, () -> {
                DevicesSavedData.load(h.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage = 23_000;
                DevicesSavedData.load(h.getLevel()).getDevice(transformer, TransformerDevice.class).ratio = 4.6;
                wire(source, 0, transformer, 0);
                wire(source, 1, transformer, 1);
            });
        }

        void wire(BlockPos a, int ap, BlockPos b, int bp) {
            WiringGameTests.connect(h, a, ap, b, bp, false);
        }

        void unwire(BlockPos a, int ap, BlockPos b, int bp) {
            InfrastructureSavedData.load(h.getLevel())
                    .removeConnection(new InWorldNodeConnection(new InWorldNode(ap, a), new InWorldNode(bp, b)));
        }

        double expectSamples(BlockPos pos, int pin, double expected, String what) {
            var results = InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
            var values = results.getVoltages(new InWorldNode(pin, pos));
            h.assertTrue(values.length == 16, "Missing full node sample history");
            for (double actual : values) near(actual, expected, what);
            return values[values.length - 1];
        }

        void near(double actual, double expected, String what) {
            h.assertTrue(Math.abs(actual - expected) < .1, what + ": expected " + expected + ", got " + actual);
        }

        void expect(double first, double second) {
            expectSamples(source, 0, 0, "Battery reference");
            expectSamples(source, 1, 23_000, "Battery voltage");
            double a = expectSamples(transformer, 2, first, "Secondary first node");
            double b = expectSamples(transformer, 3, second, "Secondary second node");
            near(b - a, 5000, "Secondary differential");
            WorldWidePowerGrid.LOGGER.info("WWPG_TRANSFORMER_REFERENCE first={} second={} differential={}", a, b, b - a);
        }
    }
}
