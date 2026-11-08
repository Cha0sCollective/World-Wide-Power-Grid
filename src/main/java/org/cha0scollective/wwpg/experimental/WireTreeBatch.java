package org.cha0scollective.wwpg.experimental;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.*;

/** Opt-in saved-wire attachment and server-thread tree resolution. Nothing is persisted. */
public final class WireTreeBatch {
    public static final boolean ENABLED = Boolean.getBoolean("wwpg.experimental.wireTreeBatch");
    private static final ThreadLocal<BaseWireEntity> READING = new ThreadLocal<>();
    private static final Map<WorldNetworks, State> PENDING = new IdentityHashMap<>();
    private static long attachments, fallback, roots, traversals, covered, visited, flushes, forced;
    private static int largest;
    private WireTreeBatch() { }
    public interface Access {
        boolean wwpg$traceTree(IWireEndpoint endpoint, Set<IWireEndpoint> visited);
        Map<OwnedFloatingNode, Set<TransmissionLinePart>> wwpg$partNodes();
        boolean wwpg$makeLine(TransmissionLinePart part);
    }
    private static final class State {
        final List<IWireEndpoint> roots = new ArrayList<>();
        final List<TransmissionLinePart> parts = new ArrayList<>();
        boolean flushing;
    }
    public static void reading(BaseWireEntity wire, Runnable original) {
        if (!ENABLED) { original.run(); return; }
        var previous = READING.get();
        READING.set(wire);
        try { original.run(); }
        finally { if (previous == null) READING.remove(); else READING.set(previous); }
    }
    /** A null return retains PG's complete ordinary connection path. */
    public static TransmissionLinePart attach(WorldNetworks world, IWireEndpoint first, IWireEndpoint second,
                                               BaseWireEntity wire, WorldNetworks.PartId id) {
        if (!ENABLED || READING.get() != wire) return null;
        if (!RestorationCoordinator.ENABLED || !(world.world instanceof ServerLevel level)
                || !level.getServer().isSameThread() || wire.getClass() != HangingWireEntity.class
                || first.getClass() != BlockWireEndpoint.class || second.getClass() != BlockWireEndpoint.class
                || !(id instanceof WorldNetworks.SimpleId simple) || !simple.id().equals(wire.getUUID())) {
            fallback++; return null;
        }
        var part = world.getPart(id);
        var state = PENDING.get(world);
        if (part == null || part.owner != null || state != null && state.flushing
                || !part.getEndpoint1().equals(first) || !part.getEndpoint2().equals(second)) {
            fallback++; return null;
        }
        // Reuse the saved object and UUID. Its unresolved line remains unavailable
        // until the existing iterative resolver runs before electrical preparation.
        part.grab(wire, id);
        state = PENDING.computeIfAbsent(world, ignored -> new State());
        state.roots.add(first); state.roots.add(second);
        state.parts.add(part);
        attachments++; roots += 2;
        return part;
    }
    public static void flush(ServerLevel level) {
        if (!ENABLED || PENDING.isEmpty()) return;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Wire restoration must run on the server thread");
        for (var entry : new ArrayList<>(PENDING.entrySet())) {
            var world = entry.getKey(); var state = entry.getValue();
            if (world.world != level || state.flushing) continue;
            state.flushing = true;
            largest = Math.max(largest, state.roots.size()); flushes++;
            var access = (Access) world;
            var counts = RestorationRoots.drain(state.roots, root -> {
                var seen = new HashSet<IWireEndpoint>();
                access.wwpg$traceTree(root, seen);
                visited += seen.size();
                // Only a completely resolved visited region can cover another root.
                // Unknown endpoint types, missing adjacency, or any unresolved part
                // retain every original root traversal. No coverage survives a flush.
                for (var endpoint : seen) {
                    if (endpoint.getClass() != BlockWireEndpoint.class) return Set.of();
                    var node = world.globalExternalNodes.get(endpoint);
                    var parts = access.wwpg$partNodes().get(node);
                    if (node == null || parts == null || parts.isEmpty()) return Set.of();
                    for (var part : parts) if (part.getLine() == null) return Set.of();
                }
                return seen;
            }, index -> {
                if ((index & 1) == 0) return false;
                var part = state.parts.get(index / 2);
                // PG finishes makeTransmissionLine with this step after both
                // root walks. It seeds a closed saved loop that has no resolved
                // edge yet. Removing it would strand otherwise valid loops.
                if (world.getPart(part.persistentOwnerId) == part && part.owner != null && part.getLine() == null) {
                    access.wwpg$makeLine(part); forced++;
                    return true; // Any connection attempt invalidates prior coverage.
                }
                return false;
            });
            traversals += counts.traversed(); covered += counts.covered();
            PENDING.remove(world);
        }
    }
    public static int pending(ServerLevel level) {
        return PENDING.entrySet().stream().filter(e -> e.getKey().world == level).mapToInt(e -> e.getValue().roots.size()).sum();
    }
    public static long attached() { return attachments; }
    public static void unload(ServerLevel level) { PENDING.keySet().removeIf(world -> world.world == level); }
    public static void reportAndClear() {
        if (ENABLED) WorldWidePowerGrid.LOGGER.info("WWPG experimental wire-tree batch: attachments={}, fallback={}, roots={}, traversals={}, covered={}, visited={}, flushes={}, largest={}, pending={}, forced={}",
                attachments, fallback, roots, traversals, covered, visited, flushes, largest,
                PENDING.values().stream().mapToInt(s -> s.roots.size()).sum(), forced);
        PENDING.clear(); READING.remove(); attachments = fallback = roots = traversals = covered = visited = flushes = forced = 0; largest = 0;
    }
}
