package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.WrappedIndexedNode;
import com.george_vi.electroenergetics.simulation.WireType;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BridgeSimulationResultsTest {
    @Test void aReorderedBuilderCannotReadRejectedNodesThroughAnUnrelatedWiresCachedOrdinals() {
        Node first = new AttachedNode(0, "healthy"), second = new AttachedNode(1, "healthy");
        Node rejectedFirst = new AttachedNode(0, "rejected"), rejectedSecond = new AttachedNode(1, "rejected");
        var wireType = new WireType.Builder(null).resistance(() -> 1).build();
        var wire = new ConnectionEntry(Vec3.ZERO, new Vec3(3, 0, 0), List.of(),
                WireData.ofLength(wireType, 3), new AABB(0, 0, 0, 3, 1, 1), null);
        var initial = results(new Node[]{first, second, rejectedFirst, rejectedSecond},
                new double[]{5, 5, 0, 0, 70, 70, 0, 0}, Set.of());
        assertEquals(5, wire.getVoltageOnWire(initial, first, second));
        var reordered = results(new Node[]{rejectedFirst, rejectedSecond, second, first},
                new double[]{70, 70, 0, 0, 0, 0, 5, 5}, Set.of(rejectedFirst, rejectedSecond));
        reordered.circuitBuilder.connect(3, 2, ElectricalProperties.resistor(3));
        var unsafeNativeHints = new SimulationResults(
                new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, 5, 5},
                2, new Object2DoubleOpenHashMap<>(), reordered.circuitBuilder, null);
        assertEquals(0, unsafeNativeHints.getNodeID(first, 0), "Pinned CEE trusts an unrelated in-range hint");
        assertTrue(Double.isNaN(wire.getVoltageOnWire(unsafeNativeHints, first, second)),
                "The original native query sends rejected samples into a healthy wire's temperature calculation");
        // Restore the same masked builder's valid RMS fields after creating the
        // native comparison above. Public queries still mask rejected nodes.
        reordered = results(new Node[]{rejectedFirst, rejectedSecond, second, first},
                new double[]{70, 70, 0, 0, 0, 0, 5, 5}, Set.of(rejectedFirst, rejectedSecond));
        reordered.circuitBuilder.connect(3, 2, ElectricalProperties.resistor(3));
        assertTrue(reordered.available(first, second));
        assertEquals(5, wire.getVoltageOnWire(reordered, first, second),
                "A stale native wire hint must resolve its stable node, rather than a rejected circuit at that ordinal");
        assertEquals(3, reordered.getNodeID(first, 0));
        assertEquals(2, reordered.getNodeID(second, 1));
        assertEquals(-1, reordered.getNodeID(new AttachedNode(0, "absent"), 0));
        assertEquals(5, wire.getWireVoltageToGround(reordered, first, second),
                "Healthy wire hazard voltage must remain tied to its actual endpoints after a rejected-node reorder");
        assertEquals(25.0 / 3, reordered.getHeatLoss(first, second), 1e-12,
                "Healthy native heat loss must retain its solved voltage and resistance");
    }

    private static BridgeSimulationResults results(Node[] order, double[] samples, Set<Node> unavailable) {
        var nodes = new ArrayList<WrappedIndexedNode>();
        var indexes = new Object2IntOpenHashMap<Node>();
        indexes.defaultReturnValue(-1);
        for (var node : order) {
            indexes.put(node, nodes.size()); nodes.add(new WrappedIndexedNode(node, nodes.size()));
        }
        return new BridgeSimulationResults(samples, 2, new Object2DoubleOpenHashMap<>(),
                new CircuitBuilder(order.length, nodes, indexes), null, unavailable);
    }

    @Test void rejectedCircuitHasNoNativeNumbersWhileUnrelatedCircuitKeepsFreshResults() {
        Node rejectedPositive = new AttachedNode(0, "rejected"), rejectedNegative = new AttachedNode(1, "rejected");
        Node healthyPositive = new AttachedNode(0, "healthy"), healthyNegative = new AttachedNode(1, "healthy");
        var nodes = new ArrayList<WrappedIndexedNode>();
        var indexes = new Object2IntOpenHashMap<Node>();
        for (var node : new Node[]{rejectedPositive, rejectedNegative, healthyPositive, healthyNegative}) {
            indexes.put(node, nodes.size()); nodes.add(new WrappedIndexedNode(node, nodes.size()));
        }
        var builder = new CircuitBuilder(4, nodes, indexes);
        builder.connect(0, 1, ElectricalProperties.resistor(100));
        builder.connect(2, 3, ElectricalProperties.resistor(100));
        var currents = new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        currents.put(new DirectionalNodeConnection(rejectedPositive, rejectedNegative), .7);
        currents.put(new DirectionalNodeConnection(healthyPositive, healthyNegative), .2);
        double[] original = {70, 70, 0, 0, 20, 20, 0, 0};
        var results = new BridgeSimulationResults(original, 2, currents, builder, null,
                Set.of(rejectedPositive, rejectedNegative));
        assertFalse(results.available(rejectedPositive, rejectedNegative));
        assertTrue(results.available(healthyPositive, healthyNegative));
        assertTrue(Double.isNaN(results.getVoltageAt(rejectedPositive)));
        assertTrue(Double.isNaN(results.getVoltageAt(0)));
        assertTrue(Double.isNaN(results.getVoltageAt(rejectedPositive, rejectedNegative)));
        assertTrue(Double.isNaN(results.getVoltageAtSqr(0, 1)));
        assertTrue(Double.isNaN(results.getCurrentThrough(rejectedPositive, rejectedNegative)));
        assertTrue(Double.isNaN(results.getHeatLoss(rejectedPositive, rejectedNegative)));
        assertTrue(Double.isNaN(results.getVoltages(rejectedPositive)[0]));
        assertTrue(Double.isNaN(results.getVoltages(rejectedPositive, new double[2])[1]));
        assertEquals(20, results.getVoltageAt(healthyPositive, healthyNegative));
        assertEquals(400, results.getVoltageAtSqr(healthyPositive, healthyNegative));
        assertEquals(.2, results.getCurrentThrough(healthyPositive, healthyNegative));
        assertEquals(4, results.getHeatLoss(healthyPositive, healthyNegative), 1e-12);
        assertArrayEquals(new double[]{20, 20}, results.getVoltages(healthyPositive));
        assertEquals(70, original[0], "Masking must not rewrite the accepted samples used by callback rollback");
        assertEquals(.7, currents.getDouble(new DirectionalNodeConnection(rejectedPositive, rejectedNegative)));
    }
}
