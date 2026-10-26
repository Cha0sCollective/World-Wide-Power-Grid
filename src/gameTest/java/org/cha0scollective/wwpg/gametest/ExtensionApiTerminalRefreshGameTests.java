package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InWorldNodeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalDevices;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;
import org.cha0scollective.wwpg.extension.ApiCeeWireIdentities;
import org.cha0scollective.wwpg.extension.ApiCircuits;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.cha0scollective.wwpg.wiring.Terminals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Unchanged API terminals must not rebuild CEE's global native facade. */
@GameTestHolder("wwpg_api_ownership")
@PrefixGameTestTemplate(false)
public final class ExtensionApiTerminalRefreshGameTests {
    private static final String PROVIDER = "wwpg:terminal_refresh_fixture";
    private static final Map<ServerLevel, Map<BlockPos, State>> STATES = new WeakHashMap<>();

    /** Packaged test profile only; unarmed vanilla copper has no electrical capability. */
    public static void register(RegisterCapabilitiesEvent event) {
        ElectricalProviders.registerBlock(event, PROVIDER, (level, pos, block, entity, context) -> {
            var state = level instanceof ServerLevel server ? STATES.getOrDefault(server, Map.of()).get(pos) : null;
            return state == null ? null : new Probe(state);
        }, Blocks.COPPER_BLOCK);
    }

    private static final class State {
        int geometry;
        boolean auxiliary;
        long commits;
        double stored;
    }

