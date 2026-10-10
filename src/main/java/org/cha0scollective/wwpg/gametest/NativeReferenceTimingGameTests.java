package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.accumulator.AccumulatorDevice;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.bulb.BulbDevice;
import com.george_vi.electroenergetics.content.fuse.FuseDevice;
import com.george_vi.electroenergetics.content.resistive_heater.ResistiveHeaterBlockEntity;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.electrical_properties.AccumulatorProperties;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;

import java.nio.file.Files;
import java.nio.file.Path;

/** Captures the actual pinned upstream gameplay, then compares compatibility.
 * The reference jar excludes all electrical compatibility hooks. */
@GameTestHolder("wwpg_reference")
@PrefixGameTestTemplate(false)
public final class NativeReferenceTimingGameTests {
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1), DEVICE = new BlockPos(4, 2, 1), LOAD = new BlockPos(6, 2, 1);

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 240)
    public static void bulbFailureAndHeaterTransitionsMatchPinnedUpstreamWithinOneTick(GameTestHelper h) {
        place(h, SOURCE, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, DEVICE, CEEBlocks.BULB.get());
        place(h, LOAD, CEEBlocks.RESISTIVE_HEATER.get());
        var data = new Trace("cee-heating", h);
        h.runAtTickTime(10, () -> {
            ShowroomTools.scroll(h, h.absolutePos(SOURCE), 300_000);
            for (var p : new BlockPos[]{DEVICE, LOAD}) wirePair(h, SOURCE, p);
        });
        h.runAtTickTime(30, () -> ShowroomTools.scroll(h, h.absolutePos(SOURCE), 1_000_000));
        h.runAtTickTime(80, () -> ShowroomTools.scroll(h, h.absolutePos(SOURCE), 0));
        for (int tick = 11; tick <= 215; tick++) {
            final int at = tick;
            h.runAtTickTime(tick, () -> {
                var device = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(DEVICE), BulbDevice.class);
                var state = h.getBlockState(DEVICE);
                var heater = (ResistiveHeaterBlockEntity) h.getBlockEntity(LOAD);
                data.sample(at, heater.heat);
                if (state.hasProperty(BulbBlock.LIGHT) && state.getValue(BulbBlock.LIGHT) > 0) data.event("bulb-lit", at);
                if (device.destroyed) data.event("bulb-destroyed", at);
                if (CEEBlocks.BROKEN_BULB.has(state)) data.event("broken-bulb-placed", at);
                if (heater.heat >= .1) data.event("heater-smouldering", at);
                if (heater.heat >= .6) data.event("heater-kindled", at);
                if (at > 80 && heater.heat < .1) data.event("heater-cooled", at);
            });
        }
        h.runAtTickTime(216, () -> {
            data.requireEvents("bulb-lit", "bulb-destroyed", "broken-bulb-placed", "heater-smouldering", "heater-kindled", "heater-cooled");
            data.finish(false); finish(h);
        });
    }

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 125)
    public static void fuseTripAndNativeRepairMatchPinnedUpstreamWithinOneTick(GameTestHelper h) {
        place(h, SOURCE, CEEBlocks.CREATIVE_BATTERY.get()); place(h, DEVICE, CEEBlocks.FUSE.get()); place(h, LOAD, CEEBlocks.CREATIVE_RESISTOR.get());
        var data = new Trace("cee-fuse", h);
        h.runAtTickTime(10, () -> {
            ShowroomTools.scroll(h, h.absolutePos(SOURCE), 20_000);
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity) h.getBlockEntity(LOAD)).setResistance(1);
            var fuse = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(DEVICE), FuseDevice.class); fuse.setAmperage = 1;
            WiringGameTests.connect(h, h.absolutePos(SOURCE), 1, h.absolutePos(DEVICE), 0, false);
            WiringGameTests.connect(h, h.absolutePos(DEVICE), 1, h.absolutePos(LOAD), 0, false);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), 0, h.absolutePos(LOAD), 1, false);
        });
        h.runAtTickTime(50, () -> {
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity) h.getBlockEntity(LOAD)).setResistance(100);
            var player = h.makeMockPlayer(GameType.SURVIVAL); var item = com.george_vi.electroenergetics.CEEItems.COPPER_WIRE.asStack();
            player.setItemInHand(InteractionHand.MAIN_HAND, item); var pos = h.absolutePos(DEVICE);
            var hit = new net.minecraft.world.phys.BlockHitResult(pos.getCenter(), net.minecraft.core.Direction.UP, pos, false);
            h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(item, h.getLevel(), player, InteractionHand.MAIN_HAND, hit).consumesAction() && item.isEmpty(),
                    "Native fuse repair did not consume its item");
        });
        for (int tick = 11; tick <= 100; tick++) {
            final int at = tick;
            h.runAtTickTime(tick, () -> {
                var fuse = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(DEVICE), FuseDevice.class);
                data.sample(at, fuse.temp);
                if (fuse.isBroken) data.event("fuse-trip", at);
                var results = com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
                if (at > 50 && results != null && Math.abs(results.getVoltageAt(h.absolutePos(LOAD), 0, 1)) > 19) data.event("repaired-power", at);
            });
        }
        h.runAtTickTime(101, () -> { data.requireEvents("fuse-trip", "repaired-power"); data.finish(false); finish(h); });
    }

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 135)
    public static void accumulatorMixedChargeDischargeMatchesPinnedStorageWithinOnePercent(GameTestHelper h) {
        boolean nativeRun = upstream();
        place(h, SOURCE, nativeRun ? CEEBlocks.CREATIVE_BATTERY.get() : ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, DEVICE, CEEBlocks.ACCUMULATOR.get()); place(h, LOAD, CEEBlocks.CREATIVE_RESISTOR.get());
        var data = new Trace("cee-accumulator", h);
        h.runAtTickTime(10, () -> {
            var d = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(DEVICE), AccumulatorDevice.class);
            d.cell1Charge = AccumulatorProperties.getNominalCharge() * .5;
            // GameTest callbacks occur after native preparation. Seed the
            // already-prepared history as well, before its solve can commit.
            d.properties1.storedCharge = d.cell1Charge;
            data.initial = d.cell1Charge;
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity) h.getBlockEntity(LOAD)).setResistance(10);
            setSupply(h, nativeRun, 30);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), nativeRun ? 1 : 0, h.absolutePos(LOAD), 0, !nativeRun);
            WiringGameTests.connect(h, h.absolutePos(LOAD), 1, h.absolutePos(DEVICE), 0, false);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), nativeRun ? 0 : 1, h.absolutePos(DEVICE), 1, !nativeRun);
        });
        h.runAtTickTime(60, () -> setSupply(h, nativeRun, 0));
        for (int tick = 11; tick <= 120; tick++) {
            final int at = tick;
            h.runAtTickTime(tick, () -> {
                var charge = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(DEVICE), AccumulatorDevice.class).cell1Charge;
                data.sample(at, charge - data.initial);
            });
        }
        h.runAtTickTime(121, () -> {
            data.write();
            h.assertTrue(data.samples.get(35).getAsJsonObject().get("value").getAsDouble() > .1, "Accumulator did not charge: " + data.samples.get(35));
            h.assertTrue(data.samples.get(109).getAsJsonObject().get("value").getAsDouble() < 0, "Accumulator did not discharge");
            data.finish(true); finish(h);
        });
    }

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 135)
    public static void mixedAcidBatteryChargeDischargeMatchesNativeEnergyWithinOnePercent(GameTestHelper h) { battery(h, false); }

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 135)
    public static void mixedPotatoBatteryPreservesNativeNoChargingAndDischargeWindow(GameTestHelper h) { battery(h, true); }

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 205)
    public static void ceeDiodeReactiveSwitchingMatchesPinnedNativeWindow(GameTestHelper h) {
        place(h, SOURCE, CEEBlocks.CREATIVE_BATTERY.get()); place(h, DEVICE, CEEBlocks.DIODE.get());
        place(h, LOAD, upstream() ? CEEBlocks.CREATIVE_RESISTOR.get() : ModdedBlocks.CREATIVE_RESISTOR.get());
        var cap = LOAD.south(3); place(h, cap, CEEBlocks.CAPACITOR.get());
        var data = new Trace("cee-diode-reactive", h, residualVoltageTolerance());
        h.runAtTickTime(10, () -> {
            setSupply(h, true, 10);
            if (upstream()) ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity) h.getBlockEntity(LOAD)).setResistance(1000);
            else ShowroomTools.resistance(h, h.absolutePos(LOAD), 1000);
            DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(cap), com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice.class).capacitance = .0001;
            WiringGameTests.connect(h, h.absolutePos(SOURCE), 1, h.absolutePos(DEVICE), 1, false);
            for (var load : new BlockPos[]{LOAD, cap}) {
                WiringGameTests.connect(h, h.absolutePos(DEVICE), 0, h.absolutePos(load), 0, false);
                WiringGameTests.connect(h, h.absolutePos(SOURCE), 0, h.absolutePos(load), 1, false);
            }
        });
        h.runAtTickTime(50, () -> setSupply(h, true, 0));
        h.runAtTickTime(90, () -> setSupply(h, true, -10));
        h.runAtTickTime(130, () -> setSupply(h, true, 5));
        for (int tick = 16; tick <= 180; tick++) {
            final int at = tick;
            h.runAtTickTime(tick, () -> data.sample(at, DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(cap),
                    com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice.class).lastVoltage));
        }
        h.runAtTickTime(181, () -> {
            data.write();
            h.assertTrue(data.valueAt(45) > 9 && Math.abs(data.valueAt(85)) < .01 && Math.abs(data.valueAt(125)) < .01 && data.valueAt(175) > 4,
                    "CEE diode failed forward charging, decay, reverse blocking or restored charging");
            data.finish(true); finish(h);
        });
    }

    @GameTest(template = "empty", batch = "reference", timeoutTicks = 205)
    public static void pgNonlinearBoardReactiveSwitchingMatchesPinnedNativeWindow(GameTestHelper h) {
        boolean nativeRun = upstream();
        place(h, SOURCE, nativeRun ? ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get() : CEEBlocks.CREATIVE_BATTERY.get());
        place(h, DEVICE, ModdedBlocks.CIRCUIT_BOARD.get());
        var data = new Trace("pg-diode-reactive-board", h, residualVoltageTolerance());
        h.runAtTickTime(10, () -> {
            var schematic = BoardGameTests.diodeBoard(1000);
            var capacitor = new org.patryk3211.powergrid.circuits.schematic.PlacedComponent(org.patryk3211.powergrid.circuits.components.Components.CAPACITOR.get(), 3, 9, null);
            capacitor.set(org.patryk3211.powergrid.circuits.components.CapacitorComponent.CAPACITANCE, .0001f);
            schematic.placeComponent(capacitor, 3, 9);
            schematic.front().addVerticalLine(3, 6, 10); schematic.front().addVerticalLine(7, 6, 10);
            schematic.front().addHorizontalLine(10, 5, 7);
            ((org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity) h.getBlockEntity(DEVICE)).setSchematic(schematic);
            setSupply(h, !nativeRun, 10);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), nativeRun ? 0 : 1, h.absolutePos(DEVICE), 1, nativeRun);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), nativeRun ? 1 : 0, h.absolutePos(DEVICE), 0, nativeRun);
        });
        h.runAtTickTime(50, () -> setSupply(h, !nativeRun, 0));
        h.runAtTickTime(90, () -> setSupply(h, !nativeRun, -10));
        h.runAtTickTime(130, () -> setSupply(h, !nativeRun, 5));
        for (int tick = 16; tick <= 180; tick++) {
            final int at = tick;
            h.runAtTickTime(tick, () -> {
                var board = (org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity) h.getBlockEntity(DEVICE);
                var capacitor = board.getComponentsStream().filter(c -> c.component == org.patryk3211.powergrid.circuits.components.Components.CAPACITOR.get()).findFirst().orElseThrow();
                data.sample(at, ((org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire) capacitor.wires.getFirst()).capacitorVoltage());
            });
        }
        h.runAtTickTime(181, () -> {
            data.write();
            h.assertTrue(data.valueAt(45) < -9 && Math.abs(data.valueAt(85)) < .01 && Math.abs(data.valueAt(125)) < .01 && data.valueAt(175) < -4,
                    "PG diode board failed forward charging, decay, reverse blocking or restored charging");
            data.finish(true); finish(h);
        });
    }

    private static void battery(GameTestHelper h, boolean potato) {
        boolean nativeRun = upstream();
        place(h, SOURCE, nativeRun ? ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get() : CEEBlocks.CREATIVE_BATTERY.get());
        place(h, DEVICE, potato ? ModdedBlocks.POTATO_BATTERY.get() : ModdedBlocks.BATTERY.get());
        var terminal = potato ? DEVICE : DEVICE.above();
        if (!potato) h.setBlock(terminal, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING, net.minecraft.core.Direction.DOWN));
        place(h, LOAD, ModdedBlocks.CREATIVE_RESISTOR.get());
        var data = new Trace(potato ? "pg-potato-battery" : "pg-acid-battery", h);
        h.runAtTickTime(10, () -> {
            var b = (org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity) h.getBlockEntity(DEVICE);
            b.setEnergy(b.getCapacity() * .8); data.initial = b.getEnergy();
            ShowroomTools.resistance(h, h.absolutePos(LOAD), potato ? 1000 : 10);
            setSupply(h, !nativeRun, potato ? 2 : 30);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), nativeRun ? 0 : 1, h.absolutePos(LOAD), 0, nativeRun);
            WiringGameTests.connect(h, h.absolutePos(LOAD), 1, h.absolutePos(terminal), 0, true);
            WiringGameTests.connect(h, h.absolutePos(SOURCE), nativeRun ? 1 : 0, h.absolutePos(terminal), 1, nativeRun);
        });
        h.runAtTickTime(60, () -> setSupply(h, !nativeRun, 0));
        for (int tick = 11; tick <= 120; tick++) {
            final int at = tick;
            h.runAtTickTime(tick, () -> data.sample(at,
                    ((org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity) h.getBlockEntity(DEVICE)).getEnergy()-data.initial));
        }
        h.runAtTickTime(121, () -> {
            var charged = data.samples.get(35).getAsJsonObject().get("value").getAsDouble();
            var discharged = data.samples.get(109).getAsJsonObject().get("value").getAsDouble();
            if (potato) BoardComponentGameTests.near(h, charged, 0, 1e-10, "Native potato batteries must not recharge");
            else h.assertTrue(charged > .1, "Native acid battery did not charge");
            h.assertTrue(discharged < charged-.000001, "Native battery did not discharge");
            data.finish(true); finish(h);
        });
    }

    private static void setSupply(GameTestHelper h, boolean cee, int voltage) {
        if (cee) {
            ShowroomTools.scroll(h, h.absolutePos(SOURCE), Math.abs(voltage)*1000);
            // The native CEE UI clamps its magnitude to nonnegative values.
            // Set the saved device's signed voltage explicitly for this equation
            // reference, so reverse bias is real and identical in both runs.
            var source = DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(SOURCE), com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class);
            source.voltage = voltage;
            BoardComponentGameTests.near(h, source.voltage, voltage, 1e-9, "Reference supply signed voltage");
        } else ShowroomTools.pgSource(h, h.absolutePos(SOURCE), voltage);
    }
    private static void place(GameTestHelper h, BlockPos at, net.minecraft.world.level.block.Block block) { h.setBlock(at.below(), Blocks.STONE); h.setBlock(at, block); }
    private static void wirePair(GameTestHelper h, BlockPos source, BlockPos device) { for (int port = 0; port < 2; port++) WiringGameTests.connect(h, h.absolutePos(source), 1-port, h.absolutePos(device), port, false); }
    private static boolean upstream() { return Boolean.getBoolean("wwpg.test.upstreamReference"); }
    private static double residualVoltageTolerance() {
        // PG stops on a current residual, not a voltage error. These outputs
        // discharge through 1 kΩ. Bound both independent solves by I_tol*R;
        // retain a percentage comparison above that explicit near-zero bound.
        return 2*1000*org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.solver.solverAbsoluteMinimumPrecision.getF();
    }
    private static void finish(GameTestHelper h) { if (!upstream()) DynamicGameTests.audit(h); h.succeed(); }

    private static final class Trace {
        final String name; final GameTestHelper h; final JsonArray samples = new JsonArray(); final JsonObject events = new JsonObject(); final double nearZero; double initial;
        Trace(String name, GameTestHelper h) { this(name, h, 1e-8); }
        Trace(String name, GameTestHelper h, double nearZero) { this.name = name; this.h = h; this.nearZero = nearZero; }
        void sample(int tick, double value) {
            h.assertTrue(Double.isFinite(value), "Nonfinite native sample");
            var row = new JsonObject(); row.addProperty("tick", tick); row.addProperty("value", value);
            if (CEEBlocks.CREATIVE_BATTERY.has(h.getBlockState(SOURCE))) {
                row.addProperty("source_setting", DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(SOURCE),
                        com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice.class).voltage);
            } else {
                row.addProperty("source_setting", ((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity) h.getBlockEntity(SOURCE)).getValue());
            }
            samples.add(row);
        }
        void event(String name, int tick) { if (!events.has(name)) events.addProperty(name, tick); }
        double valueAt(int tick) { return java.util.stream.StreamSupport.stream(samples.spliterator(), false).map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(row -> row.get("tick").getAsInt() == tick).findFirst().orElseThrow().get("value").getAsDouble(); }
        void requireEvents(String... names) { for (var name : names) h.assertTrue(events.has(name), "Missing native gameplay event " + name); }
        void finish(boolean storage) {
            write();
            try {
                var reference = Path.of("native-reference-" + name + ".json");
                if (!upstream()) {
                    var baseline = JsonParser.parseString(Files.readString(reference)).getAsJsonObject();
                    h.assertTrue(baseline.get("backend").getAsString().equals(System.getProperty("wwpg.test.backend")), "Reference backend differs from compatibility");
                    for (var entry : baseline.getAsJsonObject("events").entrySet()) h.assertTrue(events.has(entry.getKey()) && Math.abs(events.get(entry.getKey()).getAsInt()-entry.getValue().getAsInt()) <= 1,
                            name + " event differs by more than one tick: " + entry.getKey() + "; actual=" + events + "; reference=" + baseline.get("events"));
                    if (storage) {
                        var expected = baseline.getAsJsonArray("samples");
                        h.assertTrue(expected.size() == samples.size(), "Recorded storage window changed length");
                        for (int i = 0; i < samples.size(); i++) {
                            double actual = samples.get(i).getAsJsonObject().get("value").getAsDouble(), best = Double.POSITIVE_INFINITY;
                            for (int j = Math.max(0, i-1); j <= Math.min(expected.size()-1, i+1); j++) {
                                double value = expected.get(j).getAsJsonObject().get("value").getAsDouble();
                                best = Math.min(best, Math.abs(actual-value)/Math.max(Math.abs(value)*.01, nearZero));
                            }
                            h.assertTrue(best <= 1, "Native transient differs beyond 1% / recorded absolute tolerance even allowing one tick, point " + i + ": " + actual + ", tolerance multiples=" + best);
                        }
                    }
                }
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
        void write() {
            var data = new JsonObject(); data.addProperty("schema", 1); data.addProperty("backend", System.getProperty("wwpg.test.backend"));
            data.addProperty("upstream", upstream()); data.addProperty("initial", initial); data.add("samples", samples); data.add("events", events);
            data.addProperty("relative_tolerance", .01); data.addProperty("near_zero_tolerance", nearZero);
            data.addProperty("allowed_gameplay_tick_offset", 1);
            try { Files.writeString(Path.of((upstream() ? "native-reference-" : "compatibility-")+name+".json"), new GsonBuilder().setPrettyPrinting().create().toJson(data)+"\n"); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
    }
}
