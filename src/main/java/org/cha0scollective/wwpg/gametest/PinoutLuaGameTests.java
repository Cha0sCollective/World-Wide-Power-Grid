package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.google.gson.JsonParser;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/** Exercises the real peripheral provider and Lua VM. Loaded only in the PINOUT test namespace. */
@GameTestHolder("wwpg_pinout")
@PrefixGameTestTemplate(false)
public final class PinoutLuaGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void actualLuaControlsAllEightMixedLoads(GameTestHelper h) throws IOException {
        LuaRunner.run(h);
    }

    // NeoForge examines declared method signatures even for disabled namespaces.
    // Keep all CC API signatures in a class loaded only when this fixture runs.
    private static final class LuaRunner {
        private static void run(GameTestHelper h) throws IOException {
            var source = new BlockPos(1, 2, 1);
            var pins = new BlockPos(3, 2, 2);
            var computerPos = pins.west();
            StationaryEquipmentGameTests.place(h, source, CEEBlocks.CREATIVE_BATTERY.get());
            StationaryEquipmentGameTests.place(h, pins, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pinout:pinout")));
            StationaryEquipmentGameTests.place(h, computerPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:computer_normal")));
            for (int pin = 1; pin <= 8; pin++) StationaryEquipmentGameTests.place(h, load(pin),
                    pin % 2 == 0 ? ModdedBlocks.CREATIVE_RESISTOR.get() : CEEBlocks.CREATIVE_RESISTOR.get());
            var computer = ((AbstractComputerBlockEntity) h.getBlockEntity(computerPos)).createServerComputer();
            var mount = computer.createRootMount();
            if (mount.exists("acceptance.json")) mount.delete("acceptance.json");
            try (var script = PinoutLuaGameTests.class.getResourceAsStream("/data/wwpg_pinout/programs/acceptance.lua")) {
                if (script == null) throw new IOException("Missing packaged Lua acceptance program");
                write(mount, "startup.lua", script.readAllBytes());
            }
            h.runAtTickTime(5, () -> {
                StationaryEquipmentGameTests.source(h, source, 12);
                // Native Lua common 9 is PG terminal 0. No replacement peripheral API.
                StationaryEquipmentGameTests.wire(h, source, 1, pins, 0, true);
                for (int pin = 1; pin <= 8; pin++) {
                    var load = load(pin);
                    if (pin % 2 == 0) ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
                    else StationaryEquipmentGameTests.resistor(h, load, 1000);
                    StationaryEquipmentGameTests.wire(h, pins, pin, load, 0, pin % 2 == 0);
                    StationaryEquipmentGameTests.wire(h, source, 0, load, 1, pin % 2 != 0);
                }
                computer.turnOn();
            });
            h.startSequence().thenWaitUntil(() -> {
                try { h.assertTrue(mount.exists("acceptance.json"), "Waiting for the real Lua computer"); }
                catch (IOException error) { throw new IllegalStateException(error); }
            }).thenExecute(() -> {
                try {
                    var report = JsonParser.parseString(read(mount, "acceptance.json")).getAsJsonObject();
                    h.assertTrue(report.get("ok").getAsBoolean(), "Lua acceptance failed: " + report);
                    h.assertTrue(report.get("methods").getAsInt() == 8, "Lua did not exercise every API method");
                    for (int pin = 1; pin <= 8; pin++) {
                        double v = pin % 2 == 0 ? voltage(h, load(pin)) : StationaryEquipmentGameTests.ceeVoltage(h, load(pin));
                        StationaryEquipmentGameTests.near(h, v, 12, .02, "Independently measured load on Lua pin " + pin);
                    }
                    var be = h.getBlockEntity(pins);
                    var saved = be.saveWithFullMetadata(h.getLevel().registryAccess());
                    var restored = BlockEntity.loadStatic(h.absolutePos(pins), be.getBlockState(), saved, h.getLevel().registryAccess());
                    h.assertTrue(restored != null && restored.getLevel() == null, "Pinout cold-load fixture assigned a world");
                    var cold = restored.saveWithoutMetadata(h.getLevel().registryAccess());
                    for (int pin = 1; pin <= 8; pin++) h.assertTrue(cold.getBoolean("Pin" + pin),
                            "Pinout lost native saved state for pin " + pin + " before a solve");
                    mount.delete("acceptance.json");
                    computer.reboot();
                } catch (IOException error) { throw new IllegalStateException(error); }
            }).thenWaitUntil(() -> {
                try { h.assertTrue(mount.exists("acceptance.json"), "Waiting for Lua after computer restart"); }
                catch (IOException error) { throw new IllegalStateException(error); }
            }).thenExecute(() -> {
                try {
                    var report = JsonParser.parseString(read(mount, "acceptance.json")).getAsJsonObject();
                    h.assertTrue(report.get("ok").getAsBoolean(), "Lua failed after computer restart: " + report);
                    DynamicGameTests.audit(h);
                } catch (IOException error) { throw new IllegalStateException(error); }
            }).thenSucceed();
        }

        private static BlockPos load(int pin) { return new BlockPos(6, 2, pin); }

        private static double voltage(GameTestHelper h, BlockPos local) {
            var pos = h.absolutePos(local);
            return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage()
                    - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
        }

        private static void write(WritableMount mount, String path, byte[] bytes) throws IOException {
            try (var channel = mount.openFile(path, Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
            }
        }

        private static String read(WritableMount mount, String path) throws IOException {
            try (var channel = mount.openFile(path, Set.of(StandardOpenOption.READ))) {
                var buffer = ByteBuffer.allocate((int) channel.size());
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
                return new String(buffer.array(), StandardCharsets.UTF_8);
            }
        }
    }
}