    private record Probe(State state) implements ElectricalDevice {
        @Override public String providerId() { return PROVIDER; }
        @Override public List<Terminal> terminals() {
            var terminals = new ArrayList<Terminal>();
            var point = switch (state.geometry) {
                case 1 -> new LocalPoint(.5, .8, .25);
                case 2 -> new LocalPoint(.5, .8, .4);
                default -> new LocalPoint(.25, .5, 0);
            };
            terminals.add(new Terminal("positive", 0, point, .12));
            terminals.add(new Terminal("negative", 1, new LocalPoint(.75, .5, 0), .12));
            if (state.auxiliary) terminals.add(new Terminal("auxiliary", 2, new LocalPoint(.5, .2, 0), .12));
            return List.copyOf(terminals);
        }
        @Override public CircuitDescription prepare(TickContext context) {
            var elements = new ArrayList<>(List.of(
                    new Element("supply", "positive", "negative", new ElectricalModel.VoltageSource(20)),
                    new Element("load", "positive", "negative", new ElectricalModel.Resistor(200)),
                    new Element("ballast", "positive", "stored", new ElectricalModel.Resistor(100)),
                    new Element("storage", "stored", "negative", new ElectricalModel.Capacitor(.01, state.stored))));
            if (state.auxiliary) elements.add(new Element("auxiliary_load", "positive", "auxiliary", new ElectricalModel.Resistor(1000)));
            return new CircuitDescription(terminals(), List.of(new InternalNode("stored")), elements, Set.of("negative"));
        }
        @Override public void commit(DeviceResults results) {
            ++state.commits;
            if (results.status() == MeasurementStatus.VALID)
                state.stored = ((ElementState.Capacitor) results.state("storage").orElseThrow()).voltageVolts();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void unchangedFacadeAndLiveTerminalEditsPreservePoweredOwnership(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var load = pos.east(3);
        var state = new State();
        STATES.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos, state);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.COPPER_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(load.below(), Blocks.STONE.defaultBlockState());
        var loadId = ResourceLocation.fromNamespaceAndPath("wwpg_api_example", "load");
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(loadId), "Independent public API addon is required");
        level.setBlockAndUpdate(load, BuiltInRegistries.BLOCK.get(loadId).defaultBlockState());
        h.assertTrue(ElectricalDevices.changed(level, pos), "Public terminal fixture was not nominated");
        var positive = new InWorldNodeConnection(new InWorldNode(0, pos), new InWorldNode(0, load));
        var auxiliary = new InWorldNodeConnection(new InWorldNode(2, pos), new InWorldNode(0, load));
        var storage = new Object[1];
        var removedId = new java.util.UUID[1];
        h.startSequence().thenIdle(5).thenExecute(() -> {
            WiringGameTests.connect(h, pos, 0, load, 0, false);
            WiringGameTests.connect(h, pos, 1, load, 1, true);
        }).thenIdle(25).thenExecute(() -> {
            powered(h, pos, load);
            var infrastructure = InfrastructureSavedData.load(level);
            var facade = facade(infrastructure);
            var pg = ApiTerminals.savedPgWireIdentities(level, pos);
            var cee = ApiCeeWireIdentities.get(level).idsAt(pos);
            h.assertTrue(!pg.isEmpty() && !cee.isEmpty(), "Fixture requires native PG and CEE ownership");
            storage[0] = ApiCircuits.get(level).elementIdentity(pos, "storage");
            long commits = state.commits; double stored = state.stored;
            for (int pass = 0; pass < 5; ++pass) {
                refresh(level, pos);
                Terminals.refreshPgNodes(level, pos);
            }
            h.assertTrue(facade == facade(infrastructure), "Unchanged terminals rebuilt the global CEE facade");
            h.assertTrue(commits == state.commits && stored == state.stored, "Safe terminal refresh advanced provider history");
            defaultNativePositionCache(h, pos, state);
            var oldWire = infrastructure.wireSimulationState.getConnection(positive);
            state.geometry = 1;
            h.assertTrue(ElectricalDevices.changed(level, pos), "Geometry edit was not nominated");
            refresh(level, pos);
            var movedWire = infrastructure.wireSimulationState.getConnection(positive);
            var expected = Vec3.atLowerCornerOf(pos).add(.5, .8, .25);
            h.assertTrue(facade != facade(infrastructure) && movedWire != oldWire,
                    "Same-ID geometry edit did not invoke native registration");
            h.assertTrue(movedWire != null && (movedWire.pos1.equals(expected) || movedWire.pos2.equals(expected)),
                    "Same-ID terminal movement left stale CEE wire geometry");
            h.assertTrue(pg.equals(ApiTerminals.savedPgWireIdentities(level, pos))
                            && cee.equals(ApiCeeWireIdentities.get(level).idsAt(pos)),
                    "Geometry edit changed persistent wire ownership");
            state.geometry = 2;
            h.assertTrue(ElectricalDevices.changed(level, pos), "Pre-read geometry edit was not nominated");
            // Native reads can overwrite the stored local position before safe
            // preparation. The sidecar flag must still request native refresh.
            ApiTerminals.get(level, pos);
            var preReadPosition = infrastructure.getNodePosition(new InWorldNode(0, pos));
            var expectedPreRead = Vec3.atLowerCornerOf(pos).add(.5, .8, .4);
            h.assertTrue(expectedPreRead.equals(preReadPosition), "Native getter did not observe the moved API terminal");
            // Pinned CEE skips wire-entry reconstruction after that getter has
            // updated localPos. Compare the original native registration path;
            // this optimization does not introduce a separate relocation fix.
            infrastructure.registerOrUpdateNodes(pos, List.of(0, 1));
            var referenceWire = infrastructure.wireSimulationState.getConnection(positive);
            var referenceFacade = facade(infrastructure);
            refresh(level, pos);
            var refreshedWire = infrastructure.wireSimulationState.getConnection(positive);
            h.assertTrue(referenceFacade != facade(infrastructure), "Pre-read geometry cleared pending facade refresh");
            h.assertTrue(refreshedWire != null && referenceWire != null
                            && referenceWire.pos1.equals(refreshedWire.pos1) && referenceWire.pos2.equals(refreshedWire.pos2),
                    "Pre-read wire geometry differs from the original native registration path");
            h.assertTrue(infrastructure.getNodeData(new InWorldNode(0, pos)).getLocalPos().equals(new Vec3(.5, .8, .4)),
                    "Safe refresh lost the current native terminal position");
            h.assertTrue(commits == state.commits && stored == state.stored, "Geometry refresh advanced stored energy");
            state.auxiliary = true;
            h.assertTrue(ElectricalDevices.changed(level, pos), "Added terminal was not nominated");
        }).thenIdle(15).thenExecute(() -> {
            powered(h, pos, load);
            h.assertTrue(nativeIds(level, pos).equals(Set.of(0, 1, 2)), "New native terminal ID was not registered");
            WiringGameTests.connect(h, pos, 2, load, 0, false);
        }).thenIdle(10).thenExecute(() -> {
            powered(h, pos, load);
            removedId[0] = ApiCeeWireIdentities.get(level).id(auxiliary).orElseThrow();
            h.assertTrue(InfrastructureSavedData.load(level).getConnectionData(auxiliary) != null,
                    "Auxiliary terminal did not own an actual CEE wire");
            state.auxiliary = false;
            h.assertTrue(ElectricalDevices.changed(level, pos), "Removed terminal was not nominated");
        }).thenIdle(15).thenExecute(() -> {
            powered(h, pos, load);
            var infrastructure = InfrastructureSavedData.load(level);
            h.assertTrue(nativeIds(level, pos).equals(Set.of(0, 1)) && infrastructure.getNodeData(new InWorldNode(2, pos)) == null,
                    "Removed terminal retained a native node");
            h.assertTrue(infrastructure.getConnectionData(auxiliary) == null
                            && !ApiCeeWireIdentities.get(level).idsAt(pos).contains(removedId[0]),
                    "Removed terminal retained its physical wire or identity");
            h.assertTrue(storage[0] == ApiCircuits.get(level).elementIdentity(pos, "storage") && state.stored > 1,
                    "Terminal edits recreated or reset accepted capacitor history");
            roundTripLedger(h, "org.cha0scollective.wwpg.extension.ApiTerminalIdentities", false);
            roundTripLedger(h, ApiCeeWireIdentities.class.getName(), true);
            // Repeat after removal: current native ownership is authoritative,
            // while the removed terminal's stable saved identity remains legal.
            var facade = facade(infrastructure);
            refresh(level, pos);
            h.assertTrue(facade == facade(infrastructure), "Stable post-removal refresh rebuilt the CEE facade");
            DynamicGameTests.audit(h);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(load, Blocks.AIR.defaultBlockState());
            STATES.get(level).remove(pos);
        }).thenSucceed();
    }

