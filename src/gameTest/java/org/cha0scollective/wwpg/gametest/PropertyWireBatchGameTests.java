package org.cha0scollective.wwpg.gametest;

import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.experimental.PlainWireBatch;
import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.NetworkGraph;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.solver.IMultiHooks;

/** Applied property batching, including the actual package-private catenary reconnect. */
@GameTestHolder("wwpg_wire_power")
@PrefixGameTestTemplate(false)
public final class PropertyWireBatchGameTests {
    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void mixedPowerKeepsExactFallbackCountsAtSixteenAndThirtyTwoSteps(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            var plain = new ElectricWire(8, f.a, f.b);
            var property = new PropertyCurrentSourceWire(f.a, f.b, .125);
            var scalar = new CurrentSourceWire(f.a, f.b, .5);
            f.network.addWire(plain); f.network.addWire(property); f.network.addWire(scalar);
            f.settle();
            for (int steps : new int[]{16, 32}) {
                long before = f.stats().wwpg$batchedContributions();
                long beforeProperties = f.stats().wwpg$batchedPropertyContributions();
                long expectedFast = 0, expectedProperties = 0;
                var wires = new org.patryk3211.powergrid.electricity.sim.AbstractElectricWire[]{plain, f.wire, property, scalar};
                var expectedPower = new double[wires.length];
                f.network.prepare(steps);
                for (int step = 0; step < steps; step++) {
                    double first = switch (step % 4) { case 0 -> .25; case 1 -> 0; case 2 -> -.0625; default -> 1e-10; };
                    double second = step % 3 == 0 ? 0 : .125;
                    f.source.setVoltage(step % 3 == 0 ? 0 : step % 2 == 0 ? 10 : -10);
                    f.wire.setConductance(first); property.setConductance(second);
                    f.wire.setCurrent(step - 8); property.setCurrent(8 - step);
                    f.network.singleTick();
                    for (int i = 0; i < wires.length; i++) {
                        expectedPower[i] += wires[i].internalPower() / steps;
                        sameBits(h, expectedPower[i], wires[i].power(), "Mixed power at " + steps + " steps, index " + step);
                    }
                    long fastProperties = PlainWireBatch.PROPERTIES_ENABLED ? (first > 0 ? 1 : 0) + (second > 0 ? 1 : 0) : 0;
                    expectedProperties += fastProperties;
                    expectedFast += 1 + fastProperties;
                    h.assertTrue(f.stats().wwpg$batchedContributions() - before == expectedFast,
                            "Mixed fast-path count included scalar fallbacks");
                    h.assertTrue(f.stats().wwpg$batchedPropertyContributions() - beforeProperties == expectedProperties,
                            "Property count lost live-conductance fallback changes");
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void propertyOnlyBatchKeepsLiveConductanceCurrentAndSignedSupply(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            for (float voltage : new float[]{10, -10, 0}) {
                f.source.setVoltage(voltage);
                f.settle();
                long before = f.stats().wwpg$batchedPropertyContributions();
                f.network.prepare(16);
                double expected = 0;
                for (int step = 0; step < 16; step++) {
                    double conductance = switch (step % 5) { case 0 -> .25; case 1 -> .125;
                        case 2 -> 1e-10; case 3 -> 0; default -> -.0625; };
                    f.wire.setConductance(conductance);
                    f.wire.setCurrent(step - 8);
                    f.network.singleTick();
                    expected += f.wire.internalPower() / 16;
                    sameBits(h, expected, f.wire.power(), "Live property aggregation at step " + step);
                    sameBits(h, conductance, f.wire.conductance(), "Conductance edit was lost");
                    sameBits(h, step - 8, f.wire.getCurrent(), "Injected RHS current was changed");
                    h.assertTrue(Math.abs(f.a.getVoltage() - f.b.getVoltage() - voltage) < 1e-6,
                            "Source current or conductance edit changed the ideal supply voltage");
                }
                long added = f.stats().wwpg$batchedPropertyContributions() - before;
                h.assertTrue(PlainWireBatch.PROPERTIES_ENABLED ? added > 0 : added == 0,
                        "Property-only execution did not obey the separate opt-in flag");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void catenaryReconnectInvalidatesCachedPropertyEndpoints(GameTestHelper h) throws ReflectiveOperationException {
        try (var f = new Circuit(h)) {
            var next = new FloatingNode();
            f.network.addNode(next);
            f.network.addNode(new VoltageSourceCoupling(next, f.b, 0, -6));
            var graph = new NetworkGraph();
            graph.addNode(f.a); graph.addNode(f.b); graph.addNode(next);
            graph.connect(f.a, f.b, f.wire);
            f.settle();
            double oldPower = f.wire.power();
            int rows = f.network.getNodes().size();
            long before = f.stats().wwpg$batchedPropertyContributions();
            // Fixtures live in a separate module, so they cannot share the runtime package.
            var reconnect = PropertyCurrentSourceWire.class.getDeclaredMethod("reconnect", NetworkGraph.class,
                    IElectricNode.class, IElectricNode.class);
            reconnect.setAccessible(true);
            reconnect.invoke(f.wire, graph, next, f.b);
            f.settle();
            f.network.prepare(16);
            double expected = 0;
            for (int step = 0; step < 16; step++) {
                f.network.singleTick();
                expected += f.wire.internalPower() / 16;
                sameBits(h, expected, f.wire.power(), "Reconnected property used stale endpoints");
            }
            h.assertTrue(Math.abs(oldPower - 25) < 1e-6 && Math.abs(f.wire.power() - 9) < 1e-6,
                    "Reconnect did not change the actual property operating point");
            h.assertTrue(graph.getFirstWire(f.a, f.b) == null && graph.getFirstWire(next, f.b) == f.wire,
                    "Reconnect did not preserve the original wire in the new graph edge");
            h.assertTrue(f.wire.getNetwork() == f.network && f.network.getNodes().size() == rows,
                    "Reconnect replaced ownership or changed matrix size");
            long added = f.stats().wwpg$batchedPropertyContributions() - before;
            h.assertTrue(PlainWireBatch.PROPERTIES_ENABLED ? added > 0 : added == 0,
                    "Reconnect did not resume the selected property execution path");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void propertySnapshotPreservesGroundClampingAndExceptionalConductance(GameTestHelper h) {
        try (var f = new Circuit(h)) {
            f.settle();
            for (int ground = 0; ground < 3; ground++) {
                for (double voltage : new double[]{10, -10, 0.0, -0.0, Double.MIN_VALUE, 1e150,
                        Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                    for (double conductance : new double[]{.25, Double.MIN_VALUE, Double.MAX_VALUE,
                            0, -.25, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                        f.network.setValue(f.a, voltage); f.network.setValue(f.b, -0.0);
                        // This probe is never stamped: invalid conductance cannot reach either solver.
                        var probe = new PropertyCurrentSourceWire(ground == 2 ? null : f.a,
                                ground == 1 ? null : ground == 2 ? f.a : f.b, conductance);
                        probe.setNetwork(f.network); probe.prepare(16);
                        Set<IMultiHooks> original = Set.of(probe);
                        var plan = PlainWireBatch.compile(f.network, original);
                        var selected = plan.select(original);
                        h.assertTrue((selected != original) == PlainWireBatch.PROPERTIES_ENABLED,
                                "Property-only plan selection did not obey the opt-in flag");
                        double expected = 0.0 + probe.internalPower() / 16;
                        selected.forEach(IMultiHooks::postMicroTick);
                        sameBits(h, expected, probe.power(), "Snapshot/ground/conductance changed property power");
                        boolean fast = PlainWireBatch.PROPERTIES_ENABLED && Double.isFinite(conductance) && conductance > 0;
                        h.assertTrue(plan.lastPropertyContributions() == (fast ? 1 : 0),
                                "Exceptional conductance bypassed the scalar fallback");
                        probe.setNetwork(null);
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_world", timeoutTicks = 400)
    public static void physicalPropertyKeepsRestorationMaskingAndRecovery(GameTestHelper h) {
        // Reuse the real PG-source/CEE-resistor fixture without enabling unrelated loading tests.
        PropertyPowerGameTests.propertyPowerIsMaskedDuringRestorationAndResumesWithSignedSupply(h);
    }

    private static void sameBits(GameTestHelper h, double expected, double actual, String message) {
        h.assertTrue(Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual), message);
    }

    private static final class Circuit implements AutoCloseable {
        final ElectricalNetwork network = new ElectricalNetwork(false);
        final FloatingNode a = new FloatingNode(), b = new FloatingNode();
        final PropertyCurrentSourceWire wire = new PropertyCurrentSourceWire(a, b, .25);
        final VoltageSourceCoupling source = new VoltageSourceCoupling(a, b, 0, 10);
        Circuit(GameTestHelper h) {
            h.assertTrue(PlainWireBatch.ENABLED, "Release batching must be enabled");
            network.switchBackend(ModdedConfigs.server().electricity.solver.solverBackend.get());
            h.assertTrue(((ElectricalNetworkAccessor) network).wwpg$solver().type()
                    == ModdedConfigs.server().electricity.solver.solverBackend.get(), "Wrong actual backend");
            network.addNodes(a, b, source);
            network.addWire(wire);
            network.addWire(new CurrentSourceWire(b, null, 1000));
        }
        PlainWireBatch.NetworkAccess stats() { return (PlainWireBatch.NetworkAccess) network; }
        void settle() { for (int frame = 0; frame < 8; frame++) network.calculate(16); }
        @Override public void close() { network.cleanup(); }
    }
}
