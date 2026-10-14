package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.INode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.IStaticResidual;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Checks the applied RHS mixin, its native fallback, and changing powered residuals. */
@GameTestHolder("wwpg_rhs")
@PrefixGameTestTemplate(false)
public final class ResidualAssemblyGameTests {
    @BeforeBatch(batch = "wwpg_rhs")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_rhs", timeoutTicks = 40)
    public static void emptyLeafAssemblyPreservesEveryChangingContributionAndOrder(GameTestHelper h)
            throws ReflectiveOperationException {
        var recording = new RecordingSolver();
        var network = new ProbeNetwork(recording.solver);
        var node = new FloatingNode();
        for (int i = 0; i < 3; i++) network.add(new ProbeResidual(node));
        var nativeOrder = network.order();
        for (int step = 0; step < 16; step++) {
            var expected = new ArrayList<Addition>();
            double[] cancellation = {1e20, step + 1, -1e20};
            for (int i = 0; i < nativeOrder.size(); i++) {
                var residual = nativeOrder.get(i);
                residual.values = List.of(new Addition(0, cancellation[i]), new Addition(1, step - i * .25));
                expected.addAll(residual.values);
            }
            network.compute();
            h.assertTrue(recording.additions.equals(expected), "RHS changed native contribution order or reused an old source value");
            for (var residual : nativeOrder) {
                h.assertTrue(residual.incidenceReads == 0, "Empty-leaf assembly still constructed affected-node collections");
                h.assertTrue(residual.assemblies == step + 1, "A changing residual was skipped or assembled twice");
            }
        }
        h.assertTrue(recording.clears == 16, "RHS was not cleared exactly once per substep");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_rhs", timeoutTicks = 40)
    public static void nativeLeafFilteringSurvivesEmptyNonemptyAndResidualChanges(GameTestHelper h)
            throws ReflectiveOperationException {
        var recording = new RecordingSolver();
        var network = new ProbeNetwork(recording.solver);
        var leaf = new FloatingNode(); var retained = new FloatingNode();
        var touchingLeaf = new ProbeResidual(leaf, retained);
        var independent = new ProbeResidual(retained);
        touchingLeaf.values = List.of(new Addition(0, 9));
        independent.values = List.of(new Addition(1, -4));
        network.add(touchingLeaf); network.add(independent);
        network.compute();
        h.assertTrue(recording.additions.size() == 2, "Empty-leaf fixture did not assemble both residuals");
        network.leaf(leaf, retained);
        network.compute();
        h.assertTrue(recording.additions.equals(independent.values), "Native fallback did not exclude a residual touching an eliminated leaf");
        h.assertTrue(touchingLeaf.incidenceReads == 1 && independent.incidenceReads == 1,
                "Nonempty-leaf assembly bypassed native incidence checks");
        network.remove(independent);
        var replacement = new ProbeResidual(retained);
        replacement.values = List.of(new Addition(1, 7)); network.add(replacement);
        network.compute();
        h.assertTrue(recording.additions.equals(replacement.values), "Assembly retained a removed residual or missed its replacement");
        network.restoreLeaves();
        network.compute();
        var expected = new ArrayList<Addition>();
        for (var residual : network.order()) expected.addAll(residual.values);
        h.assertTrue(recording.additions.equals(expected), "Restored nodes did not return to the empty-leaf path");
        h.assertTrue(touchingLeaf.incidenceReads == 2 && replacement.incidenceReads == 1,
                "Empty-leaf decision was stale after restoration");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_rhs", timeoutTicks = 40)
    public static void changingSourcesAndCapacitorHistoryKeepAllSixteenSubsteps(GameTestHelper h) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(ModdedConfigs.server().electricity.solver.solverBackend.get());
        try {
            var first = new FloatingNode(); var second = new FloatingNode();
            network.addNode(first); network.addNode(second);
            network.addWire(new ElectricWire(1000, second, null));
            var source = new VoltageSourceCoupling(first, second, 0);
            network.addNode(source);
            var current = new CountingCurrent(first, null);
            network.addWire(current);
            var capacitor = new CountingCapacitor(first, second);
            network.addWire(capacitor);
            capacitor.setVoltage(3);
            double storedVoltage = 3;
            int acceptedSteps = 0;
            for (int frame = 0; frame < 4; frame++) {
                network.prepare(16);
                for (int step = 0; step < 16; step++) {
                    double voltage = (step - 8) * .5 + frame;
                    double injection = (step - 4) * .01;
                    source.setVoltage(voltage); current.setCurrent(injection);
                    network.singleTick();
                    if (!network.isConverged()) continue;
                    acceptedSteps++;
                    BoardComponentGameTests.near(h, first.getVoltage() - second.getVoltage(), voltage, 1e-7,
                            "Changing voltage-source RHS");
                    BoardComponentGameTests.near(h, second.getVoltage(), injection * 1000, 1e-6,
                            "Changing ground-ended current-source RHS");
                    double expectedCurrent = (voltage - storedVoltage) / (10 + network.getDeltaTime() / .02);
                    BoardComponentGameTests.near(h, capacitor.current(), expectedCurrent, 1e-7,
                            "Capacitor RHS did not use the preceding accepted history");
                    storedVoltage = (voltage - expectedCurrent * 10) * .99999;
                }
            }
            h.assertTrue(acceptedSteps >= 48, "Powered residual fixture did not obtain enough accepted substeps");
            h.assertTrue(current.assemblies == 64 && capacitor.assemblies == 64,
                    "Static-residual classification reduced the frequency of dynamic source/history updates");
            h.assertTrue(current.incidenceReads == 0 && capacitor.incidenceReads == 0,
                    "Powered circuit did not use empty-leaf assembly");
            h.assertTrue(((ElectricalNetworkAccessor) network).wwpg$solver().type()
                            == ModdedConfigs.server().electricity.solver.solverBackend.get(), "Powered fixture ran the wrong backend");
            h.succeed();
        } finally { network.cleanup(); }
    }

    private record Addition(int row, double value) { }

    private static final class RecordingSolver {
        final List<Addition> additions = new ArrayList<>();
        int clears;
        final IMNA solver = (IMNA) Proxy.newProxyInstance(IMNA.class.getClassLoader(), new Class<?>[]{IMNA.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "zeroRHS" -> { additions.clear(); clears++; }
                        case "rhsAdd" -> additions.add(new Addition((int) arguments[0], (double) arguments[1]));
                        default -> throw new AssertionError("Unexpected solver call: " + method.getName());
                    }
                    return null;
                });
    }

