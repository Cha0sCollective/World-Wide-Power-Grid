package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class BoardComponentGameTests {
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardVoltageGaugeReadsCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.VOLTAGE_GAUGE.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); });
        h.runAtTickTime(20, () -> {
            near(h, Components.VOLTAGE_GAUGE.get().getValue(f.component), 10, 0.02, "Board voltage gauge"); f.voltage(0, 20);
        });
        h.runAtTickTime(30, () -> { near(h, Components.VOLTAGE_GAUGE.get().getValue(f.component), 20, 0.02, "Changed board voltage gauge"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardCurrentGaugeReadsCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.CURRENT_GAUGE.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(0, 1000); f.resistance(1, 1000); });
        h.runAtTickTime(20, () -> {
            near(h, Components.CURRENT_GAUGE.get().getValue(f.component), 0.005, 0.00002, "Board current gauge"); f.voltage(0, 20);
        });
        h.runAtTickTime(30, () -> { near(h, Components.CURRENT_GAUGE.get().getValue(f.component), 0.01, 0.00002, "Changed board current gauge"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardSwitchControlsCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.SWITCH.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(0, 1000); });
        h.runAtTickTime(20, () -> {
            near(h, f.current(), 0, 1e-6, "Open board switch");
            Components.SWITCH.get().use(f.board, f.component, h.makeMockPlayer(GameType.SURVIVAL));
        });
        h.runAtTickTime(30, () -> {
            near(h, f.current(), 0.01, 0.00002, "Closed board switch");
            Components.SWITCH.get().use(f.board, f.component, h.makeMockPlayer(GameType.SURVIVAL));
        });
        h.runAtTickTime(40, () -> { near(h, f.current(), 0, 1e-6, "Reopened board switch"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardButtonReleasesCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.BUTTON.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(0, 1000); });
        h.runAtTickTime(20, () -> Components.BUTTON.get().use(f.board, f.component, h.makeMockPlayer(GameType.SURVIVAL)));
        h.runAtTickTime(23, () -> near(h, f.current(), 0.01, 0.00002, "Pressed board button"));
        h.runAtTickTime(35, () -> { near(h, f.current(), 0, 1e-6, "Released board button"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardRedstoneRelaySwitchesCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.REDSTONE_RELAY.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(0, 1000); });
        h.runAtTickTime(20, () -> { near(h, f.current(), 0, 1e-6, "Unpowered redstone relay"); Components.REDSTONE_RELAY.get().receiveRedstone(f.component, 15); });
        h.runAtTickTime(25, () -> { near(h, f.current(), 0.01, 0.00002, "Powered redstone relay"); Components.REDSTONE_RELAY.get().receiveRedstone(f.component, 0); });
        h.runAtTickTime(30, () -> { near(h, f.current(), 0, 1e-6, "Released redstone relay"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardRelayTransfersMixedLoad(GameTestHelper h) { relay(h, false); }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardDoubleRelayTransfersMixedLoads(GameTestHelper h) { relay(h, true); }
    private static void relay(GameTestHelper h, boolean dual) {
        var f = new BoardFixture(h, dual ? Components.RELAY_DPDT.get() : Components.RELAY.get(), p -> {});
        int[] commons = dual ? new int[] {3, 6} : new int[] {3};
        int[] closed = dual ? new int[] {2, 5} : new int[] {2};
        int[] open = dual ? new int[] {4, 7} : new int[] {4};
        h.runAtTickTime(5, () -> {
            f.connect(); for (int pad : commons) f.voltage(pad, 10);
            for (int pad : closed) f.resistance(pad, 1000); for (int pad : open) f.resistance(pad, 1000);
        });
        h.runAtTickTime(20, () -> {
            for (int i = 0; i < closed.length; ++i) { near(h, f.padVoltage(closed[i]), 10, 0.01, "Normally closed relay load"); near(h, f.padVoltage(open[i]), 0, 0.001, "Normally open relay load"); }
            f.voltage(0, 13);
        });
        h.runAtTickTime(30, () -> {
            for (int i = 0; i < closed.length; ++i) { near(h, f.padVoltage(closed[i]), 0, 0.002, "Energized NC contact / native leakage"); near(h, f.padVoltage(open[i]), 10, 0.01, "Energized NO contact"); }
            f.voltage(0, 0);
        });
        h.runAtTickTime(40, () -> { for (int pad : closed) near(h, f.padVoltage(pad), 10, 0.01, "Released relay contact"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardPotentiometerDividesCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.POTENTIOMETER.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(1, 1e9); });
        h.runAtTickTime(20, () -> {
            near(h, f.padVoltage(1), 5, 0.02, "Board potentiometer midpoint");
            f.component.set(PotentiometerComponent.VALUE, 25); f.component.stateUpdated();
        });
        h.runAtTickTime(30, () -> { near(h, f.padVoltage(1), 7.45, 0.02, "Changed board potentiometer"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardCapacitorChargesAndDischargesFromCee(GameTestHelper h) {
        var f = new BoardFixture(h, Components.CAPACITOR.get(), p -> p.set(CapacitorComponent.CAPACITANCE, 0.01f)); double[] previous = new double[1];
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(0, 100); });
        h.runAtTickTime(10, () -> previous[0] = ((CRSeriesWire) f.component.wires.getFirst()).capacitorVoltage());
        h.runAtTickTime(20, () -> {
            double actual = ((CRSeriesWire) f.component.wires.getFirst()).capacitorVoltage();
            near(h, actual, 10 + (previous[0] - 10) * Math.exp(-0.5 / 1.002), 0.05, "PG board capacitor timestep");
            previous[0] = actual; f.voltage(0, 0);
        });
        h.runAtTickTime(40, () -> {
            double actual = ((CRSeriesWire) f.component.wires.getFirst()).capacitorVoltage();
            h.assertTrue(actual > 0 && actual < previous[0] * 0.4, "PG board capacitor failed to discharge"); f.finish();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardInductorBuildsAndReleasesCeeCurrent(GameTestHelper h) {
        var f = new BoardFixture(h, Components.INDUCTOR.get(), p -> p.set(InductorComponent.INDUCTANCE, 1f));
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); f.resistance(0, 10); });
        h.runAtTickTime(25, () -> { near(h, f.current(), 10 / 10.15, 0.01, "PG board inductor current"); f.voltage(0, 0); });
        h.runAtTickTime(45, () -> { h.assertTrue(f.current() < 0.0001, "PG board inductor retained current without power"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardDisplayCountsCeePulses(GameTestHelper h) {
        var f = new BoardFixture(h, Components.DISPLAY_MODULE.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 24); });
        h.runAtTickTime(20, () -> { h.assertTrue(f.component.get(ModularDisplayComponent.INDEX) == 1, "PG display did not count CEE pulse"); f.voltage(0, 0); });
        h.runAtTickTime(30, () -> f.voltage(0, 24));
        h.runAtTickTime(40, () -> { h.assertTrue(f.component.get(ModularDisplayComponent.INDEX) == 2, "PG display did not count second CEE pulse"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardBarretterRegulatesCeeCurrent(GameTestHelper h) {
        var f = new BoardFixture(h, Components.BARRETTER_TUBE.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10); });
        h.runAtTickTime(20, () -> { near(h, f.current(), 0.1, 0.002, "Barretter regulated current"); f.voltage(0, 20); });
        h.runAtTickTime(30, () -> { near(h, f.current(), 0.1, 0.002, "Barretter changed supply regulation"); f.finish(); });
    }
    static void near(GameTestHelper h, double actual, double expected, double tolerance, String label) {
        h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) < tolerance, label + " expected " + expected + ", got " + actual);
    }
}
