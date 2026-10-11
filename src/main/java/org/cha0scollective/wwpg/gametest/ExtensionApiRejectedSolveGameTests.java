package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.Objects;
import java.util.function.Function;

/** A real backend returns a finite nonconverged trial state without throwing. */
@GameTestHolder("wwpg_api")
@PrefixGameTestTemplate(false)
public final class ExtensionApiRejectedSolveGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nonthrowingRejectedSolveRollsBackCeeAndApiButKeepsHealthyCircuit(GameTestHelper helper) {
        var supplyRelative = new BlockPos(1, 2, 1);
        var ballastRelative = new BlockPos(3, 2, 1);
        var ceeCapacitorRelative = new BlockPos(5, 2, 1);
        var apiCapacitorRelative = new BlockPos(3, 2, 4);
        var healthySupplyRelative = new BlockPos(1, 2, 7);
        var healthyApiLoadRelative = new BlockPos(4, 2, 7);
        var healthyCeeLoadRelative = new BlockPos(6, 2, 7);
        for (var pos : new BlockPos[]{supplyRelative, ballastRelative, ceeCapacitorRelative, apiCapacitorRelative,
                healthySupplyRelative, healthyApiLoadRelative, healthyCeeLoadRelative}) helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(supplyRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        helper.setBlock(ballastRelative, ModdedBlocks.CREATIVE_RESISTOR.get());
        helper.setBlock(ceeCapacitorRelative, CEEBlocks.CAPACITOR.get());
        example(helper, apiCapacitorRelative, "capacitor");
        helper.setBlock(healthySupplyRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        example(helper, healthyApiLoadRelative, "load");
        helper.setBlock(healthyCeeLoadRelative, CEEBlocks.RESISTOR.get());
        var supply = helper.absolutePos(supplyRelative);
        var ballast = helper.absolutePos(ballastRelative);
        var ceeCapacitor = helper.absolutePos(ceeCapacitorRelative);
        var apiCapacitor = helper.absolutePos(apiCapacitorRelative);
        var healthySupply = helper.absolutePos(healthySupplyRelative);
        var healthyApiLoad = helper.absolutePos(healthyApiLoadRelative);
        var healthyCeeLoad = helper.absolutePos(healthyCeeLoadRelative);
        var level = helper.getLevel();
        var network = new ElectricalNetwork[1];
        @SuppressWarnings("unchecked") var limits = (Function<Boolean, Integer>[]) new Function<?, ?>[1];
        var apiSaved = new CompoundTag[1];
        var nativeSaved = new double[2];
        var meters = new ApiMeterFixture[1];
        helper.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(supply)).setValue(20);
            ((ResistorBlockEntity) level.getBlockEntity(ballast)).setValue(100);
            DevicesSavedData.load(level).getDevice(ceeCapacitor, CapacitorDevice.class).capacitance = .01;
            ((CreativeSourceBlockEntity) level.getBlockEntity(healthySupply)).setValue(10);
            DevicesSavedData.load(level).getDevice(healthyCeeLoad, ResistorDevice.class).properties = ElectricalProperties.resistor(100);
            WiringGameTests.connect(helper, supply, 0, ballast, 0, true);
            WiringGameTests.connect(helper, ballast, 1, ceeCapacitor, 0, false);
            WiringGameTests.connect(helper, ceeCapacitor, 1, supply, 1, false);
            WiringGameTests.connect(helper, supply, 0, apiCapacitor, 0, true);
            WiringGameTests.connect(helper, supply, 1, apiCapacitor, 1, false);
            for (var load : new BlockPos[]{healthyApiLoad, healthyCeeLoad}) {
                WiringGameTests.connect(helper, healthySupply, 0, load, 0, false);
                WiringGameTests.connect(helper, healthySupply, 1, load, 1, true);
            }
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(ElectricalMeasurements.read(level, apiCapacitor).status() == MeasurementStatus.VALID,
                    "API storage was unhealthy before rejected solve");
            meters[0] = new ApiMeterFixture(helper, supply, 0, apiCapacitor, 0, 1, 1);
            meters[0].assertAvailable("Before rejected solve");
            network[0] = new BlockWireEndpoint(apiCapacitor, 0).getNode(level).getNetwork();
            limits[0] = network[0].maxIterations;
            // Limit the actual native/Java Newton loop, rather than spoofing its
            // convergence flag or throwing from an extension callback. Changing
            // the supply leaves a nonzero pre-update residual after one iteration.
            network[0].maxIterations = hooks -> 1;
            ((CreativeSourceBlockEntity) level.getBlockEntity(supply)).setValue(40);
            ((CreativeSourceBlockEntity) level.getBlockEntity(healthySupply)).setValue(15);
        });
        helper.runAtTickTime(21, () -> {
            assertRejected(helper, apiCapacitor, ceeCapacitor);
            var capacitor = DevicesSavedData.load(level).getDevice(ceeCapacitor, CapacitorDevice.class);
            nativeSaved[0] = capacitor.lastVoltage; nativeSaved[1] = capacitor.temp;
            apiSaved[0] = level.getBlockEntity(apiCapacitor).saveWithoutMetadata(level.registryAccess());
            helper.assertTrue(capacitor.properties.lastVoltage == capacitor.lastVoltage,
                    "Rejected frame retained built-in CEE numerical history ahead of native saved state");
            meters[0].assertUnavailable("Rejected native/Java electrical frame");
            // Reject another complete frame so the next check also observes the
            // previous frame's actual native gameplay/result publication.
            ((CreativeSourceBlockEntity) level.getBlockEntity(supply)).setValue(60);
        });
        helper.runAtTickTime(22, () -> {
            assertRejected(helper, apiCapacitor, ceeCapacitor);
            var capacitor = DevicesSavedData.load(level).getDevice(ceeCapacitor, CapacitorDevice.class);
            helper.assertTrue(capacitor.lastVoltage == nativeSaved[0] && capacitor.temp == nativeSaved[1]
                            && capacitor.properties.lastVoltage == nativeSaved[0],
                    "Rejected CEE frame advanced charge, heating, or numerical history");
            var after = level.getBlockEntity(apiCapacitor).saveWithoutMetadata(level.registryAccess());
            for (var key : new String[]{"StoredVoltage", "StoredCurrent"})
                helper.assertTrue(Objects.equals(apiSaved[0].get(key), after.get(key)), "Rejected API frame changed saved " + key);
            var results = InfrastructureSavedData.load(level).ticker.lastResults;
            helper.assertTrue(results != null && Double.isNaN(results.getVoltageAt(ceeCapacitor, 0, 1)),
                    "Native CEE meter publication exposed rejected numeric voltage");
            helper.assertTrue(Double.isFinite(results.getVoltageAt(healthyCeeLoad, 0, 1))
                            && Math.abs(results.getVoltageAt(healthyCeeLoad, 0, 1) - 15) < .02,
                    "Unrelated healthy native CEE circuit lost result publication");
            helper.assertTrue(ElectricalMeasurements.read(level, healthyApiLoad).status() == MeasurementStatus.VALID,
                    "Unrelated healthy extension was isolated");
            meters[0].assertUnavailable("Second rejected electrical frame");
            network[0].maxIterations = limits[0];
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(ElectricalMeasurements.read(level, apiCapacitor).status() == MeasurementStatus.VALID,
                    "Restoring the native iteration budget did not recover extension results");
            var capacitor = DevicesSavedData.load(level).getDevice(ceeCapacitor, CapacitorDevice.class);
            helper.assertTrue(capacitor.lastVoltage > nativeSaved[0], "CEE storage did not resume after a valid solve");
            meters[0].assertAvailable("Recovered electrical frame");
            DynamicGameTests.audit(helper);
            com.mojang.logging.LogUtils.getLogger().info("API_NONCONVERGENCE_PASSED: rollback=true; healthyCircuit=true; recovered=true");
            helper.succeed();
        });
    }

    private static void assertRejected(GameTestHelper helper, BlockPos apiCapacitor, BlockPos ceeCapacitor) {
        var api = ElectricalMeasurements.read(helper.getLevel(), apiCapacitor);
        helper.assertTrue(api.status() == MeasurementStatus.SOLVE_FAILED && api.electricalState().isEmpty(),
                "Nonthrowing backend rejection exposed accepted API history: " + api.status());
        helper.assertTrue(ElectricalMeasurements.readTerminal(helper.getLevel(), ceeCapacitor, 0).status() == MeasurementStatus.SOLVE_FAILED,
                "Nonthrowing backend rejection exposed accepted native CEE terminal voltage");
    }

    private static void example(GameTestHelper helper, BlockPos relative, String name) {
        var id = ResourceLocation.fromNamespaceAndPath("wwpg_api_example", name);
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Independent API example is required: " + id);
        helper.setBlock(relative, BuiltInRegistries.BLOCK.get(id));
    }
}
