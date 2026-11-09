package org.cha0scollective.wwpg.gametest;

import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.patryk3211.powergrid.electricity.sim.solver.IResidualAdder;
import org.patryk3211.powergrid.electricity.sim.solver.ISolverHook;
import static org.cha0scollective.wwpg.gametest.SolverFailureGameTests.solver;

/** Added only to an experimental fixture copy; exercises vector blocks and tails. */
@GameTestHolder("wwpg_native_guard")
@PrefixGameTestTemplate(false)
public final class NativeReductionGameTests {
    private static ElectricalNetwork network(GameTestHelper h, int rows) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(CSolver.SolverBackend.NATIVE);
        for (int i = 0; i < rows; i++) network.addNode(new FloatingNode());
        var s = solver(network);
        h.assertTrue(s.type() == CSolver.SolverBackend.NATIVE, "Native reduction fixture requires native backend");
        s.allocate(rows);
        s.setPrecision(1e-7, 1e-12, 1e-6, .99);
        network.maxIterations = hooks -> 8;
        s.jacobianPrepareForWrite();
        for (int i = 0; i < rows; i++) s.jacobianAdd(i, i, 2);
        s.finishJacobianWrite();
        // Node additions warm the backend; finish the same initialization used
        // by the existing native fixtures before checking individual substeps.
        rhs(s, rows, 1);
        for (int warmup = 0; warmup < 3; warmup++) s.singleTick();
        h.assertTrue(s.isConverged(), "Wide native fixture did not finish initialization");
        state(h, s, rows, 1);
        return network;
    }
    private static void rhs(IMNA s, int rows, double scale) {
        s.zeroRHS();
        for (int i = 0; i < rows; i++) s.rhsAdd(i, 2 * (i + 1) * scale);
    }
    private static void state(GameTestHelper h, IMNA s, int rows, double scale) {
        for (int i = 0; i < rows; i++)
            h.assertTrue(Double.doubleToRawLongBits(s.stateVector().get(i, 0))
                    == Double.doubleToRawLongBits((i + 1) * scale), "Wrong exact wide-vector state at row " + i);
    }
    private static void accepted(GameTestHelper h, IMNA s, int rows, double scale) {
        rhs(s, rows, scale);
        s.singleTick();
        h.assertTrue(s.isConverged(), "Finite wide-vector solve did not converge");
        state(h, s, rows, scale);
    }
    private static void rejected(GameTestHelper h, IMNA s, int rows) {
        try { s.singleTick(); throw new AssertionError("Nonfinite wide vector was accepted"); }
        catch (IllegalStateException expected) {
            h.assertTrue(expected.getMessage().contains("info=-1004"), "Wrong wide-vector rejection");
        }
        h.assertTrue(!s.isConverged(), "Nonfinite vector retained acceptance");
        state(h, s, rows, 1);
    }

    @GameTest(template="empty", batch="wwpg_native_guard", timeoutTicks=80)
    public static void wideChangingInputsRetainEveryRowAndSixteenSubsteps(GameTestHelper h) {
        for (int rows : new int[]{16, 17, 32, 33, 67}) {
            var network = network(h, rows);
            try {
                var s = solver(network);
                for (int step = 1; step <= 16; step++) accepted(h, s, rows, step);
            } finally { network.cleanup(); }
        }
        h.succeed();
    }

    @GameTest(template="empty", batch="wwpg_native_guard", timeoutTicks=80)
    public static void wideNonfiniteRhsRollsBackAndRecovers(GameTestHelper h) {
        int rows = 67;
        var network = network(h, rows);
        try {
            var s = solver(network); accepted(h, s, rows, 1);
            for (int row : new int[]{0, 3, 4, 15, 16, 31, 32, 65, 66})
                for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                        Double.longBitsToDouble(0x7ff0000000000001L)}) {
                    rhs(s, rows, 2); s.rhsAdd(row, value);
                    rejected(h, s, rows);
                    accepted(h, s, rows, 1);
                }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty", batch="wwpg_native_guard", timeoutTicks=80)
    public static void wideNonfiniteResidualRollsBackAndRecovers(GameTestHelper h) {
        int rows = 67;
        var network = network(h, rows);
        try {
            var s = solver(network); accepted(h, s, rows, 1);
            for (int row : new int[]{0, 3, 4, 15, 16, 31, 32, 65, 66})
                for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                    network.innerHooks.add(new ISolverHook() {
                        public Collection<IElectricNode> coupledNodes() { return List.of(); }
                        public void startIteration(int iteration) { }
                        public void addResidual(IResidualAdder residual) { residual.add(row, value); }
                    });
                    rejected(h, s, rows);
                    network.innerHooks.clear(); accepted(h, s, rows, 1);
                }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template="empty", batch="wwpg_native_guard", timeoutTicks=80)
    public static void wideSubnormalResidualsKeepTheStoppingCriterion(GameTestHelper h) {
        int rows = 67;
        var network = network(h, rows);
        try {
            var s = solver(network);
            for (int row : new int[]{0, 3, 4, 15, 16, 31, 32, 65, 66})
                for (double value : new double[]{Double.MIN_VALUE, -Double.MIN_VALUE,
                        Math.nextDown(Double.MIN_NORMAL), -Math.nextDown(Double.MIN_NORMAL)}) {
                    s.zeroState(); s.zeroRHS(); s.rhsAdd(row, value);
                    s.singleTick();
                    h.assertTrue(s.isConverged(), "Tiny residual changed the stopping criterion");
                    state(h, s, rows, 0);
                }
            accepted(h, s, rows, 1);
            h.succeed();
        } finally { network.cleanup(); }
    }
}
