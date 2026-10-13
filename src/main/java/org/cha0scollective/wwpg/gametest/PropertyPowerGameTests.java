package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** A real mixed circuit verifies that optimized native property power obeys restoration masking. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class PropertyPowerGameTests {
    @BeforeBatch(batch = "wwpg_loading_property_power")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_loading_property_power", timeoutTicks = 400)
    public static void propertyPowerIsMaskedDuringRestorationAndResumesWithSignedSupply(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1); var resistor = new BlockPos(4, 2, 1);
        h.setBlock(source.below(), Blocks.STONE); h.setBlock(resistor.below(), Blocks.STONE);
        h.setBlock(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(resistor, CEEBlocks.CREATIVE_RESISTOR.get());
        var level = h.getLevel(); var sourcePos = h.absolutePos(source); var loadPos = h.absolutePos(resistor);
        PropertyCurrentSourceWire[] branch = {null}; ElectricalNetwork[] network = {null};
        boolean[] wired = {false}; int[] healthy = {0}, elapsed = {-1}, held = {0};
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(sourcePos)).setValue(10);
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)
                    level.getBlockEntity(loadPos)).setResistance(1000);
            WiringGameTests.connect(h, sourcePos, 0, loadPos, 0, true);
            WiringGameTests.connect(h, sourcePos, 1, loadPos, 1, false);
            wired[0] = true;
        });
        h.onEachTick(() -> {
            if (!wired[0]) return;
            var reading = ElectricalMeasurements.readVoltageBetween(level, loadPos, 0, loadPos, 1);
            if (elapsed[0] < 0) {
                if (reading.status() != MeasurementStatus.VALID
                        || Math.abs(reading.voltage().orElseThrow().meanVoltageVolts()) < 9) { healthy[0] = 0; return; }
                if (++healthy[0] < 10) return;
                network[0] = new BlockWireEndpoint(loadPos, 0).getNode(level).getNetwork();
                branch[0] = ((ElectricalNetworkAccessor) network[0]).wwpg$wires().stream()
                        .filter(wire -> wire instanceof PropertyCurrentSourceWire && wire.internalPower() > .09)
                        .map(wire -> (PropertyCurrentSourceWire) wire).findFirst().orElseThrow();
                elapsed[0] = 0;
            }
            int step = ++elapsed[0];
            if (step >= 2 && step <= 12 && RestorationCoordinator.paused(network[0])) {
                held[0]++;
                h.assertTrue(reading.status() == MeasurementStatus.NOT_READY, "Paused property exposed a valid stale reading");
                h.assertTrue(branch[0].current() == 0 && branch[0].potentialDifference() == 0
                                && branch[0].internalPower() == 0 && branch[0].power() == 0,
                        "Optimized property power bypassed the native restoration mask");
            }
            if (step <= 12) RestorationCoordinator.restoring(level, new ChunkPos(sourcePos));
            if (step == 45) {
                h.assertTrue(held[0] > 0, "Fixture never observed an actual loading pause");
                h.assertTrue(reading.status() == MeasurementStatus.VALID && branch[0].internalPower() > .09,
                        "Property power did not resume after restoration");
                h.assertTrue(branch[0].getNetwork() == network[0], "Restoration replaced the property stamp owner");
                ((CreativeSourceBlockEntity) level.getBlockEntity(sourcePos)).setValue(-10);
            }
            if (step == 60) {
                h.assertTrue(reading.status() == MeasurementStatus.VALID, "Reversed source did not produce accepted measurements");
                BoardComponentGameTests.near(h, reading.voltage().orElseThrow().meanVoltageVolts(), -10, .02,
                        "Reversed property supply voltage");
                BoardComponentGameTests.near(h, branch[0].internalPower(), .1, .0001,
                        "Resistive property power changed sign with source polarity");
                ((CreativeSourceBlockEntity) level.getBlockEntity(sourcePos)).setValue(0);
            }
            if (step == 75) {
                h.assertTrue(reading.status() == MeasurementStatus.VALID, "Zero source lost accepted measurement availability");
                BoardComponentGameTests.near(h, branch[0].internalPower(), 0, 1e-9, "Zero source retained property power");
                BoardComponentGameTests.near(h, branch[0].power(), 0, 1e-9, "Zero source retained aggregate property power");
                DynamicGameTests.audit(h); h.succeed();
            }
        });
    }
}
