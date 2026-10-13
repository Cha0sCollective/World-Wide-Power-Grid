package org.cha0scollective.wwpg.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.INode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.mixin.WorldNetworksAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Loading callbacks enqueue identities only. Preparation and decisions stay on the server thread. */
public final class RestorationCoordinator {
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("wwpg.restorationBatch", "true"));
    private static final Map<ServerLevel, State> WORLDS = new ConcurrentHashMap<>();
    // Networks own their ephemeral mark. Advancing the epoch expires all marks
    // without a retained identity set or touching discarded native networks.
    private static long pauseEpoch = 1;
    private static boolean anyPaused;
    private static int serverTick = Integer.MIN_VALUE;
    private RestorationCoordinator() { }
    private static State state(ServerLevel level) { return WORLDS.computeIfAbsent(level, ignored -> new State()); }
    public static void loaded(ChunkEvent.Load event) {
        if (ENABLED && !event.isNewChunk() && event.getLevel() instanceof ServerLevel level)
            state(level).incoming.add(event.getChunk().getPos().toLong());
    }
    public static void entityLoaded(EntityJoinLevelEvent event) {
        if (ENABLED && event.loadedFromDisk() && event.getEntity() instanceof BaseWireEntity wire && event.getLevel() instanceof ServerLevel level) {
            var state = state(level); long chunk = wire.chunkPosition().toLong();
            state.incoming.add(chunk);
            if (wire.getEndpoint1() instanceof BlockWireEndpoint first) state.wireDependencies.add(new Dependency(chunk, new BlockWireEndpoint(first.getPos().immutable(), first.getTerminal())));
            if (wire.getEndpoint2() instanceof BlockWireEndpoint second) state.wireDependencies.add(new Dependency(chunk, new BlockWireEndpoint(second.getPos().immutable(), second.getTerminal())));
        }
    }
    public static void unloaded(ChunkEvent.Unload event) {
        if (ENABLED && event.getLevel() instanceof ServerLevel level) state(level).retired.add(event.getChunk().getPos().toLong());
    }
    public static void begin(ServerLevel level) {
        if (!ENABLED) return;
        int tick = level.getServer().getTickCount();
        if (serverTick != tick) { nextPauseEpoch(); serverTick = tick; }
        var state = state(level); state.decisions.clear(); state.pausedPositions.clear(); state.heldDiscovery.clear();
        state.owners.clear(); state.claimed.clear(); state.dependencies.clear(); state.networkDependencies.clear();
        state.chunks.values().removeIf(record -> !record.window.active() && record.window.activated());
        Long chunk;
        while ((chunk = state.retired.poll()) != null) state.chunks.remove(chunk);
        while ((chunk = state.incoming.poll()) != null) state.chunks.computeIfAbsent(chunk, ignored -> new Chunk()).window.touch(tick);
        Dependency dependency;
        while ((dependency = state.wireDependencies.poll()) != null) {
            var record = state.chunks.computeIfAbsent(dependency.chunk(), ignored -> new Chunk());
            record.window.touch(tick); record.endpoints.add(dependency.endpoint());
        }
        for (var entry : state.chunks.entrySet()) {
            var record = entry.getValue();
            var position = new ChunkPos(entry.getKey());
            boolean ready = level.getChunkSource().getChunkNow(position.x, position.z) != null
                    && level.getChunkSource().isPositionTicking(entry.getKey()) && level.areEntitiesLoaded(entry.getKey());
            if (ready != record.ready && record.window.active()) record.window.touch(tick);
            record.ready = ready;
            for (var endpoint : record.endpoints) state.dependencies.computeIfAbsent(endpoint.getPos(), ignored -> new HashSet<>()).add(record);
        }
        state.active = state.chunks.values().stream().anyMatch(record -> record.window.active());
    }
    public static boolean active(ServerLevel level) { var state = WORLDS.get(level); return ENABLED && state != null && state.active; }
    public static boolean pending(ServerLevel level, ChunkPos chunk) { var state = WORLDS.get(level); var record = state == null ? null : state.chunks.get(chunk.toLong()); return record != null && record.window.active(); }
    public static void owner(ServerLevel level, OwnedFloatingNode node, BlockPos position) {
        if (ENABLED && position != null) state(level).owners.put(node, position);
    }
    private static List<Chunk> records(WorldNetworks world, ElectricalNetwork network, Set<BlockPos> positions) {
        if (!(world.world instanceof ServerLevel level)) return List.of();
        var state = state(level); var records = Collections.newSetFromMap(new IdentityHashMap<Chunk, Boolean>());
        records.addAll(state.networkDependencies.getOrDefault(network, Set.of()));
        var nodes = new ArrayList<INode>(network.getNodes()); nodes.addAll(network.getLeafs());
        for (var node : nodes) if (node instanceof OwnedFloatingNode owned) {
            BlockPos position = owned.endpoint instanceof BlockWireEndpoint endpoint ? endpoint.getPos() : state.owners.get(owned);
            if (position == null) continue;
            if (positions != null) positions.add(position);
            var record = state.chunks.get(ChunkPos.asLong(position)); if (record != null) records.add(record);
            records.addAll(state.dependencies.getOrDefault(position, Set.of()));
        }
        state.claimed.addAll(records);
        return List.copyOf(records);
    }
    /** Coupled circuits share their earliest deadline before any native preparation is deferred. */
    public static void joinCoupledWindows(List<WorldNetworks> worlds) {
        if (!ENABLED || WORLDS.values().stream().noneMatch(state -> state.active)) return;
        for (var world : worlds) if (world.world instanceof ServerLevel level && active(level)) {
            var state = state(level);
            for (var record : state.chunks.values()) for (var endpoint : record.endpoints) {
                var node = world.globalExternalNodes.get(endpoint);
                if (node == null) continue;
                if (node.getNetwork() != null) state.networkDependencies.computeIfAbsent(node.getNetwork(), ignored -> new HashSet<>()).add(record);
                else {
                    var line = world.findLineMiddle(node);
                    if (line == null) continue;
                    for (var end : new OwnedFloatingNode[]{line.getNode1(), line.getNode2()})
                        if (end != null && end.getNetwork() != null) state.networkDependencies.computeIfAbsent(end.getNetwork(), ignored -> new HashSet<>()).add(record);
                }
            }
        }
        var windows = new IdentityHashMap<ElectricalNetwork, RestorationWindow>();
        for (var world : worlds) if (world.world instanceof ServerLevel level && active(level)) for (var network : world.subnetworks) {
            var records = records(world, network, null); RestorationWindow batch = null;
            for (var record : records) { if (batch == null) batch = record.window; else batch.absorb(record.window); }
            if (batch != null) windows.put(network, batch);
        }
        for (var world : worlds) {
            for (var line : world.transmissionLines.values()) join(windows.get(line.getNode1().getNetwork()), windows.get(line.getNode2().getNetwork()));
            for (var network : world.subnetworks) for (var wire : ((ElectricalNetworkAccessor) network).wwpg$wires())
                if (wire instanceof SplitTransformerControllerWire transformer && transformer.secondary != null)
                    join(windows.get(network), windows.get(transformer.secondary.getNetwork()));
        }
        for (var state : WORLDS.values()) for (var record : state.chunks.values()) record.window.resetReady();
        for (var state : WORLDS.values()) for (var record : state.chunks.values()) record.window.observeReady(record.ready);
        for (var state : WORLDS.values()) state.active = state.chunks.values().stream().anyMatch(record -> record.window.active());
    }
    /** Chunks without circuit endpoints still have a finite loading window. */
    public static void finishPreparation(List<WorldNetworks> worlds) {
        if (!ENABLED) return;
        for (var world : worlds) if (world.world instanceof ServerLevel level) {
            var state = state(level);
            if (!state.active) continue;
            var claimedRoots = Collections.newSetFromMap(new IdentityHashMap<RestorationWindow, Boolean>());
            for (var record : state.claimed) claimedRoots.add(record.window.root());
            for (var record : state.chunks.values())
                if (!claimedRoots.contains(record.window.root())) record.window.hold(level.getServer().getTickCount(), record.ready);
        }
    }
    private static void join(RestorationWindow first, RestorationWindow second) { if (first != null && second != null) first.absorb(second); }
    public static boolean defer(WorldNetworks world, ElectricalNetwork network) {
        if (!ENABLED || !(world.world instanceof ServerLevel level)) return false;
        var state = state(level); var previous = state.decisions.get(network);
        if (previous != null) return previous;
        if (!state.active) return false;
        var positions = new HashSet<BlockPos>();
        var records = records(world, network, positions);
        RestorationWindow batch = null; boolean ready = true;
        for (var record : records) {
            ready &= record.ready;
            if (batch == null) batch = record.window;
            else if (batch != record.window) batch.absorb(record.window);
        }
        boolean hold = false;
        if (batch != null) {
            for (var record : records) record.window = batch;
            hold = batch.hold(level.getServer().getTickCount(), ready);
        }
        state.decisions.put(network, hold);
        if (hold) { markPaused(network); state.pausedPositions.addAll(positions); }
        return hold;
    }
    public static void holdDiscovery(WorldNetworks world, ElectricalNetwork network) {
        if (world.world instanceof ServerLevel level) state(level).heldDiscovery.add(network);
    }
    public static void retainDiscovery(WorldNetworks world) {
        if (ENABLED && world.world instanceof ServerLevel level) {
            var state = state(level);
            ((WorldNetworksAccessor) world).wwpg$islandQueue().addAll(state.heldDiscovery);
        }
    }
    public static boolean paused(ElectricalNetwork network) {
        return anyPaused && network != null && ((PauseMarker) network).wwpg$pauseEpoch() == pauseEpoch;
    }
    private static void markPaused(ElectricalNetwork network) {
        ((PauseMarker) network).wwpg$pauseEpoch(pauseEpoch);
        anyPaused = true;
    }
    private static void nextPauseEpoch() {
        if (++pauseEpoch == 0) ++pauseEpoch; // Zero belongs to an unmarked new network.
        anyPaused = false;
    }
    /** Internal, unsaved native-network state; the coordinator is its only writer. */
    public interface PauseMarker {
        long wwpg$pauseEpoch();
        void wwpg$pauseEpoch(long epoch);
    }
    public static boolean anyPaused() { return anyPaused; }
    /** Internal fixture entry point uses the same queued identity as the native loading events. */
    public static void restoring(ServerLevel level, ChunkPos chunk) {
        if (!ENABLED) throw new IllegalStateException("Restoration batching is disabled");
        state(level).incoming.add(chunk.toLong());
    }
    public static boolean pausedAt(ServerLevel level, BlockPos position) {
        var state = WORLDS.get(level); return anyPaused && state != null && state.pausedPositions.contains(position);
    }
    public static void markOwner(ServerLevel level, BlockPos position) { if (position != null) state(level).pausedPositions.add(position); }
    public static void coupledClosure(List<WorldNetworks> worlds) {
        if (!anyPaused) return;
        boolean changed;
        do {
            changed = false;
            for (var world : worlds) {
                for (var line : world.transmissionLines.values())
                    changed |= couple(line.getNode1().getNetwork(), line.getNode2().getNetwork());
                for (var network : world.subnetworks)
                    for (var wire : ((ElectricalNetworkAccessor) network).wwpg$wires())
                        if (wire instanceof SplitTransformerControllerWire transformer && transformer.secondary != null)
                            changed |= couple(network, transformer.secondary.getNetwork());
            }
        } while (changed);
        for (var world : worlds) if (world.world instanceof ServerLevel level)
            for (var network : world.subnetworks) if (paused(network))
                for (var node : network.getNodes()) if (node instanceof OwnedFloatingNode owned && owned.endpoint instanceof BlockWireEndpoint endpoint)
                    markOwner(level, endpoint.getPos());
    }
    private static boolean couple(ElectricalNetwork first, ElectricalNetwork second) {
        if (first == null || second == null || paused(first) == paused(second)) return false;
        markPaused(first); markPaused(second); return true;
    }
    public static String status(ServerLevel level) {
        var state = WORLDS.get(level);
        return "restorationBatch=" + ENABLED + ", pendingChunks=" + (state == null ? 0 : state.chunks.values().stream().filter(record -> record.window.active()).count())
                + ", pausedCircuits=" + (state == null ? 0 : state.decisions.values().stream().filter(Boolean::booleanValue).count());
    }
    public static void unload(ServerLevel level) { WORLDS.remove(level); }
    public static void clear() { WORLDS.clear(); nextPauseEpoch(); serverTick = Integer.MIN_VALUE; }
    private record Dependency(long chunk, BlockWireEndpoint endpoint) { }
    private static final class Chunk { RestorationWindow window = new RestorationWindow(); boolean ready; final Set<BlockWireEndpoint> endpoints = new HashSet<>(); }
    private static final class State {
        boolean active;
        final Queue<Long> incoming = new ConcurrentLinkedQueue<>(), retired = new ConcurrentLinkedQueue<>();
        final Queue<Dependency> wireDependencies = new ConcurrentLinkedQueue<>();
        final Map<Long, Chunk> chunks = new HashMap<>();
        final Map<ElectricalNetwork, Boolean> decisions = new IdentityHashMap<>();
        final Set<BlockPos> pausedPositions = new HashSet<>();
        final Set<ElectricalNetwork> heldDiscovery = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<OwnedFloatingNode, BlockPos> owners = new IdentityHashMap<>();
        final Set<Chunk> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<BlockPos, Set<Chunk>> dependencies = new HashMap<>();
        final Map<ElectricalNetwork, Set<Chunk>> networkDependencies = new IdentityHashMap<>();
    }
}
