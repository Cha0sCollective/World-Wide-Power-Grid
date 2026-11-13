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
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("wwpg.experimental.plainWireBatch", "true"));
    public static final boolean VERIFY = Boolean.getBoolean("wwpg.experimental.plainWireBatch.verify");
    public static final boolean PROPERTIES_ENABLED = ENABLED && Boolean.parseBoolean(System.getProperty("wwpg.experimental.plainWireBatch.properties", "true"));

    public interface NetworkAccess {
        void wwpg$invalidateWireBatch();
        long wwpg$batchedSteps();
        long wwpg$batchedContributions();
        long wwpg$batchedPropertyContributions();
    }

    public interface WireAccess {
        /** Adds precisely power / tickCount, in the original hook position. */
        boolean wwpg$accumulateResistivePower(double voltage);
    }

    private static long plans, unsupportedPlans, steps, contributions, voltageReads, scalarFallbacks;
    private static long propertyContributions, propertyScalarFallbacks;
    private static int largestPlainBatch, largestPropertyBatch;
    private final ElectricalNetwork network;
    private final AbstractElectricWire[] hooks;
    // 0: original callback, 1: plain wire, 2: exact CEE property-source stamp.
    private final byte[] kinds;
    private final int[] first, second, rows;
    private final IElectricNode[] nodes;
    private final double[] voltages;
    private final boolean supported;
    private final int batchedWireCount, propertyWireCount;
    private final Set<IMultiHooks> singleton = Collections.singleton(this);
    private long lastContributions, lastPropertyContributions;

    private PlainWireBatch(ElectricalNetwork network, Set<IMultiHooks> original) {
        this.network = network;
        hooks = new AbstractElectricWire[original.size()];
        kinds = new byte[hooks.length];
        first = new int[hooks.length];
        second = new int[hooks.length];
        var nodeIds = new IdentityHashMap<IElectricNode, Integer>();
        var unique = new ArrayList<IElectricNode>();
        boolean safe = network.getClass() == ElectricalNetwork.class
                || network.getClass() == GraphedElectricalNetwork.class;
        var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
        safe &= solver != null && (solver.getClass() == JavaMNA.class || solver.getClass() == NativeMNA.class);
        int index = 0, plainCount = 0, propertyCount = 0;
        for (var hook : original) {
            Class<?> type = hook.getClass();
            boolean plain = type == ElectricWire.class || type == TransmissionLine.class;
            boolean property = type == PropertyCurrentSourceWire.class;
            // These exact classes inherit the pure aggregation callback. Their
            // remaining specialized formulas run normally. Unknown hooks disable the
            // entire plan: they may observe power, mutate state, or release JNI.
            boolean pure = plain || property
                    || type == CurrentSourceWire.class || type == SwitchedWire.class || type == CRSeriesWire.class;
            if (!pure) { safe = false; break; }
            var wire = (AbstractElectricWire) hook;
            if (wire.getNetwork() != network || wire.getNode1() == null && wire.getNode2() == null
                    || !assigned(network, wire.getNode1()) || !assigned(network, wire.getNode2())) {
                safe = false; break;
            }
            hooks[index] = wire;
            if (plain) { kinds[index] = 1; plainCount++; }
            // The final WWPG stamp inherits I = V * G. Its injected source
            // current belongs to RHS preparation, not its power formula.
            else if (property && PROPERTIES_ENABLED) { kinds[index] = 2; propertyCount++; }
            first[index] = intern(wire.getNode1(), nodeIds, unique);
            second[index] = intern(wire.getNode2(), nodeIds, unique);
            index++;
        }
        batchedWireCount = plainCount + propertyCount;
        propertyWireCount = propertyCount;
        supported = safe && batchedWireCount > 0;
        nodes = unique.toArray(IElectricNode[]::new);
        rows = new int[nodes.length];
        voltages = new double[nodes.length];
        for (int i = 0; i < nodes.length; i++) rows[i] = nodes[i].getIndex();
        plans++;
        if (!supported) unsupportedPlans++;
        else {
            largestPlainBatch = Math.max(largestPlainBatch, plainCount);
            largestPropertyBatch = Math.max(largestPropertyBatch, propertyCount);
        }
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
        // Membership is fixed for this plan; only live-conductance fallbacks
        // change how many contributions used the fast path in this substep.
        long count = batchedWireCount, propertyCount = propertyWireCount;
        // Retain the exact ReferenceOpenHashSet iteration order, including the
        // positions of specialized formulas. Conductance is read every substep.
        for (int i = 0; i < hooks.length; i++) {
            var wire = hooks[i];
            byte kind = kinds[i];
            if (kind != 0) {
                int a = first[i], b = second[i];
                double voltage = a < 0 ? -voltages[b] : b < 0 ? voltages[a] : voltages[a] - voltages[b];
                if (!((WireAccess) wire).wwpg$accumulateResistivePower(voltage)) {
                    count--;
                    scalarFallbacks++;
                    if (kind == 2) { propertyCount--; propertyScalarFallbacks++; }
                }
            } else wire.postMicroTick();
        }
        lastContributions = count;
        lastPropertyContributions = propertyCount;
        contributions += count;
        propertyContributions += propertyCount;
        steps++;
    }

    public long lastContributions() { return lastContributions; }
    public long lastPropertyContributions() { return lastPropertyContributions; }

    public static void reportAndReset() {
        if (ENABLED) WorldWidePowerGrid.LOGGER.info(
                "WWPG experimental plain-wire batch: verify={}, plans={}, unsupported={}, steps={}, contributions={}, voltageReads={}, scalarFallbacks={}, largestPlainBatch={}, properties={}, propertyContributions={}, propertyScalarFallbacks={}, largestPropertyBatch={}",
                VERIFY, plans, unsupportedPlans, steps, contributions, voltageReads, scalarFallbacks, largestPlainBatch,
                PROPERTIES_ENABLED, propertyContributions, propertyScalarFallbacks, largestPropertyBatch);
        plans = unsupportedPlans = steps = contributions = voltageReads = scalarFallbacks = 0;
        propertyContributions = propertyScalarFallbacks = 0;
        largestPlainBatch = largestPropertyBatch = 0;
    }
}
