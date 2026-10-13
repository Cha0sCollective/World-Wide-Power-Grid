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
import java.util.Arrays;
import java.util.HashSet;
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
        return new BridgeSimulationResults(samples, 2, new Object2DoubleOpenHashMap<>(), builder(order), null, unavailable);
    }

    private static CircuitBuilder builder(Node[] order) {
        var nodes = new ArrayList<WrappedIndexedNode>();
        var indexes = new Object2IntOpenHashMap<Node>();
        indexes.defaultReturnValue(-1);
        for (var node : order) {
            indexes.put(node, nodes.size()); nodes.add(new WrappedIndexedNode(node, nodes.size()));
        }
        return new CircuitBuilder(order.length, nodes, indexes);
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

    @Test void publicPublicationRetainsItsOwnCurrentsAndAvailabilityAfterCallerMutation() {
        Node first = new AttachedNode(0, "healthy"), second = new AttachedNode(1, "healthy");
        var connection = new DirectionalNodeConnection(first, second);
        var currents = new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        currents.put(connection, -.25);
        double[] samples = {10, -10, 0, 0};
        var unavailable = new HashSet<Node>();
        var retained = new BridgeSimulationResults(samples, 2, currents,
                builder(new Node[]{first, second}), null, unavailable);

        currents.put(connection, 100);
        currents.put(connection.invert(), 200);
        unavailable.add(first);
        Arrays.fill(samples, 99);
        assertTrue(retained.allAvailable());
        assertTrue(retained.available(first, second));
        assertEquals(10, retained.getVoltageAt(first, second));
        assertArrayEquals(new double[]{10, -10}, retained.getVoltages(first));
        assertEquals(-.25, retained.getCurrentThrough(first, second));
        assertEquals(.25, retained.getCurrentThrough(second, first));
        assertEquals(100, currents.getDouble(connection), "Public publication must not rewrite its caller's map");
    }

    @Test void ownedPublicationMasksLateRejectedAndCrossCircuitCurrentsWithoutRewritingSamples() {
        Node rejectedFirst = new AttachedNode(0, "rejected"), rejectedSecond = new AttachedNode(1, "rejected");
        Node healthyFirst = new AttachedNode(0, "healthy"), healthySecond = new AttachedNode(1, "healthy");
        var rejected = new DirectionalNodeConnection(rejectedFirst, rejectedSecond);
        var healthy = new DirectionalNodeConnection(healthyFirst, healthySecond);
        var crossCircuit = new DirectionalNodeConnection(healthyFirst, rejectedFirst);
        var currents = new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        currents.put(rejected, .7); currents.put(healthy, -.2); currents.put(crossCircuit, .1);
        double[] samples = {70, 70, 0, 0, 20, 20, 0, 0};
        // Availability is established only after the complete current map exists,
        // matching adapter finish/late validation before WorldBridge publishes.
        var unavailable = new HashSet<Node>();
        unavailable.add(rejectedFirst);
        var results = BridgeSimulationResults.takeOwnershipOfCurrents(samples, 2, currents,
                builder(new Node[]{rejectedFirst, rejectedSecond, healthyFirst, healthySecond}), null, unavailable);

        assertFalse(results.allAvailable());
        assertTrue(Double.isNaN(currents.getDouble(rejected)), "The transferred map is masked in place");
        assertTrue(Double.isNaN(currents.getDouble(crossCircuit)), "Either unavailable endpoint rejects a current");
        assertEquals(-.2, currents.getDouble(healthy));
        assertTrue(Double.isNaN(results.getCurrentThrough(rejectedFirst, rejectedSecond)));
        assertTrue(Double.isNaN(results.getCurrentThrough(healthyFirst, rejectedFirst)));
        assertTrue(Double.isNaN(results.getVoltageAt(rejectedFirst)));
        assertTrue(Double.isNaN(results.getVoltages(rejectedFirst)[0]));
        assertEquals(20, results.getVoltageAt(healthyFirst, healthySecond));
        assertEquals(-.2, results.getCurrentThrough(healthyFirst, healthySecond));
        assertEquals(.2, results.getCurrentThrough(healthySecond, healthyFirst));
        assertEquals(70, samples[0], "Masking must not mutate the callback/next-tick sample buffer");
        unavailable.clear();
        assertFalse(results.allAvailable(), "Publication owns its final availability snapshot");
        assertFalse(results.available(rejectedFirst));
    }

    @Test void retainedOwnedResultsSurviveTheNextPublicationWithAReusedSixteenStepBuffer() {
        Node first = new AttachedNode(0, "healthy"), second = new AttachedNode(1, "healthy");
        var connection = new DirectionalNodeConnection(first, second);
        double[] samples = new double[32];
        for (int step = 0; step < 16; step++) samples[step] = step % 2 == 0 ? 8 : -8;
        var firstCurrents = new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        firstCurrents.put(connection, -.2);
        var retained = BridgeSimulationResults.takeOwnershipOfCurrents(samples, 16, firstCurrents,
                builder(new Node[]{first, second}), null, Set.of());
        double[] firstWaveform = Arrays.copyOf(samples, 16);

        Arrays.fill(samples, 0, 16, 30);
        var secondCurrents = new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        secondCurrents.put(connection, .3);
        var next = BridgeSimulationResults.takeOwnershipOfCurrents(samples, 16, secondCurrents,
                builder(new Node[]{first, second}), null, Set.of());
        assertTrue(retained.allAvailable());
        assertTrue(next.allAvailable());
        assertArrayEquals(firstWaveform, retained.getVoltages(first));
        assertEquals(8, retained.getVoltageAt(first, second));
        assertEquals(64, retained.getVoltageAtSqr(first, second));
        assertEquals(-.2, retained.getCurrentThrough(first, second));
        assertEquals(.2, retained.getCurrentThrough(second, first));
        assertEquals(30, next.getVoltageAt(first, second));
        assertEquals(.3, next.getCurrentThrough(first, second));
        var returned = retained.getVoltages(first);
        Arrays.fill(returned, 1000);
        assertArrayEquals(firstWaveform, retained.getVoltages(first), "Published voltage arrays remain defensive copies");
    }
}