    private static void powered(GameTestHelper h, BlockPos pos, BlockPos load) {
        var result = ElectricalMeasurements.read(h.getLevel(), pos);
        h.assertTrue(result.status() == MeasurementStatus.VALID && result.context().substeps() == 16,
                "Incomplete public provider frame: " + result.status() + " " + result.detail());
        var own = result.element("load").orElseThrow();
        h.assertTrue(Math.abs(own.meanVoltageVolts() - 20) < .001 && Math.abs(own.meanCurrentAmps() - .1) < .00001,
                "Terminal refresh changed actual public API electrical results");
        var external = ElectricalMeasurements.read(h.getLevel(), load);
        h.assertTrue(external.status() == MeasurementStatus.VALID
                        && Math.abs(external.element("load_a").orElseThrow().meanCurrentAmps() - .2) < .0003,
                "Terminal edit lost the powered independent addon load");
    }

    private static Set<Integer> nativeIds(ServerLevel level, BlockPos pos) {
        var result = new java.util.HashSet<Integer>();
        for (var data : InfrastructureSavedData.load(level).getNodesAt(pos)) result.add(data.node.id());
        return Set.copyOf(result);
    }

    private static void refresh(ServerLevel level, BlockPos pos) {
        ApiTerminals.prepare(level, pos, ApiTerminals.device(level, pos));
    }

    /** Reproduce the real triplets' PG-only native cache without a mutating position getter. */
    private static void defaultNativePositionCache(GameTestHelper h, BlockPos pos, State state) {
        var level = h.getLevel(); var infrastructure = InfrastructureSavedData.load(level);
        var data = infrastructure.getNodeData(new InWorldNode(1, pos));
        var point = ApiTerminals.existing(level, pos).terminalDefinition(1).position();
        var expected = data.node.toGlobalPos(new Vec3(point.x(), point.y(), point.z()), level);
        // createNode starts at CENTER. Native registration sets globalPos but
        // may leave localPos there until an actual CEE geometry read occurs.
        storedPositions(data, InWorldNodeData.CENTER, expected);
        var before = facade(infrastructure);
        long commits = state.commits; double stored = state.stored;
        var model = ApiCircuits.get(level).elementIdentity(pos, "storage");
        for (int pass = 0; pass < 5; ++pass) refresh(level, pos);
        h.assertTrue(before == facade(infrastructure) && expected.equals(data.getGlobalPos())
                        && data.getLocalPos().equals(InWorldNodeData.CENTER),
                "Native default local cache rebuilt unchanged global terminal ownership");
        h.assertTrue(commits == state.commits && stored == state.stored
                        && model == ApiCircuits.get(level).elementIdentity(pos, "storage"),
                "Default native cache refresh advanced stored energy or replaced its model");
        // A genuinely incorrect native global position still needs the exact
        // original registration path; the optimization must not hide it.
        storedPositions(data, InWorldNodeData.CENTER, expected.add(.2, 0, 0));
        refresh(level, pos);
        h.assertTrue(before != facade(infrastructure) && expected.equals(data.getGlobalPos()),
                "Wrong native global geometry did not invoke original registration");
        h.assertTrue(commits == state.commits && stored == state.stored
                        && model == ApiCircuits.get(level).elementIdentity(pos, "storage"),
                "Native geometry repair advanced stored energy or replaced its model");
    }

    private static void storedPositions(InWorldNodeData data, Vec3 local, Vec3 global) {
        try {
            var type = data.getClass();
            var localField = type.getDeclaredField("localPos"); localField.setAccessible(true); localField.set(data, local);
            var globalField = type.getDeclaredField("globalPos"); globalField.setAccessible(true); globalField.set(data, global);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Pinned native position fields unavailable", error); }
    }

    private static Object facade(InfrastructureSavedData infrastructure) {
        try {
            var field = infrastructure.wireSimulationState.getClass().getDeclaredField("allLazyIndexedNodes");
            field.setAccessible(true);
            return field.get(infrastructure.wireSimulationState);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Pinned CEE facade field unavailable", error); }
    }

    private static void roundTripLedger(GameTestHelper h, String name, boolean needsLevel) {
        try {
            var type = Class.forName(name);
            var get = type.getDeclaredMethod("get", ServerLevel.class); get.setAccessible(true);
            var saved = ((SavedData) get.invoke(null, h.getLevel())).save(new CompoundTag(), h.getLevel().registryAccess());
            var load = needsLevel ? type.getDeclaredMethod("load", CompoundTag.class, ServerLevel.class)
                    : type.getDeclaredMethod("load", CompoundTag.class);
            load.setAccessible(true);
            var copy = (SavedData) (needsLevel ? load.invoke(null, saved.copy(), h.getLevel()) : load.invoke(null, saved.copy()));
            h.assertTrue(saved.equals(copy.save(new CompoundTag(), h.getLevel().registryAccess())),
                    "Native wire/terminal ownership changed across saved-data reload: " + name);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Ownership reload fixture unavailable", error); }
    }
}
