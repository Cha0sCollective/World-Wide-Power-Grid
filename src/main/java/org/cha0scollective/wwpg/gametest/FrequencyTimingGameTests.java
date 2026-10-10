package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterDevice;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.synchroscope.SynchroscopeDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

/** Saved native sample counts must not permanently starve display synchronization. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class FrequencyTimingGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void evenSavedFrequencySampleCountUpdates(GameTestHelper h) { frequency(h, 1000); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oddSavedFrequencySampleCountUpdates(GameTestHelper h) { frequency(h, 1001); }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void oddSavedSynchroscopeSampleCountUpdatesAfterPhaseChange(GameTestHelper h) {
        var meter = new BlockPos(2, 2, 2); var common = new BlockPos(4, 2, 4);
        StationaryEquipmentGameTests.place(h, meter, CEEBlocks.SYNCHROSCOPE.get());
        StationaryEquipmentGameTests.place(h, common, CEEBlocks.CONNECTOR.get());
        var sources = new BlockPos[6];
        for (int i = 0; i < 6; i++) {
            sources[i] = new BlockPos(1+i%3, 2, i < 3 ? 1 : 3);
            StationaryEquipmentGameTests.place(h, sources[i], CEEBlocks.CREATIVE_BATTERY.get());
        }
        h.runAtTickTime(5, () -> {
            for (int i = 0; i < 6; i++) {
                var source = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(sources[i]), CreativeBatteryDevice.class);
                source.voltage = 100; source.acFrequency = 20; source.phaseOffset = 120*(i%3);
                StationaryEquipmentGameTests.wire(h, sources[i], 0, common, 0, false);
                StationaryEquipmentGameTests.wire(h, sources[i], 1, meter, i, true);
            }
            var device = synchroscope(h, meter); var saved = new CompoundTag(); device.write(saved);
            saved.putInt("Ticks", 1001); device.read(saved);
        });
        h.runAtTickTime(35, () -> {
            near(h, phase(h, meter), 0, .5, "Initial synchronized phase");
            for (int i = 3; i < 6; i++)
                DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(sources[i]), CreativeBatteryDevice.class).phaseOffset += 45;
        });
        h.runAtTickTime(65, () -> {
            double phase = phase(h, meter);
            near(h, Math.abs(Math.IEEEremainder(Math.toDegrees(synchroscope(h, meter).prevDiff), 360)), 45, .5,
                    "Native synchroscope phase history");
            near(h, Math.abs(Math.IEEEremainder(phase, 360)), 45, .5, "Synchroscope display after an odd saved count");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static SynchroscopeDevice synchroscope(GameTestHelper h, BlockPos pos) {
        return DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(pos), SynchroscopeDevice.class);
    }
    private static float phase(GameTestHelper h, BlockPos pos) {
        return h.getBlockEntity(pos).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("PhaseOffset");
    }

    private static void frequency(GameTestHelper h, int samples) {
        var source = new BlockPos(1, 2, 1); var meter = new BlockPos(4, 2, 1);
        StationaryEquipmentGameTests.place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        StationaryEquipmentGameTests.place(h, meter, CEEBlocks.FREQUENCY_METER.get());
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(100, 50, 0);
            StationaryEquipmentGameTests.wire(h, source, 0, meter, 0, false);
            StationaryEquipmentGameTests.wire(h, source, 1, meter, 1, true);
            var device = device(h, meter);
            var saved = new CompoundTag(); device.write(saved);
            saved.putInt("Ticks", samples); device.read(saved);
            h.assertTrue(device.ticks == samples, "Native saved sample count was not exercised");
        });
        h.runAtTickTime(35, () -> {
            var device = device(h, meter);
            var measured = new CompoundTag(); device.write(measured);
            h.assertTrue(device.prevPeriod > 6 && device.prevPeriod < 7 && measured.getDouble("MaxVoltageLastPeriod") > 90,
                    "Native frequency waveform was missing, rather than only its display: period=" + device.prevPeriod);
            near(h, reading(h, meter), 50, .5, "Frequency display after saved count " + samples);
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(100, 40, 0);
        });
        h.runAtTickTime(60, () -> {
            near(h, reading(h, meter), 40, .5, "Frequency display after source change");
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    private static FrequencyMeterDevice device(GameTestHelper h, BlockPos pos) {
        return DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(pos), FrequencyMeterDevice.class);
    }
    private static float reading(GameTestHelper h, BlockPos pos) {
        return h.getBlockEntity(pos).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("Frequency");
    }
    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String label) {
        BoardComponentGameTests.near(h, actual, expected, tolerance, label);
    }
}
