package org.cha0scollective.wwpg.compat.cee12;

import org.cha0scollective.wwpg.bridge.CeeResultsAvailability;

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
public final class Cee12SimulationResults extends SimulationResults implements CeeResultsAvailability {
    // An empty mask makes every ordinal available without allocating a zero-filled array.
    private static final boolean[] ALL_AVAILABLE = new boolean[0];
    private final Set<Node> unavailableNodes;
    private final boolean[] unavailableOrdinals;

    private final Object2DoubleMap<DirectionalNodeConnection> currents;
    public Cee12SimulationResults(double[] samples,int substeps,Object2DoubleMap<DirectionalNodeConnection> currents,
            CircuitBuilder builder,InfrastructureSavedData infrastructure,Set<Node> unavailableNodes) {
        super(maskSamples(samples,substeps,builder,unavailableNodes),substeps,builder,infrastructure);
        this.currents=maskCurrents(currents,unavailableNodes,true);
        this.unavailableNodes = Set.copyOf(unavailableNodes);
        unavailableOrdinals = unavailableNodes.isEmpty() ? ALL_AVAILABLE : new boolean[builder.allNodes().size()];
        if (!unavailableNodes.isEmpty())
            for (var node : builder.allNodes()) unavailableOrdinals[node.ordinal] = unavailableNodes.contains(node.node);
    }

    public boolean allAvailable() { return unavailableNodes.isEmpty(); }
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
        if(!available(first,second))return Double.NaN;
        var connection=connection(first,second);
        if(currents.containsKey(connection))return currents.getDouble(connection);
        if(currents.containsKey(connection.invert()))return -currents.getDouble(connection.invert());
        return super.getCurrentThrough(first,second);
    }
    @Override public double getHeatLoss(Node first, Node second) {
        return available(first, second) ? super.getHeatLoss(first, second) : Double.NaN;
    }

    /** Match CEE's single-middle-node lookup while preserving the measured source current. */
    private DirectionalNodeConnection connection(Node first,Node second) {
        int a=getNodeID(first,-1),b=getNodeID(second,-1);
        if(a<0||b<0)return new DirectionalNodeConnection(first,second);
        var x=circuitBuilder.getNode(a);var y=circuitBuilder.getNode(b);
        if(x.adjacency.containsKey(b))return new DirectionalNodeConnection(first,second);
        int middle=-1;
        for(int neighbor:x.adjacency.keySet())if(y.adjacency.containsKey(neighbor)){
            if(middle>=0)return new DirectionalNodeConnection(first,second);middle=neighbor;
        }
        return new DirectionalNodeConnection(first,middle<0?second:circuitBuilder.getNode(middle).node);
    }

    private static double[] maskSamples(double[] samples, int substeps, CircuitBuilder builder, Set<Node> unavailable) {
        var copy = samples.clone();
        if (!unavailable.isEmpty())
            for (var node : builder.allNodes()) if (unavailable.contains(node.node))
                Arrays.fill(copy, node.ordinal * substeps, (node.ordinal + 1) * substeps, Double.NaN);
        return copy;
    }
    private static Object2DoubleMap<DirectionalNodeConnection> maskCurrents(
            Object2DoubleMap<DirectionalNodeConnection> currents, Set<Node> unavailable, boolean ownsCurrents) {
        Object2DoubleMap<DirectionalNodeConnection> result = ownsCurrents
                ? currents : new Object2DoubleOpenHashMap<>(currents);
        if (!unavailable.isEmpty()) {
            for (var entry : result.object2DoubleEntrySet())
                if (unavailable.contains(entry.getKey().node1()) || unavailable.contains(entry.getKey().node2()))
                    entry.setValue(Double.NaN);
        }
        return result;
    }
}
