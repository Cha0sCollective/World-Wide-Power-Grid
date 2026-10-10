package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.converter.ConverterBlock;
import com.george_vi.electroenergetics.content.converter.ConverterDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlock;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

/** Both native converters together, without another FE mod or a compatibility adapter block. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryConversionGameTests {
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void nativeFeConvertersExchangeMixedPower(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var converter = new BlockPos(4, 2, 1);
        var inverter = converter.below();
        var load = new BlockPos(6, 2, 1);
        var control = new BlockPos(1, 2, 4);
        place(h, source, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, control, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(inverter.below(), Blocks.STONE);
        h.setBlock(inverter, ModdedBlocks.FE_INVERTER.getDefaultState().setValue(FEInverterBlock.FACING, Direction.UP));
        h.setBlock(converter, CEEBlocks.CONVERTER.getDefaultState().setValue(ConverterBlock.FACING, Direction.UP));
        h.runAtTickTime(5, () -> {
            var storage = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(inverter), Direction.UP);
            h.assertTrue(storage != null && storage.canReceive() && !storage.canExtract(), "Native inverter FE direction changed");
            h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(inverter), Direction.DOWN) == null,
                    "Inverter accepted FE on a native unsupported side");
            h.assertTrue(storage.getEnergyStored() == 0, "Inverter was not empty before first conversion");
            source(h, source, 100);
            source(h, control, 0);
            resistor(h, load, 1000);
            wire(h, source, 1, converter, 0, true);
            wire(h, source, 0, converter, 1, false);
            wire(h, inverter, 0, load, 0, false);
            wire(h, inverter, 1, load, 1, true);
            wire(h, control, 1, inverter, 2, true);
            wire(h, control, 0, inverter, 1, false);
        });
        double[] output = {0};
        h.runAtTickTime(45, () -> {
            var storage = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(inverter), Direction.UP);
            output[0] = ceeVoltage(h, load);
            h.assertTrue(output[0] > 10 && storage.getEnergyStored() > 0,
                    "CEE electrical input did not pass through native FE conversion to the mixed load: " + output[0]);
            var device = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(converter), ConverterDevice.class);
            h.assertTrue(!device.isSource && device.storedEnergy >= 0, "Native CEE converter input mode changed");
            var saved = h.getBlockEntity(inverter).saveWithoutMetadata(h.getLevel().registryAccess());
            h.getBlockEntity(inverter).loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(storage.getEnergyStored() == saved.getInt("Energy"), "Stored FE changed before the first resumed solve");
            source(h, control, 2 * ModdedConfigs.server().electricity.feInverterControlVoltage.get());
        });
        h.runAtTickTime(90, () -> {
            h.assertTrue(Math.abs(ceeVoltage(h, load)) < output[0] * .1,
                    "CEE control input did not throttle the native FE inverter");
            source(h, source, 0);
            // Test native capacity and simulation semantics with the actual FE capability.
            var storage = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(inverter), Direction.UP);
            int energy = storage.getEnergyStored();
            int accepted = storage.receiveEnergy(Integer.MAX_VALUE, true);
            h.assertTrue(storage.getEnergyStored() == energy && accepted == storage.getMaxEnergyStored() - energy,
                    "Simulated FE receipt changed energy or ignored capacity");
            h.assertTrue(storage.receiveEnergy(Integer.MAX_VALUE, false) == accepted,
                    "Committed FE receipt disagreed with native simulation");
            h.assertTrue(storage.getEnergyStored() == storage.getMaxEnergyStored()
                    && storage.receiveEnergy(1, false) == 0 && storage.extractEnergy(1, false) == 0,
                    "Native FE capacity or direction violated");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 75)
    public static void nativeInverterAdvancesEnergyOncePerGameTick(GameTestHelper h) {
        var inverter = new BlockPos(3, 2, 1);
        var load = new BlockPos(6, 2, 1);
        place(h, inverter, ModdedBlocks.FE_INVERTER.get());
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(5, () -> {
            var face = h.getBlockState(inverter).getValue(FEInverterBlock.FACING);
            var storage = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(inverter), face);
            storage.receiveEnergy(10000, false);
            resistor(h, load, 100000);
            wire(h, inverter, 0, load, 0, false);
            wire(h, inverter, 1, load, 1, true);
        });
        h.runAtTickTime(25, () -> {
            var face = h.getBlockState(inverter).getValue(FEInverterBlock.FACING);
            var storage = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(inverter), face);
            int before = storage.getEnergyStored();
            // The terminal reading belongs to the preceding solve. Predict the next
            // solve from the currently committed buffer instead of using stale voltage.
            double v = before / ModdedConfigs.server().electricity.forgeEnergyPerVolt.getF();
            double watts = before / ModdedConfigs.server().electricity.forgeEnergyPerWatt.getF();
            double resistance = v * v / (2 * watts);
            double i = v / (100000 + resistance);
            int expected = (int) Math.ceil(ModdedConfigs.server().electricity.forgeEnergyPerWatt.getF()
                    * (i * v - i * i * resistance));
            h.assertTrue(expected > 0, "Inverter fixture drew no energy");
            h.runAfterDelay(1, () -> {
                // One FE quantization step can shift as the buffer voltage falls.
                near(h, before - storage.getEnergyStored(), expected, 1, "Once-per-tick native FE debit");
                finish(h);
            });
        });
    }

}
