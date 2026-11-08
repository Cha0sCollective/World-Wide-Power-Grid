package org.cha0scollective.wwpg.experimental;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.GraphedElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.solver.IMultiHooks;
import org.patryk3211.powergrid.electricity.sim.solver.JavaMNA;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;

/**
 * Experimental, server-thread-only PG 0.6.2 power aggregation. No solver work or
 * gameplay callbacks move to another thread. The actual PG hook set is untouched.
 */
public final class PlainWireBatch implements IMultiHooks {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.plainWireBatch");
    public static final boolean VERIFY = Boolean.getBoolean("wwpg.experimental.plainWireBatch.verify");

    public interface NetworkAccess {
        void wwpg$invalidateWireBatch();
        long wwpg$batchedSteps();
        long wwpg$batchedContributions();
    }

    public interface WireAccess {
        /** Adds precisely power / tickCount, in the original hook position. */
        boolean wwpg$accumulatePlainPower(double voltage);
    }

    private static long plans, unsupportedPlans, steps, contributions, voltageReads, scalarFallbacks;
    private static int largestPlainBatch;
    private final ElectricalNetwork network;
    private final AbstractElectricWire[] hooks;
    private final boolean[] plain;
    private final int[] first, second, rows;
    private final IElectricNode[] nodes;
    private final double[] voltages;
    private final boolean supported;
    private final Set<IMultiHooks> singleton = Collections.singleton(this);
    private long lastContributions;

    private PlainWireBatch(ElectricalNetwork network, Set<IMultiHooks> original) {
        this.network = network;
        hooks = new AbstractElectricWire[original.size()];
        plain = new boolean[hooks.length];
        first = new int[hooks.length];
        second = new int[hooks.length];
        var nodeIds = new IdentityHashMap<IElectricNode, Integer>();
        var unique = new ArrayList<IElectricNode>();
        boolean safe = network.getClass() == ElectricalNetwork.class
                || network.getClass() == GraphedElectricalNetwork.class;
        var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
        safe &= solver != null && (solver.getClass() == JavaMNA.class || solver.getClass() == NativeMNA.class);
        int index = 0, count = 0;
        for (var hook : original) {
            Class<?> type = hook.getClass();
            boolean eligible = type == ElectricWire.class || type == TransmissionLine.class;
            // These exact classes inherit the pure aggregation callback. Their
            // specialized formulas still run normally. Unknown hooks disable the
            // entire plan: they may observe power, mutate state, or release JNI.
            boolean pure = eligible || type == PropertyCurrentSourceWire.class
                    || type == CurrentSourceWire.class || type == SwitchedWire.class || type == CRSeriesWire.class;
            if (!pure) { safe = false; break; }
            var wire = (AbstractElectricWire) hook;
            if (wire.getNetwork() != network || wire.getNode1() == null && wire.getNode2() == null
                    || !assigned(network, wire.getNode1()) || !assigned(network, wire.getNode2())) {
                safe = false; break;
            }
            hooks[index] = wire;
            plain[index] = eligible;
            if (eligible) count++;
            first[index] = intern(wire.getNode1(), nodeIds, unique);
            second[index] = intern(wire.getNode2(), nodeIds, unique);
            index++;
        }
        supported = safe && count > 0;
        nodes = unique.toArray(IElectricNode[]::new);
        rows = new int[nodes.length];
        voltages = new double[nodes.length];
        for (int i = 0; i < nodes.length; i++) rows[i] = nodes[i].getIndex();
        plans++;
        if (!supported) unsupportedPlans++;
        else largestPlainBatch = Math.max(largestPlainBatch, count);
    }

    public static PlainWireBatch compile(ElectricalNetwork network, Set<IMultiHooks> original) {
        return new PlainWireBatch(network, original);
    }

    public static void invalidate(ElectricalNetwork network) {
        if (ENABLED && network instanceof NetworkAccess access) access.wwpg$invalidateWireBatch();
    }

    private static boolean assigned(ElectricalNetwork owner, IElectricNode node) {
        if (node == null) return true; // Reference ground; retain unary minus below.
        Class<?> type = node.getClass();
        if (type != FloatingNode.class && type != OwnedFloatingNode.class) return false;
        int row = node.getIndex();
        return node.getNetwork() == owner && row >= 0 && row < owner.getNodes().size()
                && owner.getNodes().get(row) == node && !owner.isLeaf(node);
    }

    private static int intern(IElectricNode node, IdentityHashMap<IElectricNode, Integer> ids,
                              ArrayList<IElectricNode> unique) {
        if (node == null) return -1;
        Integer previous = ids.get(node);
        if (previous != null) return previous;
        int index = unique.size();
        unique.add(node); ids.put(node, index);
        return index;
    }

    /** Select only after PG has solved and completed every postUpperSolve callback. */
    public Set<IMultiHooks> select(Set<IMultiHooks> original) {
        if (!supported || RestorationCoordinator.paused(network) || network.isDirty() || !network.isConverged())
            return original;
        var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
        if (solver == null) return original;
        // Never retain a native state view across substeps, cleanup, or callbacks.
        var state = solver.stateVector();
        var assigned = network.getNodes();
        for (int i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            int row = rows[i];
            if (node.getNetwork() != network || node.getIndex() != row
                    || row >= assigned.size() || assigned.get(row) != node) {
                invalidate(network);
                return original; // No contribution has been written yet.
            }
            double voltage = state.safe_get(row, 0);
            voltages[i] = Double.isFinite(voltage) ? voltage : 0;
        }
        voltageReads += nodes.length;
        return singleton;
    }

    @Override public void postMicroTick() {
        long count = 0;
        // Retain the exact ReferenceOpenHashSet iteration order, including the
        // positions of specialized formulas. Conductance is read every substep.
        for (int i = 0; i < hooks.length; i++) {
            var wire = hooks[i];
            if (plain[i]) {
                int a = first[i], b = second[i];
                double voltage = a < 0 ? -voltages[b] : b < 0 ? voltages[a] : voltages[a] - voltages[b];
                if (((WireAccess) wire).wwpg$accumulatePlainPower(voltage)) count++;
                else scalarFallbacks++;
            } else wire.postMicroTick();
        }
        lastContributions = count;
        contributions += count;
        steps++;
    }

    public long lastContributions() { return lastContributions; }

    public static void reportAndReset() {
        if (ENABLED) WorldWidePowerGrid.LOGGER.info(
                "WWPG experimental plain-wire batch: verify={}, plans={}, unsupported={}, steps={}, contributions={}, voltageReads={}, scalarFallbacks={}, largestPlainBatch={}",
                VERIFY, plans, unsupportedPlans, steps, contributions, voltageReads, scalarFallbacks, largestPlainBatch);
        plans = unsupportedPlans = steps = contributions = voltageReads = scalarFallbacks = 0;
        largestPlainBatch = 0;
    }
}
