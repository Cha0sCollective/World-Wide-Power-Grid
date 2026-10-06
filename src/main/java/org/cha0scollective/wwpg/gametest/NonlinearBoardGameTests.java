package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.fuse.FuseState;
import org.patryk3211.powergrid.electricity.sim.special.NeonBulbWire;

import static org.cha0scollective.wwpg.gametest.BoardComponentGameTests.near;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class NonlinearBoardGameTests {
    @GameTest(template = "empty", timeoutTicks = 90) public static void boardVaristorClampsCeeSupply(GameTestHelper h) {
        var f = new BoardFixture(h, Components.VARISTOR.get(), p -> {}); double[] off = new double[1];
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 50); f.resistance(0, 1000); });
        h.runAtTickTime(20, () -> { off[0] = f.current(); h.assertTrue(off[0] > 0 && off[0] < 0.006, "Varistor off-state leakage was " + off[0]); f.voltage(0, 150); });
        h.runAtTickTime(30, () -> {
            h.assertTrue(f.current() > off[0] * 8 && f.padVoltage(0) < 120, "PG varistor did not clamp CEE overvoltage"); f.voltage(0, 50);
        });
        h.runAtTickTime(45, () -> { near(h, f.current(), off[0], 0.0001, "Recovered varistor leakage"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardNeonStrikesAndExtinguishesFromCee(GameTestHelper h) { discharge(h, false); }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardRegulatorTubeStrikesAndExtinguishesFromCee(GameTestHelper h) { discharge(h, true); }
    private static void discharge(GameTestHelper h, boolean regulator) {
        var f = new BoardFixture(h, regulator ? Components.REGULATOR_TUBE.get() : Components.NEON_BULB.get(), p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, regulator ? 100 : 80); f.resistance(0, 5000); });
        h.runAtTickTime(25, () -> {
            h.assertTrue(((NeonBulbWire) f.component.wires.getFirst()).isLit(), "CEE voltage did not strike the PG discharge component");
            h.assertTrue(f.current() > 0.006 && f.current() < 0.009, "Discharge current did not include native holding voltage");
            near(h, Math.abs(f.padVoltage(0) - f.padVoltage(1)), regulator ? 60 : 45, 2, "Discharge holding voltage"); f.voltage(0, 10);
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(!((NeonBulbWire) f.component.wires.getFirst()).isLit() && f.current() < 0.0001, "PG discharge component retained conduction without holding current"); f.finish();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 120) public static void boardLightBulbHeatsAndCoolsFromCee(GameTestHelper h) {
        var f = new BoardFixture(h, Components.LIGHT_BULB.get(), p -> {}); float[] lit = new float[1];
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 12); });
        h.runAtTickTime(45, () -> {
            lit[0] = ((Component.FloatPair) f.component.customData).lerped(1);
            h.assertTrue(lit[0] > 0.1 && f.current() > 0.2, "CEE power did not heat/light PG board bulb"); f.voltage(0, 0);
        });
        h.runAtTickTime(85, () -> {
            h.assertTrue(((Component.FloatPair) f.component.customData).lerped(1) < lit[0] && f.current() < 0.00001, "PG board bulb did not cool after power removal"); f.finish();
        });
    }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardFuseTripsAndPlayerRepairsCeePower(GameTestHelper h) {
        var f = new BoardFixture(h, Components.FUSE_HOLDER.get(), p -> p.set(FuseHolderComponent.MAX_CURRENT, 1f));
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 0.1); f.resistance(0, 10); repair(h, f); });
        h.runAtTickTime(20, () -> {
            h.assertTrue(f.component.get(FuseHolderComponent.STATE) == FuseState.CLOSED && f.current() > 0.009, "Installed board fuse did not conduct");
            f.resistance(0, 0.1); f.voltage(0, 5);
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(f.component.get(FuseHolderComponent.STATE) == FuseState.BLOWN && f.current() < 0.00001, "PG board fuse failed to open on CEE overcurrent");
            f.voltage(0, 0.1); f.resistance(0, 10);
            Components.FUSE_HOLDER.get().use(f.board, f.component, h.makeMockPlayer(GameType.SURVIVAL)); repair(h, f);
        });
        h.runAtTickTime(45, () -> { h.assertTrue(f.component.get(FuseHolderComponent.STATE) == FuseState.CLOSED && f.current() > 0.009, "Player repair did not restore PG board current"); f.finish(); });
    }
    private static void repair(GameTestHelper h, BoardFixture f) {
        var player = h.makeMockPlayer(GameType.SURVIVAL); var stack = ModdedItems.IRON_WIRE.asStack(1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Components.FUSE_HOLDER.get().use(f.board, f.component, player);
        h.assertTrue(stack.isEmpty(), "Board fuse repair did not consume iron wire");
    }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardNpnAmplifiesCeeBaseCurrent(GameTestHelper h) { transistor(h, false); }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardPnpAmplifiesCeeBaseCurrent(GameTestHelper h) { transistor(h, true); }
    private static void transistor(GameTestHelper h, boolean pnp) {
        var f = new BoardFixture(h, pnp ? Components.BJT_PNP.get() : Components.BJT_NPN.get(), p -> {}); int polarity = pnp ? -1 : 1;
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 10 * polarity); f.resistance(0, 1000); f.resistance(1, 100000); });
        h.runAtTickTime(20, () -> { h.assertTrue(Math.abs(f.feedCurrent(0)) < 0.00002, "Unbiased transistor passed collector current"); f.voltage(1, 5 * polarity); });
        h.runAtTickTime(35, () -> {
            double collector = Math.abs(f.feedCurrent(0)), base = Math.abs(f.feedCurrent(1));
            h.assertTrue(collector > 0.0005 && base > 0, "CEE base drive did not enable PG transistor");
            near(h, collector / base, 20, 2, "PG transistor current gain"); f.voltage(1, 0);
        });
        h.runAtTickTime(50, () -> { h.assertTrue(Math.abs(f.feedCurrent(0)) < 0.00002, "PG transistor did not turn off"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 100) public static void boardVfetRespondsToCeeGateBias(GameTestHelper h) {
        var f = new BoardFixture(h, Components.VFET.get(), p -> {}); double[] current = new double[1];
        h.runAtTickTime(5, () -> { f.connect(); f.voltage(0, 20); f.resistance(0, 1000); f.voltage(2, -5); });
        h.runAtTickTime(20, () -> { h.assertTrue(f.current() < 0.0001, "Negative VFET gate did not cut off current"); f.voltage(2, 0); });
        h.runAtTickTime(35, () -> { current[0] = f.current(); h.assertTrue(current[0] > 0.0001, "Zero VFET gate supplied no drain current"); f.voltage(2, 2); });
        h.runAtTickTime(50, () -> { h.assertTrue(f.current() > current[0], "Positive VFET gate did not increase drain current"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 120) public static void boardTriodeHeaterAndGridUseCeePower(GameTestHelper h) { tube(h, false); }
    @GameTest(template = "empty", timeoutTicks = 120) public static void boardPentodeHeaterAndGridUseCeePower(GameTestHelper h) { tube(h, true); }
    private static void tube(GameTestHelper h, boolean pentode) {
        var f = new BoardFixture(h, pentode ? Components.PENTODE.get() : Components.TRIODE.get(), p -> {}); double[] on = new double[1];
        h.runAtTickTime(5, () -> {
            f.connect(); f.voltage(2, 100); f.resistance(2, 10000); f.voltage(3, 6);
            f.resistance(3, 0.01); f.resistance(4, 0.01); if (pentode) { f.voltage(5, 100); f.resistance(5, 1000); }
        });
        h.runAtTickTime(45, () -> {
            on[0] = f.current(); h.assertTrue(on[0] > 0.0001 && Math.abs(f.component.wires.get(1).potentialDifference()) > 5.8, "CEE heater did not enable PG tube conduction"); f.voltage(1, -30);
        });
        h.runAtTickTime(65, () -> { h.assertTrue(f.current() < on[0] * 0.05, "Negative PG tube grid failed to cut off plate current"); f.finish(); });
    }
    @GameTest(template = "empty", timeoutTicks = 140) public static void boardThyratronHeaterTriggerAndExtinctionUseCee(GameTestHelper h) {
        var f = new BoardFixture(h, Components.THYRATRON.get(), p -> {});
        h.runAtTickTime(5, () -> {
            f.connect(); f.voltage(2, 100); f.resistance(2, 1000); f.voltage(1, -5); f.voltage(3, 7);
            f.resistance(3, 0.01); f.resistance(4, 0.01);
        });
        h.runAtTickTime(15, () -> {
            double heater = Math.abs(f.component.wires.get(1).potentialDifference());
            h.assertTrue(heater > 1, "Thyratron heater received no CEE voltage"); f.voltage(3, 7 * 6 / heater);
        });
        h.runAtTickTime(45, () -> { h.assertTrue(f.current() < 0.0001, "Negative thyratron grid failed to prevent ignition"); f.voltage(1, 0); });
        h.runAtTickTime(65, () -> { h.assertTrue(f.current() > 0.05 && f.component.get(NeonBulbComponent.LIT), "Heated PG thyratron did not ignite from CEE grid bias"); f.voltage(2, 0); });
        h.runAtTickTime(85, () -> { h.assertTrue(f.current() < 0.0001 && !f.component.get(NeonBulbComponent.LIT), "PG thyratron retained discharge after anode power removal"); f.finish(); });
    }
}
