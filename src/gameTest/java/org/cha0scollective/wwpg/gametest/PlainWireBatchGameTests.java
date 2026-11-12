package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.experimental.PlainWireBatch;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

/** Release-default batching with independent bitwise power assertions. */
@GameTestHolder("wwpg_wire_power")
@PrefixGameTestTemplate(false)
public final class PlainWireBatchGameTests {
    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void batchHandlesLiveResistanceGroundAndFlips(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            var groundFirst = new ElectricWire(8, null, f.b);
            var groundSecond = new ElectricWire(8, f.b, null);
            f.network.addWire(groundFirst); f.network.addWire(groundSecond);
            f.settle();
            h.assertTrue(f.wire.power() > 0, "Live-edit fixture needs an actual powered wire");
            long before = f.stats().wwpg$batchedContributions();
            f.network.prepare(16);
            double expected = 0, first = 0, second = 0;
            for (int step = 0; step < 16; step++) {
                f.wire.setResistance(step < 8 ? 8 : 16);
                if (step == 8) { f.wire.flipNodes(); groundFirst.flipNodes(); }
                f.network.singleTick();
                expected += f.wire.internalPower() / 16;
                first += groundFirst.internalPower() / 16;
                second += groundSecond.internalPower() / 16;
                sameBits(h, expected, f.wire.power(), "Edited wire aggregation at step " + step);
                sameBits(h, first, groundFirst.power(), "First-ground aggregation at step " + step);
                sameBits(h, second, groundSecond.power(), "Second-ground aggregation at step " + step);
            }
            h.assertTrue(f.stats().wwpg$batchedContributions() > before, "No real batched contribution was exercised");
            long steps = f.stats().wwpg$batchedSteps();
            f.network.prepare(1); f.network.singleTick();
            h.assertTrue(f.stats().wwpg$batchedSteps() == steps, "One-step calculation entered the multi-step batch");
            sameBits(h, f.wire.internalPower(), f.wire.power(), "One-step power");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void batchRebuildsAfterMembershipRowsAndLeafChanges(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            f.settle();
            var extra = new FloatingNode();
            f.network.addNode(extra);
            var branch = new ElectricWire(4, f.a, extra);
            var ground = new ElectricWire(12, extra, f.b);
            f.network.addWire(branch); f.network.addWire(ground);
            f.checkedFrame(h, branch, ground);
            f.network.swapNodes(f.a, extra);
            f.checkedFrame(h, branch, ground);
            f.network.makeLeaf(extra, f.a);
            long beforeLeaf = f.stats().wwpg$batchedSteps();
            f.checkedFrame(h, branch, ground);
            h.assertTrue(f.stats().wwpg$batchedSteps() == beforeLeaf, "Tracked leaf entered an assigned-row batch");
            f.network.removeLeaf(extra);
            f.checkedFrame(h, branch, ground);
            long resumed = f.stats().wwpg$batchedSteps();
            h.assertTrue(resumed > beforeLeaf, "Leaf restoration failed to rebuild a working plan");
            f.network.removeWire(branch); f.network.removeWire(ground); f.network.removeNode(extra);
            f.checkedFrame(h, f.wire);
            h.assertTrue(f.stats().wwpg$batchedSteps() > resumed, "Removal/reindex failed to rebuild a working plan");
            f.network.clear();
            f.network.prepare(16); f.network.singleTick();
            h.assertTrue(f.network.getNodes().isEmpty(), "Cleared network retained nodes");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void unknownHookKeepsScalarCallbacksAndMayReleaseSolver(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            f.settle();
            long before = f.stats().wwpg$batchedSteps();
            int[] callbacks = {0};
            var custom = new ElectricWire(16, f.a, f.b) {
                @Override public void postMicroTick() {
                    callbacks[0]++;
                    super.postMicroTick();
                    f.network.cleanup();
                }
            };
            f.network.addWire(custom);
            f.network.prepare(16); f.network.singleTick();
            h.assertTrue(callbacks[0] == 1, "Unknown callback was skipped or replayed");
            h.assertTrue(((ElectricalNetworkAccessor) f.network).wwpg$solver() == null, "Callback did not release its solver");
            h.assertTrue(f.stats().wwpg$batchedSteps() == before, "Unknown callback entered cached-voltage execution");
            f.network.singleTick(); // Released JNI remains unused.
            h.assertTrue(callbacks[0] == 1, "Released network invoked another callback");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void customNodeRetainsVirtualVoltageReads(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            int[] reads = {0};
            var custom = new FloatingNode() {
                @Override public double getVoltage() { reads[0]++; return super.getVoltage(); }
            };
            f.network.addNode(custom);
            var branch = new ElectricWire(16, f.a, custom);
            var ground = new ElectricWire(16, custom, f.b);
            f.network.addWire(branch); f.network.addWire(ground);
            f.checkedFrame(h, branch, ground);
            h.assertTrue(reads[0] > 0, "Custom voltage method was bypassed");
            h.assertTrue(f.stats().wwpg$batchedSteps() == 0, "Custom node entered a cached-row plan");
        }
        h.succeed();
    }

    private static void sameBits(GameTestHelper h, double expected, double actual, String message) {
        h.assertTrue(Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual), message);
    }

    private static final class Circuit implements AutoCloseable {
        final ElectricalNetwork network = new ElectricalNetwork(false);
        final FloatingNode a = new FloatingNode(), b = new FloatingNode();
        final ElectricWire wire = new ElectricWire(8, a, b);
        Circuit(GameTestHelper h) {
            h.assertTrue(PlainWireBatch.ENABLED, "Release batching must be enabled");
            network.switchBackend(ModdedConfigs.server().electricity.solver.solverBackend.get());
            network.addNodes(a, b);
            network.addWire(wire);
            network.addWire(new CurrentSourceWire(b, null, 1000));
            network.addNode(new VoltageSourceCoupling(a, b, 0, 10));
        }
        PlainWireBatch.NetworkAccess stats() { return (PlainWireBatch.NetworkAccess) network; }
        void settle() { for (int frame = 0; frame < 8; frame++) network.calculate(16); }
        void checkedFrame(GameTestHelper h, ElectricWire... wires) {
            settle();
            network.prepare(16);
            double[] expected = new double[wires.length];
            for (int step = 0; step < 16; step++) {
                network.singleTick();
                for (int i = 0; i < wires.length; i++) {
                    expected[i] += wires[i].internalPower() / 16;
                    sameBits(h, expected[i], wires[i].power(), "Topology edit aggregation at step " + step);
                }
            }
            h.assertTrue(((ElectricalNetworkAccessor) network).wwpg$solver().type()
                    == ModdedConfigs.server().electricity.solver.solverBackend.get(), "Wrong actual backend");
        }
        @Override public void close() { network.cleanup(); }
    }
}
