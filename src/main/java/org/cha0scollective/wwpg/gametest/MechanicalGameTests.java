package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity;
import com.george_vi.electroenergetics.content.variac.VariacDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class MechanicalGameTests {
    @GameTest(template = "empty", timeoutTicks = 150) public static void mixedMotorsRespondToPowerAndPolarity(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
        var c = new BlockPos(1, 2, 4); var d = new BlockPos(4, 2, 4);
        for (var p : new BlockPos[] {a, b, c, d}) h.setBlock(p.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(b, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("electroenergetics:white_electric_motor")));
        h.setBlock(c, CEEBlocks.CREATIVE_BATTERY.get()); h.setBlock(d, ModdedBlocks.ELECTRIC_MOTOR.get());
        var pgSource = h.absolutePos(a); var ceeMotor = h.absolutePos(b); var ceeSource = h.absolutePos(c); var pgMotor = h.absolutePos(d);
        var level = h.getLevel(); double[] direction = new double[2];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(pgSource)).setValue(100);
            DevicesSavedData.load(level).getDevice(ceeSource, CreativeBatteryDevice.class).voltage = 100;
            for (int i = 0; i < 2; ++i) {
                WiringGameTests.connect(h, pgSource, i, ceeMotor, i, false);
                WiringGameTests.connect(h, ceeSource, 1 - i, pgMotor, i, true);
            }
        });
        h.runAtTickTime(40, () -> {
            var cee = (ElectricMotorBlockEntity) level.getBlockEntity(ceeMotor);
            h.assertTrue(Math.abs(cee.getGeneratedSpeed()) == 32, "CEE locked motor mode failed");
            cee.getBehaviour(ScrollValueBehaviour.TYPE).setValue(1_000_001);
        });
        h.runAtTickTime(60, () -> {
            var cee = (ElectricMotorBlockEntity) level.getBlockEntity(ceeMotor);
            var pg = (org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity) level.getBlockEntity(pgMotor);
            direction[0] = cee.getGeneratedSpeed(); direction[1] = pg.getGeneratedSpeed();
            h.assertTrue(Math.abs(direction[0]) > 32 && Math.abs(direction[1]) > 0, "Mixed motor output was missing");
            h.assertTrue(cee.calculateAddedStressCapacity() > 0 && pg.calculateAddedStressCapacity() > 0, "Mixed motors supplied no mechanical capacity");
            ((CreativeSourceBlockEntity) level.getBlockEntity(pgSource)).setValue(-100);
            DevicesSavedData.load(level).getDevice(ceeSource, CreativeBatteryDevice.class).voltage = -100;
        });
        h.runAtTickTime(90, () -> {
            var cee = (ElectricMotorBlockEntity) level.getBlockEntity(ceeMotor);
            var pg = (org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity) level.getBlockEntity(pgMotor);
            h.assertTrue(cee.getGeneratedSpeed() * direction[0] < 0 && pg.getGeneratedSpeed() * direction[1] < 0,
                    "Solved current/voltage polarity did not reverse the motors");
            ((CreativeSourceBlockEntity) level.getBlockEntity(pgSource)).setValue(0);
            DevicesSavedData.load(level).getDevice(ceeSource, CreativeBatteryDevice.class).voltage = 0;
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(((ElectricMotorBlockEntity) level.getBlockEntity(ceeMotor)).getGeneratedSpeed() == 0
                    && ((org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity) level.getBlockEntity(pgMotor)).getGeneratedSpeed() == 0,
                    "Mixed motors retained mechanical output without power");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100) public static void ceeVariacControlsPgLoad(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var v = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        for (var p : new BlockPos[] {a, v, b}) h.setBlock(p.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(v, CEEBlocks.VARIAC.get());
        h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var variac = h.absolutePos(v); var load = h.absolutePos(b); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(20);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(10);
            var device = DevicesSavedData.load(level).getDevice(variac, VariacDevice.class);
            device.ratio = 1; device.progress = 0.5f;
            WiringGameTests.connect(h, source, 0, variac, 0, true);
            WiringGameTests.connect(h, source, 1, variac, 1, true);
            WiringGameTests.connect(h, variac, 2, load, 0, true);
            WiringGameTests.connect(h, variac, 1, load, 1, true);
        });
        h.runAtTickTime(25, () -> {
            voltage(h, load, 10);
            DevicesSavedData.load(level).getDevice(variac, VariacDevice.class).progress = 0.25f;
        });
        h.runAtTickTime(40, () -> {
            voltage(h, load, 5);
            DevicesSavedData.load(level).getDevice(variac, VariacDevice.class).progress = 0;
        });
        h.runAtTickTime(55, () -> { voltage(h, load, 0); DynamicGameTests.audit(h); h.succeed(); });
    }
    private static void voltage(GameTestHelper h, BlockPos pos, double expected) {
        double v = new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
        h.assertTrue(Double.isFinite(v) && Math.abs(v - expected) < 0.1, "Variac expected " + expected + " V, got " + v);
    }
}
