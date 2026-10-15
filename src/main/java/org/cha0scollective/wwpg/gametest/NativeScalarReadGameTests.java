package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.bridge.NativeStateRead;
import org.cha0scollective.wwpg.mixin.PgNativeStateAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.solver.IMatrixAccess;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;

/** Compare the applied read path against PG's retained matrix accessor on both backends. */
@GameTestHolder("wwpg_wire_power")
@PrefixGameTestTemplate(false)
public final class NativeScalarReadGameTests {
    @BeforeBatch(batch = "wwpg_scalar_reads")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_scalar_reads", timeoutTicks = 40)
    public static void assignedReadsMatchPgAfterWritesExchangeAndReallocation(GameTestHelper h) {
        var network = prepared(h, ModdedConfigs.server().electricity.solver.solverBackend.get());
        var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
        try {
            var first = network.getNodes().getFirst();
            var second = network.getNodes().get(1);
            for (double value : new double[]{0.0, -0.0, 10, -10, Double.MIN_VALUE, Double.MAX_VALUE,
                    Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN}) {
                // This is a live write between repeated reads, never a cached frame.
                solver.stateVector().set(first.getIndex(), 0, value);
                check(h, network, first);
            }
            solver.stateVector().set(first.getIndex(), 0, 13);
            solver.stateVector().set(second.getIndex(), 0, -17);
            network.swapNodes(first, second);
            check(h, network, first); check(h, network, second);
            var foreign = new FloatingNode(); foreign.assignIndex(first.getIndex());
            // Identity-mismatch/leaf handling stays on PG's existing fallback.
            sameBits(h, solver.stateVector().safe_get(foreign.getIndex(), 0), network.getValue(foreign), "Foreign identity fallback");
            int index = first.getIndex();
            first.assignIndex(-1); sameBits(h, 0, network.getValue(first), "Unassigned row");
            first.assignIndex(network.getNodes().size() + 1); sameBits(h, 0, network.getValue(first), "Out of range row");
            first.assignIndex(index);
            solver.allocate(solver.stateVector().numRows());
            check(h, network, first); check(h, network, second);
        } finally {
            network.cleanup();
            if (solver instanceof PgNativeStateAccessor nativeState)
                sameBits(h, 0, NativeStateRead.assignedRow(nativeState, 0), "Cleanup released the native buffer");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_scalar_reads", timeoutTicks = 40)
    public static void backendReplacementUsesNewStateImmediately(GameTestHelper h) {
        var initial = ModdedConfigs.server().electricity.solver.solverBackend.get();
        var network = prepared(h, initial);
        try {
            for (var backend : new CSolver.SolverBackend[]{initial == CSolver.SolverBackend.NATIVE
                    ? CSolver.SolverBackend.JAVA : CSolver.SolverBackend.NATIVE, initial}) {
                network.switchBackend(backend);
                settle(h, network);
                h.assertTrue(((ElectricalNetworkAccessor) network).wwpg$solver().type() == backend, "Replacement backend differs");
                for (var node : network.getNodes()) check(h, network, node);
            }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template = "empty", batch = "wwpg_scalar_reads", timeoutTicks = 40)
    public static void customNativeSubclassKeepsItsVirtualStateAccessor(GameTestHelper h) throws ReflectiveOperationException {
        var network = prepared(h, ModdedConfigs.server().electricity.solver.solverBackend.get());
        var field = ElectricalNetwork.class.getDeclaredField("mna"); field.setAccessible(true);
        var original = field.get(network);
        var custom = new CustomNative(network);
        try {
            field.set(network, custom);
            sameBits(h, 123, network.getValue(network.getNodes().getFirst()), "Custom matrix accessor remains authoritative");
            h.assertTrue(custom.reads == 1, "Custom stateVector callback was bypassed or repeated");
            h.succeed();
        } finally { field.set(network, original); custom.cleanup(); network.cleanup(); }
    }

    private static ElectricalNetwork prepared(GameTestHelper h, CSolver.SolverBackend backend) {
        var network = new ElectricalNetwork(false); network.switchBackend(backend);
        var first = new FloatingNode(); var second = new FloatingNode();
        network.addNode(first); network.addNode(second);
        network.addWire(new ElectricWire(8, first, second));
        network.addWire(new CurrentSourceWire(second, null, 1000));
        network.addNode(new VoltageSourceCoupling(first, second, 10));
        settle(h, network);
        return network;
    }
    @GameTest(template = "empty", batch = "wwpg_scalar_reads", timeoutTicks = 40)
    public static void customIndexCallbackMayReleaseBackend(GameTestHelper h) {
        var network = prepared(h, ModdedConfigs.server().electricity.solver.solverBackend.get());
        try {
            var observedNetwork = network;
            var custom = new FloatingNode() {
                @Override public int getIndex() { observedNetwork.cleanup(); return 0; }
            };
            sameBits(h, 0, network.getValue(custom), "Solver released by an index callback stays unavailable");
            h.succeed();
        } finally { network.cleanup(); }
    }
    private static void settle(GameTestHelper h, ElectricalNetwork network) {
        for (int frame = 0; frame < 20; frame++) {
            network.prepare(16);
            for (int step = 0; step < 16; step++) network.singleTick();
            if (network.isConverged()) return;
        }
        h.fail("Scalar-read fixture failed to converge");
    }
    private static void check(GameTestHelper h, ElectricalNetwork network, org.patryk3211.powergrid.electricity.sim.node.INode node) {
        var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
        double original = solver.stateVector().safe_get(node.getIndex(), 0);
        if (solver.getClass() == NativeMNA.class)
            sameBits(h, original, NativeStateRead.assignedRow((PgNativeStateAccessor) solver, node.getIndex()), "Actual native matrix parity");
        sameBits(h, Double.isFinite(original) ? original : 0, network.getValue(node), "Assigned value normalization");
    }
    private static void sameBits(GameTestHelper h, double expected, double actual, String message) {
        h.assertTrue(Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual), message + ": " + expected + " vs " + actual);
    }
    private static final class CustomNative extends NativeMNA {
        int reads;
        CustomNative(ElectricalNetwork network) { super(network); }
        @Override public IMatrixAccess stateVector() {
            reads++;
            return new IMatrixAccess() {
                @Override public void set(int row, int col, double value) { throw new UnsupportedOperationException(); }
                @Override public double get(int row, int col) { return 123; }
                @Override public int numRows() { return Integer.MAX_VALUE; }
                @Override public int numCols() { return 1; }
            };
        }
    }
}
