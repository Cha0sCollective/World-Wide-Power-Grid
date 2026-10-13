package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;

import java.nio.ByteBuffer;

/** Actual command queues and paired native/Java circuit histories; malformed commands are never executed. */
@GameTestHolder("wwpg_native_rhs")
@PrefixGameTestTemplate(false)
public final class NativeRhsGameTests {
    private static final int COMMAND_BYTES = Integer.BYTES + Double.BYTES;
    private static final double SOLVER_ZERO_TOLERANCE = 1e-6;

    private static ByteBuffer commands(NativeMNA solver) throws ReflectiveOperationException {
        var field = NativeMNA.class.getDeclaredField("rhsOps");
        field.setAccessible(true);
        return (ByteBuffer) field.get(solver);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void validZeroCommandsAreSkippedWhileMalformedAndNonfiniteCommandsRemainVisible(GameTestHelper h)
            throws ReflectiveOperationException {
        h.assertTrue(NativeMNA.isSupported(), "Command regression requires the pinned native backend");
        var network = new ElectricalNetwork(false);
        var solver = new NativeMNA(network);
        try {
            var queue = commands(solver);
            // Before allocation there is no valid row. Keep the original command for diagnostics.
            solver.rhsAdd(0, 0);
            h.assertTrue(queue.position() == COMMAND_BYTES, "A pre-allocation zero command was silently discarded");
            solver.allocate(2); // Discard the unexecuted pre-allocation command.
            for (int index = 0; index < 1024; index++) {
                solver.rhsAdd(0, 0.0); solver.rhsAdd(1, -0.0);
            }
            h.assertTrue(queue.position() == 0, "Valid signed zeroes filled the native command buffer");

            int[] invalidRows = {-1, -2, 2, Integer.MAX_VALUE};
            for (int index = 0; index < invalidRows.length; index++) {
                double value = index % 2 == 0 ? 0.0 : -0.0;
                solver.rhsAdd(invalidRows[index], value);
                h.assertTrue(queue.getInt(index * COMMAND_BYTES) == invalidRows[index]
                                && Double.doubleToRawLongBits(queue.getDouble(index * COMMAND_BYTES + Integer.BYTES))
                                == Double.doubleToRawLongBits(value),
                        "Malformed zero command changed or disappeared before native validation");
            }
            h.assertTrue(queue.position() == invalidRows.length * COMMAND_BYTES,
                    "Malformed row commands did not preserve native queue positions");
            // Fewer than 256 commands were queued. Never flush malformed rows to the native binary.
            solver.allocate(2);
            double[] retained = {Double.MIN_VALUE, -Double.MIN_VALUE, 1e16, 1, -1e16,
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int index = 0; index < retained.length; index++) {
                solver.rhsAdd(index % 2, retained[index]);
                solver.rhsAdd(index % 2, 0);
                h.assertTrue(queue.getInt(index * COMMAND_BYTES) == index % 2
                                && Double.doubleToRawLongBits(queue.getDouble(index * COMMAND_BYTES + Integer.BYTES))
                                == Double.doubleToRawLongBits(retained[index]),
                        "Nonzero, subnormal or nonfinite input was dropped or reordered");
            }
            h.assertTrue(queue.position() == retained.length * COMMAND_BYTES,
                    "Skipped zeroes altered the ordered nonzero command count");
            // This queue intentionally contains nonfinite values; position proof ends without a solve.
        } finally { solver.cleanup(); network.cleanup(); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeFlushesRetainFloatingPointAdditionOrderCancellationAndEmptyFollowingStep(GameTestHelper h)
            throws ReflectiveOperationException {
        h.assertTrue(NativeMNA.isSupported(), "Command regression requires the pinned native backend");
        var network = new ElectricalNetwork(false);
        var solver = new NativeMNA(network);
        try {
            solver.allocate(1);
            solver.jacobianPrepareForWrite(); solver.jacobianAdd(0, 0, 1); solver.finishJacobianWrite();
            var queue = commands(solver);
            double[] ordered = {Double.MIN_VALUE, -Double.MIN_VALUE, 1e16, 1, -1e16, 3};
            double expected = 0;
            for (double value : ordered) { solver.rhsAdd(0, value); solver.rhsAdd(0, -0.0); expected += value; }
            h.assertTrue(expected == 3 && queue.position() == ordered.length * COMMAND_BYTES,
                    "Order-sensitive native reference did not begin with three retained units");
            // Cross three complete 256-command flushes, retaining only additive signed contributions.
            for (int index = 0; index < 384; index++) {
                solver.rhsAdd(0, 2); solver.rhsAdd(0, 0); solver.rhsAdd(0, -1); expected += 2; expected -= 1;
            }
            solver.singleTick();
            h.assertTrue(solver.isConverged(), "Native RHS boundary circuit did not converge");
            BoardComponentGameTests.near(h, solver.stateVector().get(0, 0), expected, 1e-10,
                    "Native buffer flushes reordered or lost signed/cancelling contributions");
            h.assertTrue(queue.position() == 0, "Completed native solve retained queued commands");

            solver.zeroRHS(); solver.rhsAdd(0, -0.0); solver.singleTick();
            h.assertTrue(solver.isConverged(), "Empty native RHS step did not converge");
            BoardComponentGameTests.near(h, solver.stateVector().get(0, 0), 0, 1e-10,
                    "A zero-only step retained current from the previous step");
        } finally { solver.cleanup(); network.cleanup(); }
        h.succeed();
    }

    private record Circuit(ElectricalNetwork network, FloatingNode positive, FloatingNode negative,
                           CurrentSourceWire source, ElectricWire load, CRSeriesWire storage) { }

    private static Circuit circuit(CSolver.SolverBackend backend, boolean storage) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(backend); network.warmUp(-1);
        var positive = new FloatingNode(); var negative = new FloatingNode();
        network.addNodes(positive, negative); network.addWire(new ElectricWire(.001, negative, null));
        var load = new ElectricWire(2, positive, negative); network.addWire(load);
        for (int index = 0; index < 600; index++) network.addWire(new CurrentSourceWire(positive, negative, .001));
        var source = new CurrentSourceWire(positive, negative, 0); network.addWire(source);
        var capacitor = storage ? new CRSeriesWire(.002, 1, positive, negative) : null;
        if (capacitor != null) { capacitor.setVoltage(5); network.addWire(capacitor); }
        return new Circuit(network, positive, negative, source, load, capacitor);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeAndJavaRetainSixteenSubstepFramesZeroCurrentConductanceAndStoredEnergy(GameTestHelper h) {
        h.assertTrue(NativeMNA.isSupported(), "Paired circuit regression requires the pinned native backend");
        for (boolean storage : new boolean[]{false, true}) {
            var nativeCircuit = circuit(CSolver.SolverBackend.NATIVE, storage);
            var javaCircuit = circuit(CSolver.SolverBackend.JAVA, storage);
            try {
                h.assertTrue(((ElectricalNetworkAccessor) nativeCircuit.network).wwpg$solver().type() == CSolver.SolverBackend.NATIVE
                                && ((ElectricalNetworkAccessor) javaCircuit.network).wwpg$solver().type() == CSolver.SolverBackend.JAVA,
                        "Paired correctness check did not select distinct native and Java solvers");
                double[] currents = {0, 2.2, 0, -2.2, 0, 1.1, -1.1, 0, 2.2, 0, -2.2, 0, 0, 1.1, 0, -1.1};
                for (double current : currents) {
                    nativeCircuit.source.setCurrent(current); javaCircuit.source.setCurrent(current);
                    nativeCircuit.network.prepare(16); javaCircuit.network.prepare(16);
                    int nativeStamp = nativeCircuit.network.getStamp(), javaStamp = javaCircuit.network.getStamp();
                    for (int step = 0; step < 16; step++) {
                        nativeCircuit.network.singleTick(); javaCircuit.network.singleTick();
                        h.assertTrue(nativeCircuit.network.isConverged() && javaCircuit.network.isConverged(),
                                "Zero-current conductor/history circuit did not accept its electrical substep");
                        double voltage = nativeCircuit.positive.getVoltage() - nativeCircuit.negative.getVoltage();
                        double reference = javaCircuit.positive.getVoltage() - javaCircuit.negative.getVoltage();
                        BoardComponentGameTests.near(h, voltage, reference, SOLVER_ZERO_TOLERANCE, "Paired native/Java signed voltage");
                        BoardComponentGameTests.near(h, nativeCircuit.load.current(), javaCircuit.load.current(), SOLVER_ZERO_TOLERANCE,
                                "Paired native/Java signed load current");
                        if (!storage) {
                            BoardComponentGameTests.near(h, voltage, current / 1.1, 1e-8,
                                    "Zero-current Norton conductance or source polarity changed");
                        } else {
                            BoardComponentGameTests.near(h, nativeCircuit.storage.capacitorVoltage(), javaCircuit.storage.capacitorVoltage(),
                                    SOLVER_ZERO_TOLERANCE, "Zero-source frames altered native capacitor history");
                        }
                    }
                    h.assertTrue(nativeCircuit.network.getStamp() - nativeStamp == 16
                                    && javaCircuit.network.getStamp() - javaStamp == 16,
                            "RHS optimization changed physical substep frequency");
                    BoardComponentGameTests.near(h, nativeCircuit.load.power(), javaCircuit.load.power(), SOLVER_ZERO_TOLERANCE,
                            "Paired accepted-substep power aggregation changed");
                }
            } finally { nativeCircuit.network.cleanup(); javaCircuit.network.cleanup(); }
        }
        h.succeed();
    }
}
