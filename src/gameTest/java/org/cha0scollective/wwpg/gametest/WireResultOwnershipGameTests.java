package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireLifetimeModule;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import org.patryk3211.powergrid.collections.ModdedBlocks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the packaged redirect against real native wires without changing their temperature or random state. */
@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class WireResultOwnershipGameTests {
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void healthyNativeOrderAndRejectedEndpointsAndCutsUseTheActualSelector(GameTestHelper h) {
        var firstSource = h.absolutePos(new BlockPos(1, 2, 1));
        var firstLoad = firstSource.east(4);
        var secondSource = firstSource.south(4);
        var secondLoad = secondSource.east(4);
        for (var pos : new BlockPos[]{firstSource, firstLoad, secondSource, secondLoad})
            h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        for (var pos : new BlockPos[]{firstSource, secondSource})
            h.getLevel().setBlockAndUpdate(pos, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        for (var pos : new BlockPos[]{firstLoad, secondLoad})
            h.getLevel().setBlockAndUpdate(pos, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        h.runAtTickTime(5, () -> {
            for (var source : new BlockPos[]{firstSource, secondSource}) ShowroomTools.pgSource(h, source, 20);
            for (var load : new BlockPos[]{firstLoad, secondLoad}) ShowroomTools.resistance(h, load, 1000);
            for (int terminal = 0; terminal < 2; terminal++) {
                WiringGameTests.connect(h, firstSource, terminal, firstLoad, terminal, false);
                WiringGameTests.connect(h, secondSource, terminal, secondLoad, terminal, false);
            }
        });
        h.runAtTickTime(25, () -> {
            for (var load : new BlockPos[]{firstLoad, secondLoad}) {
                var reading = ElectricalMeasurements.readVoltageBetween(h.getLevel(), load, 0, load, 1);
                h.assertTrue(reading.status() == MeasurementStatus.VALID && reading.context().substeps() == 16,
                        "The selector fixture must start from an accepted sixteen-step powered circuit");
                BoardComponentGameTests.near(h, reading.voltage().orElseThrow().meanVoltageVolts(), 20, .05,
                        "Native source/load circuit before availability selection");
            }
            var infrastructure = InfrastructureSavedData.load(h.getLevel());
            var state = infrastructure.wireSimulationState;
            var endpointWire = new InWorldNodeConnection(new InWorldNode(0, firstSource), new InWorldNode(0, firstLoad));
            var cutWire = new InWorldNodeConnection(new InWorldNode(0, secondSource), new InWorldNode(0, secondLoad));
            var upstream = state.getAllConnections();
            var upstreamOrder = upstream.stream().map(Map.Entry::getKey).toList();
            var upstreamKeys = keys(upstream);
            h.assertTrue(upstreamKeys.contains(endpointWire) && upstreamKeys.contains(cutWire),
                    "Fixture wires must be present in the actual native wire state");
            var selector = selector();
            var healthy = results(infrastructure, List.of(endpointWire.node1(), endpointWire.node2(), cutWire.node1(), cutWire.node2()), Set.of());
            var selectedHealthy = select(selector, infrastructure.wireLifetimeModule, state, healthy);
            h.assertTrue(selectedHealthy == upstream, "Healthy selection must return the original native connection set");
            h.assertTrue(selectedHealthy.stream().map(Map.Entry::getKey).toList().equals(upstreamOrder),
                    "Healthy selection changed native iteration order, including first-longest overheating ties");

            var endpointRejected = results(infrastructure,
                    List.of(endpointWire.node1(), endpointWire.node2(), cutWire.node1(), cutWire.node2()), Set.of(endpointWire.node1()));
            var expectedEndpoint = new HashSet<>(upstreamKeys);
            expectedEndpoint.remove(endpointWire);
            h.assertTrue(keys(select(selector, infrastructure.wireLifetimeModule, state, endpointRejected)).equals(expectedEndpoint),
                    "Unavailable endpoint selection must exclude only that wire and retain unrelated native wires");

            var handle = state.createHandle("WWPG result availability fixture");
            try {
                var cut = state.createCut(handle, cutWire, .5f);
                h.assertTrue(state.cutExists(handle, cut), "The cut-node case must use a real native cut");
                var cutRejected = results(infrastructure,
                        List.of(endpointWire.node1(), endpointWire.node2(), cutWire.node1(), cutWire.node2(), cut), Set.of(cut));
                var expectedCut = new HashSet<>(upstreamKeys);
                expectedCut.remove(cutWire);
                h.assertTrue(keys(select(selector, infrastructure.wireLifetimeModule, state, cutRejected)).equals(expectedCut),
                        "Unavailable cut-node selection must exclude its wire even when both endpoints are available");
                var bothRejected = results(infrastructure,
                        List.of(endpointWire.node1(), endpointWire.node2(), cutWire.node1(), cutWire.node2(), cut), Set.of(endpointWire.node1(), cut));
                expectedCut.remove(endpointWire);
                h.assertTrue(keys(select(selector, infrastructure.wireLifetimeModule, state, bothRejected)).equals(expectedCut),
                        "Endpoint and cut-node rejection lost unrelated healthy native wires");
            } finally {
                state.invalidateHandle(handle);
            }
            h.assertTrue(keys(state.getAllConnections()).equals(upstreamKeys), "Availability selection mutated the native wires");
            var selectedAgain = select(selector, infrastructure.wireLifetimeModule, state, healthy);
            h.assertTrue(selectedAgain == upstream && selectedAgain.stream().map(Map.Entry::getKey).toList().equals(upstreamOrder),
                    "The healthy fast path did not recover its native set and order after rejected cases");
            DynamicGameTests.audit(h);
            h.succeed();
        });
    }

    private static BridgeSimulationResults results(InfrastructureSavedData infrastructure, List<? extends Node> nodes,
                                                   Set<Node> unavailable) {
        var builder = new CircuitBuilder(Set.<InWorldNode>of());
        for (var node : nodes) builder.addNode(node);
        return new BridgeSimulationResults(new double[nodes.size() * 16], 16, new Object2DoubleOpenHashMap<>(),
                builder, infrastructure, unavailable);
    }

    private static Set<InWorldNodeConnection> keys(Set<Map.Entry<InWorldNodeConnection, ConnectionEntry>> entries) {
        var keys = new HashSet<InWorldNodeConnection>();
        for (var entry : entries) keys.add(entry.getKey());
        return keys;
    }

    private static Method selector() {
        var matching = Arrays.stream(WireLifetimeModule.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("wwpg$availableWires") && method.getReturnType() == Set.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{WireSimulationState.class, SimulationResults.class}))
                .toList();
        if (matching.size() != 1) throw new IllegalStateException("Expected exactly one packaged availability redirect, found " + matching.size());
        var selector = matching.getFirst();
        selector.setAccessible(true);
        return selector;
    }

    @SuppressWarnings("unchecked")
    private static Set<Map.Entry<InWorldNodeConnection, ConnectionEntry>> select(Method selector, WireLifetimeModule module,
                                                                             WireSimulationState state, SimulationResults results) {
        try {
            return (Set<Map.Entry<InWorldNodeConnection, ConnectionEntry>>) selector.invoke(module, state, results);
        } catch (InvocationTargetException error) {
            throw new IllegalStateException("Packaged availability redirect failed", error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot invoke the packaged availability redirect", error);
        }
    }
}
