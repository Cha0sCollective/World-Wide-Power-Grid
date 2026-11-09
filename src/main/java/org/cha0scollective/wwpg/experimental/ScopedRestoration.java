package org.cha0scollective.wwpg.experimental;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.mixin.WorldNetworksAccessor;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.INode;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import static org.cha0scollective.wwpg.experimental.RestorationComponents.join;

/** Server-thread metadata only. No endpoint resolution, geometry callbacks or retained graph index. */
public final class ScopedRestoration {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.scopedRestoration");
    private static Scope current;
    private static final Set<ElectricalNetwork> deferredNetworks = identitySet();
    private static final Set<INode> deferredNodes = identitySet();
    private static long snapshots, membershipChecks, savedParts, nodeBindings, seedEndpoints, heldNetworks, fallbackWorlds, totalNanos, maxNanos;
    private static long topologyPasses, topologyNanos, maxTopologyNanos;
    static {
        if (ENABLED && !WireTreeBatch.BOUNDED) throw new IllegalArgumentException("Scoped restoration requires bounded saved-wire batching");
    }
    private ScopedRestoration() { }
    private static <T> Set<T> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
    public static void beginTick() { current = null; deferredNetworks.clear(); deferredNodes.clear(); }
    /** Preserve holds actually used before topology preparation for this entire tick. */
    public static void deferred(ElectricalNetwork network) {
        if (!ENABLED || current == null || !deferredNetworks.add(network)) return;
        deferredNodes.addAll(network.getNodes()); deferredNodes.addAll(network.getLeafs());
    }
    public static void prepare(List<WorldNetworks> worlds) {
        if (!ENABLED) return;
        if (!WireTreeBatch.hasPending() && deferredNodes.isEmpty()) { current = null; return; }
        long start = System.nanoTime();
        for (var world : worlds) if (world.world instanceof ServerLevel level) {
            if (!level.getServer().isSameThread()) throw new IllegalStateException("Restoration scope must run on the server thread");
            Bridges.get(level).restorationOwners();
            org.cha0scollective.wwpg.extension.ApiCircuits.get(level).restorationOwners();
        }
        var scope = new Scope();
        for (var world : worlds) if (world.world instanceof ServerLevel level) scope.capture(world, level);
        for (var world : worlds) if (world.world instanceof ServerLevel level) {
            WireTreeBatch.visitPending(world, (endpoint, part) -> {
                scope.endpoint(level, endpoint).hold();
                scope.node(level, part.getNode1()).hold(); scope.node(level, part.getNode2()).hold();
                seedEndpoints++;
            });
        }
        for (var node : deferredNodes) {
            var vertex = scope.nodes.get(node); if (vertex != null) vertex.hold();
        }
        scope.applyFallbacks();
        current = scope; snapshots++;
        long elapsed = System.nanoTime() - start; totalNanos += elapsed; maxNanos = Math.max(maxNanos, elapsed);
    }
    /** Rebind after PG island discovery/merging and before any network advances. */
    public static void finishPreparation(List<WorldNetworks> worlds) {
        if (!ENABLED) return;
        prepare(worlds);
        if (current == null) return;
        for (var world : worlds) for (var network : world.subnetworks)
            if (holding(world, network)) RestorationCoordinator.holdScoped(world, network);
    }
    /** Let scheduled physical splits complete even while an affected component is held. */
    public static void discover(WorldNetworks world, ElectricalNetwork network) {
        long start = System.nanoTime();
        org.cha0scollective.wwpg.bridge.IndexedIslandDiscovery.run(world, network);
        long elapsed = System.nanoTime() - start;
        topologyPasses++; topologyNanos += elapsed; maxTopologyNanos = Math.max(maxTopologyNanos, elapsed);
    }
    public static boolean holding(WorldNetworks world, ElectricalNetwork network) {
        if (!(world.world instanceof ServerLevel level)) return false;
        if (!ENABLED || current == null) return WireTreeBatch.holding(level);
        var data = current.worlds.get(level);
        if (data == null) return WireTreeBatch.holding(level);
        var vertex = current.networks.get(network);
        if (!data.fallback && (vertex == null || !vertex.held()) && network != null) {
            // Preparation may have absorbed nodes since the snapshot or a
            // previous negative decision. Graft fresh membership before deferring.
            long start = System.nanoTime();
            vertex = current.captureNetwork(level, network); current.applyFallbacks(); membershipChecks++;
            long elapsed = System.nanoTime() - start; totalNanos += elapsed; maxNanos = Math.max(maxNanos, elapsed);
        }
        boolean held = data.fallback || vertex != null && vertex.held();
        if (held) heldNetworks++;
        return held;
    }
    public static boolean pausedAt(ServerLevel level, BlockPos position) {
        if (!ENABLED || current == null) return WireTreeBatch.holding(level);
        var data = current.worlds.get(level);
        if (data == null) return WireTreeBatch.holding(level);
        var vertex = data.positions.get(position);
        return data.fallback || vertex != null && vertex.held();
    }
    public static void invalidate() { beginTick(); }
    /** Read-only fixture diagnostics; never resolves an endpoint. */
    public static String diagnostic(ServerLevel level) {
        var data = current == null ? null : current.worlds.get(level);
        return data == null ? "no snapshot" : "fallback=" + data.fallback + ", unknown=" + data.unknownReasons;
    }
    public static void reportAndClear() {
        if (ENABLED) WorldWidePowerGrid.LOGGER.info("WWPG experimental scoped restoration: snapshots={}, membershipChecks={}, savedParts={}, nodeBindings={}, seedEndpoints={}, heldNetworks={}, fallbackWorlds={}, totalNanos={}, maxNanos={}, topologyPasses={}, topologyNanos={}, maxTopologyNanos={}",
                snapshots, membershipChecks, savedParts, nodeBindings, seedEndpoints, heldNetworks, fallbackWorlds, totalNanos, maxNanos, topologyPasses, topologyNanos, maxTopologyNanos);
        beginTick(); snapshots = membershipChecks = savedParts = nodeBindings = seedEndpoints = heldNetworks = fallbackWorlds = totalNanos = maxNanos = 0;
        topologyPasses = topologyNanos = maxTopologyNanos = 0;
    }
    private static final class WorldData {
        final Map<BlockPos, RestorationComponents.Node> positions = new HashMap<>();
        final Map<IWireEndpoint, RestorationComponents.Node> ownedEndpoints = new IdentityHashMap<>();
        final Set<RestorationComponents.Node> vertices = identitySet();
        final Set<String> unknownReasons = new TreeSet<>();
        boolean unknown, fallback;
    }
    private static final class Scope {
        final Map<ServerLevel, WorldData> worlds = new IdentityHashMap<>();
        final Map<ElectricalNetwork, RestorationComponents.Node> networks = new IdentityHashMap<>();
        final Map<INode, RestorationComponents.Node> nodes = new IdentityHashMap<>();
        final Map<IWireEndpoint, RestorationComponents.Node> unknownEndpoints = new IdentityHashMap<>();
        WorldData data(ServerLevel level) { return worlds.computeIfAbsent(level, ignored -> new WorldData()); }
        void unknown(ServerLevel level, String reason) { data(level).unknown = true; data(level).unknownReasons.add(reason); }
        RestorationComponents.Node remember(ServerLevel level, RestorationComponents.Node node) {
            data(level).vertices.add(node); return node;
        }
        RestorationComponents.Node position(ServerLevel level, BlockPos position) {
            return remember(level, data(level).positions.computeIfAbsent(position.immutable(), ignored -> new RestorationComponents.Node()));
        }
        RestorationComponents.Node endpoint(ServerLevel level, IWireEndpoint endpoint) {
            // CEE/API preparation has already registered solver-only endpoint
            // ownership. Do not invoke their deliberately unsupported geometry.
            var owned = data(level).ownedEndpoints.get(endpoint);
            if (owned != null) return owned;
            var apiOwner = org.cha0scollective.wwpg.extension.ApiCircuits.restorationOwner(level, endpoint);
            if (apiOwner != null) return position(level, apiOwner);
            if (endpoint != null && endpoint.getClass() == BlockWireEndpoint.class) {
                var position = ((BlockWireEndpoint) endpoint).getPos();
                if (position != null) return position(level, position);
            }
            unknown(level, "endpoint:" + (endpoint == null ? "null" : endpoint.getClass().getName()));
            return remember(level, unknownEndpoints.computeIfAbsent(endpoint, ignored -> new RestorationComponents.Node()));
        }
        RestorationComponents.Node network(ServerLevel level, ElectricalNetwork network) {
            if (network == null) return null;
            return remember(level, networks.computeIfAbsent(network, ignored -> new RestorationComponents.Node()));
        }
        RestorationComponents.Node node(ServerLevel level, INode node) {
            if (node == null) {
                unknown(level, "node:null");
                return remember(level, new RestorationComponents.Node());
            }
            var vertex = remember(level, nodes.computeIfAbsent(node, ignored -> new RestorationComponents.Node()));
            if (node instanceof OwnedFloatingNode owned) {
                var owner = RestorationCoordinator.ownerPosition(level, owned);
                if (owner == null) owner = org.cha0scollective.wwpg.extension.ApiCircuits.restorationOwner(level, owned.endpoint);
                if (owner != null) {
                    var position = position(level, owner); join(vertex, position);
                    if (owned.endpoint != null) data(level).ownedEndpoints.put(owned.endpoint, position);
                }
                if (owned.endpoint != null && owned.endpoint.getClass() == BlockWireEndpoint.class)
                    join(vertex, endpoint(level, owned.endpoint));
                if (node.getClass() == OwnedFloatingNode.class) join(vertex, network(level, owned.getNetwork()));
                else unknown(level, "node:" + node.getClass().getName());
            }
            return vertex;
        }
        RestorationComponents.Node captureNetwork(ServerLevel level, ElectricalNetwork network) {
            var vertex = network(level, network);
            for (var node : network.getNodes()) { join(vertex, node(level, node)); nodeBindings++; }
            for (var node : network.getLeafs()) { join(vertex, node(level, node)); nodeBindings++; }
            for (var wire : ((ElectricalNetworkAccessor) network).wwpg$wires())
                if (wire instanceof SplitTransformerControllerWire transformer && transformer.secondary != null)
                    join(vertex, network(level, transformer.secondary.getNetwork()));
            return vertex;
        }
        void applyFallbacks() {
            boolean changed;
            do {
                changed = false;
                for (var data : worlds.values()) if (data.unknown && !data.fallback
                        && data.vertices.stream().anyMatch(RestorationComponents.Node::held)) {
                    data.fallback = true; fallbackWorlds++; changed = true;
                    data.vertices.forEach(RestorationComponents.Node::hold);
                }
            } while (changed);
        }
        void capture(WorldNetworks world, ServerLevel level) {
            data(level);
            for (var network : world.subnetworks) captureNetwork(level, network);
            for (var entry : world.globalExternalNodes.entrySet()) {
                var vertex = node(level, entry.getValue());
                join(endpoint(level, entry.getKey()), vertex);
            }
            for (var part : ((WorldNetworksAccessor) world).wwpg$lineParts().values()) {
                var a = part.getNode1(); var b = part.getNode2();
                var firstNode = node(level, a); var secondNode = node(level, b);
                var first = endpoint(level, a == null ? null : part.getEndpoint1());
                var second = endpoint(level, b == null ? null : part.getEndpoint2());
                join(first, second); join(first, firstNode); join(second, secondNode); savedParts++;
            }
            for (var line : world.transmissionLines.values()) join(node(level, line.getNode1()), node(level, line.getNode2()));
        }
    }
}
