package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.Arrays;
import java.util.Set;

/**
 * Fresh native CEE results with per-circuit availability. Native CEE's primitive
 * query API cannot express a status, so rejected readings are NaN. Public WWPG
 * measurements expose an explicit status and no numbers instead. Invalid samples
 * never reach CEE gameplay or wire heating; healthy circuits keep their results.
 */
public final class BridgeSimulationResults extends SimulationResults {
    private final Set<Node> unavailableNodes;
    private final boolean[] unavailableOrdinals;

    public BridgeSimulationResults(double[] samples, int substeps,
                                   Object2DoubleMap<DirectionalNodeConnection> currents,
                                   CircuitBuilder builder, InfrastructureSavedData infrastructure,
                                   Set<Node> unavailableNodes) {
        super(maskSamples(samples, substeps, builder, unavailableNodes), substeps,
                maskCurrents(currents, unavailableNodes), builder, infrastructure);
        this.unavailableNodes = Set.copyOf(unavailableNodes);
        unavailableOrdinals = new boolean[builder.allNodes().size()];
        for (var node : builder.allNodes()) unavailableOrdinals[node.ordinal] = unavailableNodes.contains(node.node);
    }

    public boolean available(Node node) { return !unavailableNodes.contains(node); }
    public boolean available(Node first, Node second) { return available(first) && available(second); }

    /** Cached ordinals are hints only; CEE rebuilds their ordering every tick. */
    @Override public int getNodeID(Node node, int hint) {
        var indexed = circuitBuilder.allNodes();
        if (hint >= 0 && hint < indexed.size() && indexed.get(hint).node.equals(node)) return hint;
        return super.getNodeID(node, -1);
    }

    private boolean available(int ordinal) {
        return ordinal < 0 || ordinal >= unavailableOrdinals.length || !unavailableOrdinals[ordinal];
    }

    @Override public double getVoltageAt(Node node) {
        return available(node) ? super.getVoltageAt(node) : Double.NaN;
    }
    @Override public double getVoltageAt(int ordinal) {
        return available(ordinal) ? super.getVoltageAt(ordinal) : Double.NaN;
    }
    @Override public double getVoltageAt(Node first, Node second) {
        return available(first, second) ? super.getVoltageAt(first, second) : Double.NaN;
    }
    @Override public double getVoltageAt(int first, int second) {
        return available(first) && available(second) ? super.getVoltageAt(first, second) : Double.NaN;
    }
    @Override public double getVoltageAtSqr(Node first, Node second) {
        return available(first, second) ? super.getVoltageAtSqr(first, second) : Double.NaN;
    }
    @Override public double getVoltageAtSqr(int first, int second) {
        return available(first) && available(second) ? super.getVoltageAtSqr(first, second) : Double.NaN;
    }
    @Override public double getCurrentThrough(Node first, Node second) {
        return available(first, second) ? super.getCurrentThrough(first, second) : Double.NaN;
    }
    @Override public double getHeatLoss(Node first, Node second) {
        return available(first, second) ? super.getHeatLoss(first, second) : Double.NaN;
    }

    private static double[] maskSamples(double[] samples, int substeps, CircuitBuilder builder, Set<Node> unavailable) {
        var copy = samples.clone();
        for (var node : builder.allNodes()) if (unavailable.contains(node.node))
            Arrays.fill(copy, node.ordinal * substeps, (node.ordinal + 1) * substeps, Double.NaN);
        return copy;
    }
    private static Object2DoubleMap<DirectionalNodeConnection> maskCurrents(
            Object2DoubleMap<DirectionalNodeConnection> currents, Set<Node> unavailable) {
        var copy = new Object2DoubleOpenHashMap<>(currents);
        for (var entry : copy.object2DoubleEntrySet())
            if (unavailable.contains(entry.getKey().node1()) || unavailable.contains(entry.getKey().node2()))
                entry.setValue(Double.NaN);
        return copy;
    }
}