    private static final class ProbeNetwork extends ElectricalNetwork {
        ProbeNetwork(IMNA solver) { super(false, ignored -> solver); }
        void add(ProbeResidual residual) { residuals.add(residual); }
        void remove(ProbeResidual residual) { residuals.remove(residual); }
        List<ProbeResidual> order() {
            // PG uses the set iterator, whose order is not the set's stream/spliterator order.
            var order = new ArrayList<ProbeResidual>();
            for (var residual : residuals) order.add((ProbeResidual) residual);
            return order;
        }
        void leaf(IElectricNode leaf, IElectricNode tracked) { leafNodes.put(leaf, tracked); }
        void restoreLeaves() { leafNodes.clear(); }
        void compute() throws ReflectiveOperationException {
            var method = ElectricalNetwork.class.getDeclaredMethod("computeRHS");
            method.setAccessible(true); method.invoke(this);
        }
    }

    private static final class ProbeResidual implements IStaticResidual {
        final List<INode> nodes;
        List<Addition> values = List.of();
        int incidenceReads, assemblies;
        ProbeResidual(INode... nodes) { this.nodes = List.of(nodes); }
        @Override public List<INode> affectedNodes() { incidenceReads++; return nodes; }
        @Override public void addStaticResidual(IResidualAdder add) {
            assemblies++;
            for (var value : values) add.add(value.row, value.value);
        }
    }

    private static final class CountingCurrent extends CurrentSourceWire {
        int incidenceReads, assemblies;
        CountingCurrent(IElectricNode first, IElectricNode second) { super(first, second, 0d); }
        @Override public List<INode> affectedNodes() { incidenceReads++; return super.affectedNodes(); }
        @Override public void addStaticResidual(IResidualAdder add) { assemblies++; super.addStaticResidual(add); }
    }

    private static final class CountingCapacitor extends CRSeriesWire {
        int incidenceReads, assemblies;
        CountingCapacitor(IElectricNode first, IElectricNode second) { super(.02, 10, first, second); }
        @Override public List<INode> affectedNodes() { incidenceReads++; return super.affectedNodes(); }
        @Override public void addStaticResidual(IResidualAdder add) { assemblies++; super.addStaticResidual(add); }
    }
}
