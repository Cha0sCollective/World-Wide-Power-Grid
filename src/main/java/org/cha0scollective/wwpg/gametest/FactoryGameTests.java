package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.electric_pump.ElectricPumpBlockEntity;
import com.george_vi.electroenergetics.content.fuse.FuseDevice;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.equipment.portablebattery.PortableBatteryBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class FactoryGameTests {
    @GameTest(template = "empty", timeoutTicks = 90) public static void ceeChargesPgPortableBattery(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
        h.setBlock(a.below(), Blocks.STONE); h.setBlock(b.below(), Blocks.STONE);
        h.setBlock(a, CEEBlocks.CREATIVE_BATTERY.get()); h.setBlock(b, ModdedBlocks.PORTABLE_BATTERY.get());
        var source = h.absolutePos(a); var battery = h.absolutePos(b); var level = h.getLevel(); int[] charge = new int[1];
        h.runAtTickTime(5, () -> {
            DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class).voltage = 24;
            ((PortableBatteryBlockEntity) level.getBlockEntity(battery)).setCharge(0);
            for (int i = 0; i < 2; ++i) WiringGameTests.connect(h, source, 1 - i, battery, i, false);
        });
        h.runAtTickTime(30, () -> {
            var be = (PortableBatteryBlockEntity) level.getBlockEntity(battery);
            h.assertTrue(be.getCharge() > 0, "CEE power failed to charge PG's portable battery");
            charge[0] = be.getCharge();
            DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class).voltage = 0;
        });
        h.runAtTickTime(40, () -> charge[0] = ((PortableBatteryBlockEntity) level.getBlockEntity(battery)).getCharge());
        h.runAtTickTime(55, () -> {
            h.assertTrue(((PortableBatteryBlockEntity) level.getBlockEntity(battery)).getCharge() == charge[0], "PG battery gained charge without solved voltage");
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 110) public static void pgPowersCeeLightHeatAndPump(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(3, 2, 1); var c = new BlockPos(5, 2, 1); var d = new BlockPos(3, 2, 4);
        for (var pos : new BlockPos[] {a, b, c, d}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(b, CEEBlocks.BULB.get());
        h.setBlock(c, CEEBlocks.RESISTIVE_HEATER.get()); h.setBlock(d, CEEBlocks.ELECTRIC_PUMP.get());
        var source = h.absolutePos(a); var bulb = h.absolutePos(b); var heater = h.absolutePos(c); var pump = h.absolutePos(d); var level = h.getLevel();
        double[] heat = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(300);
            for (var load : new BlockPos[] {bulb, heater, pump}) {
                WiringGameTests.connect(h, source, 0, load, 0, false);
                WiringGameTests.connect(h, source, 1, load, 1, false);
            }
        });
        h.runAtTickTime(45, () -> {
            h.assertTrue(level.getBlockState(bulb).getValue(BulbBlock.LIGHT) > 0, "PG power did not light CEE bulb");
            var hot = (ResistiveHeaterBlockEntity) level.getBlockEntity(heater);
            h.assertTrue(hot.heat > 0.1 && hot.voltage > 290, "PG power did not heat CEE heater");
            heat[0] = hot.heat;
            h.assertTrue(((ElectricPumpBlockEntity) level.getBlockEntity(pump)).getSpeed() > 0, "PG power did not drive CEE pump");
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(0);
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(level.getBlockState(bulb).getValue(BulbBlock.LIGHT) == 0, "CEE bulb retained light without power");
            h.assertTrue(((ResistiveHeaterBlockEntity) level.getBlockEntity(heater)).heat < heat[0], "CEE heater did not cool");
            h.assertTrue(((ElectricPumpBlockEntity) level.getBlockEntity(pump)).getSpeed() == 0, "CEE pump retained output without power");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110) public static void ceeFuseTripsAndRepairRestoresPgLoad(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var f = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, f, b}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(f, CEEBlocks.FUSE.get());
        h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var fuse = h.absolutePos(f); var load = h.absolutePos(b); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(100);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(1);
            var device = DevicesSavedData.load(level).getDevice(fuse, FuseDevice.class);
            device.setAmperage = 1;
            WiringGameTests.connect(h, source, 0, fuse, 0, false);
            WiringGameTests.connect(h, fuse, 1, load, 0, false);
            WiringGameTests.connect(h, source, 1, load, 1, false);
        });
        h.runAtTickTime(40, () -> {
            var device = DevicesSavedData.load(level).getDevice(fuse, FuseDevice.class);
            h.assertTrue(device.isBroken, "CEE fuse did not trip on solved PG current");
            h.assertTrue(Math.abs(voltage(h, load)) < 0.001, "Fuse trip left PG load energized");
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(100);
            device.isBroken = false; device.temp = 0; device.setAmperage = 10;
        });
        h.runAtTickTime(70, () -> {
            h.assertTrue(Math.abs(voltage(h, load) - 100) < 1, "Repair failed to restore PG power");
            h.assertTrue(!DevicesSavedData.load(level).getDevice(fuse, FuseDevice.class).isBroken, "Repaired fuse tripped below its rating");
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    private static double voltage(GameTestHelper h, BlockPos pos) {
        return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
    }
}
