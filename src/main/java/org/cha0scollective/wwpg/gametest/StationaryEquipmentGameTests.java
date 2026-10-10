package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.bell.AlarmBellBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.electromagnet.ElectromagnetBlockEntity;
import org.patryk3211.powergrid.electricity.fan.ElectricFanBlockEntity;
import org.patryk3211.powergrid.electricity.redstoneconverter.RedstoneConverterBlock;
import org.patryk3211.powergrid.electricity.sparkgap.SparkGapBlockEntity;

/** Native equipment is powered through real mixed wires, including its off state. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryEquipmentGameTests {
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1), LOAD = new BlockPos(4, 2, 1);

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void pgPowerSoundsCeeBuzzer(GameTestHelper h) {
        place(h, SOURCE, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, LOAD, CEEBlocks.BUZZER.get());
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(SOURCE)).setValue(100);
            wire(h, SOURCE, 0, LOAD, 0, true);
            wire(h, SOURCE, 1, LOAD, 1, false);
        });
        h.runAtTickTime(25, () -> {
            near(h, h.getBlockEntity(LOAD).saveWithoutMetadata(h.getLevel().registryAccess())
                    .getDouble("Voltage"), 100, .1, "Buzzer's native audio voltage");
            ((CreativeSourceBlockEntity) h.getBlockEntity(SOURCE)).setValue(0);
        });
        h.runAtTickTime(45, () -> {
            near(h, h.getBlockEntity(LOAD).saveWithoutMetadata(h.getLevel().registryAccess())
                    .getDouble("Voltage"), 0, .001, "Buzzer's off state");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ceePowerRunsPgFanMagnetAndBell(GameTestHelper h) {
        Block[] blocks = {ModdedBlocks.ELECTRIC_FAN.get(), ModdedBlocks.ELECTROMAGNET.get(),
                ModdedBlocks.ALARM_BELL.get()};
        for (int i = 0; i < blocks.length; i++) {
            place(h, SOURCE.offset(0, 0, 3 * i), CEEBlocks.CREATIVE_BATTERY.get());
            place(h, LOAD.offset(0, 0, 3 * i), blocks[i]);
            if (i == 1) h.setBlock(LOAD.offset(0, 0, 3 * i).above(),
                    ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
        }
        h.runAtTickTime(5, () -> {
            for (int i = 0; i < blocks.length; i++) {
                var s = SOURCE.offset(0, 0, 3 * i);
                var l = LOAD.offset(0, 0, 3 * i);
                // Magnet's native threshold is 2.5 A; other loads use 1 A.
                source(h, s, ((ElectricBlockEntity) h.getBlockEntity(l)).resistance() * (i == 1 ? 3 : 1));
                var endpoint = i == 1 ? l.above() : l;
                wire(h, s, 1, endpoint, 0, true);
                wire(h, s, 0, endpoint, 1, false);
            }
        });
        float[] speed = {0};
        h.runAtTickTime(30, () -> {
            var fan = (ElectricFanBlockEntity) h.getBlockEntity(LOAD);
            speed[0] = fan.getSpeed();
            h.assertTrue(Math.abs(speed[0]) > 50 && fan.getAirFlowDirection() != null,
                    "Solved CEE current did not produce fan airflow");
            h.assertTrue(((ElectromagnetBlockEntity) h.getBlockEntity(LOAD.offset(0, 0, 3)))
                    .getFieldStrength() > .25, "CEE power did not activate the native magnetic field");
            h.assertTrue(((AlarmBellBlockEntity) h.getBlockEntity(LOAD.offset(0, 0, 6))).getVolume() > 1,
                    "CEE power did not activate native bell audio");
            source(h, SOURCE, -((ElectricBlockEntity) fan).resistance());
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(((ElectricFanBlockEntity) h.getBlockEntity(LOAD)).getSpeed() * speed[0] < 0,
                    "Reversed CEE voltage did not reverse airflow");
            for (int i = 0; i < blocks.length; i++) source(h, SOURCE.offset(0, 0, 3 * i), 0);
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(((ElectricFanBlockEntity) h.getBlockEntity(LOAD)).getSpeed() == 0,
                    "Unpowered fan retained airflow");
            h.assertTrue(((ElectromagnetBlockEntity) h.getBlockEntity(LOAD.offset(0, 0, 3)))
                    .getFieldStrength() == 0, "Unpowered magnet retained its field");
            h.assertTrue(((AlarmBellBlockEntity) h.getBlockEntity(LOAD.offset(0, 0, 6))).getVolume() == 0,
                    "Unpowered bell retained audio");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void stationaryBatteriesChargeAndDischargeWithCee(GameTestHelper h) {
        place(h, SOURCE, ModdedBlocks.BATTERY.get());
        var terminals = SOURCE.above();
        h.setBlock(terminals, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
        place(h, LOAD, CEEBlocks.CREATIVE_RESISTOR.get());
        var chargeSource = SOURCE.offset(0, 0, 4);
        var limit = LOAD.offset(0, 0, 4);
        place(h, chargeSource, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, limit, CEEBlocks.CREATIVE_RESISTOR.get());
        double[] energy = {0};
        h.runAtTickTime(5, () -> {
            var battery = (BatteryBlockEntity) h.getBlockEntity(SOURCE);
            battery.setEnergy(battery.getCapacity() * .8);
            energy[0] = battery.getEnergy();
            resistor(h, LOAD, 1000);
            wire(h, terminals, 0, LOAD, 0, false);
            wire(h, terminals, 1, LOAD, 1, true);
        });
        h.runAtTickTime(30, () -> {
            var battery = (BatteryBlockEntity) h.getBlockEntity(SOURCE);
            h.assertTrue(battery.getEnergy() < energy[0] && Math.abs(ceeVoltage(h, LOAD)) > 10,
                    "Stationary battery failed to discharge into CEE");
            var tag = battery.saveWithoutMetadata(h.getLevel().registryAccess());
            energy[0] = battery.getEnergy();
            battery.setEnergy(1);
            battery.loadWithComponents(tag, h.getLevel().registryAccess());
            near(h, battery.getEnergy(), energy[0], .00001, "Saved battery before any recharge");
            source(h, chargeSource, 24);
            resistor(h, limit, 100);
            wire(h, chargeSource, 1, limit, 0, true);
            wire(h, limit, 1, terminals, 0, false);
            wire(h, chargeSource, 0, terminals, 1, true);
        });
        h.runAtTickTime(65, () -> {
            var battery = (BatteryBlockEntity) h.getBlockEntity(SOURCE);
            h.assertTrue(battery.getEnergy() > energy[0], "CEE supply failed to charge stationary battery");
            energy[0] = battery.getEnergy();
            double expected = -battery.calculatePower() * .05;
            h.runAfterDelay(1, () -> near(h, battery.getEnergy() - energy[0], expected,
                    Math.max(.00001, Math.abs(expected) * .01), "Battery's once-per-tick energy advancement"));
        });
        h.runAtTickTime(75, () -> {
            var battery = (BatteryBlockEntity) h.getBlockEntity(SOURCE);
            battery.setEnergy(battery.getCapacity() - .001);
        });
        h.runAtTickTime(85, () -> {
            var battery = (BatteryBlockEntity) h.getBlockEntity(SOURCE);
            near(h, battery.getEnergy(), battery.getCapacity(), .00001, "Native full-charge limit");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void potatoBatteryPreservesNativeNonRechargeableBehavior(GameTestHelper h) {
        place(h, SOURCE, ModdedBlocks.POTATO_BATTERY.get());
        place(h, LOAD, CEEBlocks.CREATIVE_RESISTOR.get());
        var charger = SOURCE.offset(0, 0, 4);
        var limit = LOAD.offset(0, 0, 4);
        place(h, charger, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, limit, CEEBlocks.CREATIVE_RESISTOR.get());
        double[] energy = {0};
        h.runAtTickTime(5, () -> {
            energy[0] = ((BatteryBlockEntity) h.getBlockEntity(SOURCE)).getEnergy();
            resistor(h, LOAD, 1000);
            wire(h, SOURCE, 0, LOAD, 0, true);
            wire(h, SOURCE, 1, LOAD, 1, false);
        });
        h.runAtTickTime(25, () -> {
            var battery = (BatteryBlockEntity) h.getBlockEntity(SOURCE);
            h.assertTrue(battery.getEnergy() < energy[0] && Math.abs(ceeVoltage(h, LOAD)) > .1,
                    "Potato battery failed to power CEE");
            energy[0] = battery.getEnergy();
            source(h, charger, 3);
            resistor(h, limit, 100);
            wire(h, charger, 1, limit, 0, true);
            wire(h, limit, 1, SOURCE, 0, false);
            wire(h, charger, 0, SOURCE, 1, true);
        });
        h.runAtTickTime(55, () -> {
            h.assertTrue(((BatteryBlockEntity) h.getBlockEntity(SOURCE)).getEnergy() <= energy[0],
                    "WWPG incorrectly made the native potato battery rechargeable");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void nativeRedstoneConverterControlsCeeLoad(GameTestHelper h) {
        place(h, SOURCE, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, LOAD, ModdedBlocks.REDSTONE_CONVERTER.get());
        var output = LOAD.offset(0, 0, 3);
        place(h, output, CEEBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(5, () -> {
            source(h, SOURCE, 10);
            resistor(h, output, 100000);
            wire(h, SOURCE, 1, LOAD, 0, true);
            wire(h, SOURCE, 0, LOAD, 1, false);
            wire(h, LOAD, 2, output, 0, true);
            wire(h, LOAD, 1, output, 1, false);
        });
        h.runAtTickTime(25, () -> {
            near(h, ceeVoltage(h, output), 0, .1, "No redstone input");
            h.setBlock(LOAD.relative(h.getBlockState(LOAD).getValue(RedstoneConverterBlock.FACING)), Blocks.REDSTONE_BLOCK);
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(h.getBlockState(LOAD).getValue(RedstoneConverterBlock.POWERED),
                    "Native redstone input was not recognized");
            near(h, ceeVoltage(h, output), 10, .1, "Redstone-controlled CEE output");
            h.setBlock(LOAD.relative(h.getBlockState(LOAD).getValue(RedstoneConverterBlock.FACING)), Blocks.STONE);
        });
        h.runAtTickTime(65, () -> {
            near(h, ceeVoltage(h, output), 0, .1, "Removed redstone input");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mixedVoltageTriggersAndExtinguishesNativeSparkGap(GameTestHelper h) {
        place(h, SOURCE, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, LOAD, ModdedBlocks.SPARK_GAP.get());
        var limit = new BlockPos(2, 2, 4);
        place(h, limit, CEEBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(5, () -> {
            source(h, SOURCE, 900);
            resistor(h, limit, 1000);
            wire(h, SOURCE, 1, limit, 0, true);
            wire(h, limit, 1, LOAD, 0, false);
            wire(h, SOURCE, 0, LOAD, 1, true);
        });
        h.runAtTickTime(25, () -> {
            h.assertTrue(!((SparkGapBlockEntity) h.getBlockEntity(LOAD)).isSparking(),
                    "Gap conducted below its native breakdown threshold");
            source(h, SOURCE, 1500);
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(((SparkGapBlockEntity) h.getBlockEntity(LOAD)).isSparking(),
                    "Mixed high voltage did not trigger the native spark gap");
            source(h, SOURCE, 0);
        });
        h.runAtTickTime(65, () -> {
            h.assertTrue(!((SparkGapBlockEntity) h.getBlockEntity(LOAD)).isSparking(),
                    "Spark gap retained an arc without holding current");
            finish(h);
        });
    }

    static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(pos, block);
    }

    static void source(GameTestHelper h, BlockPos pos, double voltage) {
        DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(pos), CreativeBatteryDevice.class).voltage = voltage;
    }

    static void resistor(GameTestHelper h, BlockPos pos, double resistance) {
        DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(pos), ResistorDevice.class).properties = ElectricalProperties.resistor(resistance);
    }

    static void wire(GameTestHelper h, BlockPos a, int pa, BlockPos b, int pb, boolean pg) {
        WiringGameTests.connect(h, h.absolutePos(a), pa, h.absolutePos(b), pb, pg);
    }

    static double ceeVoltage(GameTestHelper h, BlockPos pos) {
        return InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(h.absolutePos(pos), 0, 1);
    }

    static void near(GameTestHelper h, double actual, double expected, double tolerance, String label) {
        BoardComponentGameTests.near(h, actual, expected, tolerance, label);
    }

    static void finish(GameTestHelper h) {
        DynamicGameTests.audit(h);
        h.succeed();
    }
}
