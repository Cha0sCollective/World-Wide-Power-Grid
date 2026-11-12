package org.cha0scollective.wwpg.experimental;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.*;

/** Saved-wire attachment and server-thread tree resolution. Nothing is persisted. */
public final class WireTreeBatch {
    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("wwpg.experimental.wireTreeBatch", "true"));
    public static final int BUDGET_MILLIS = Integer.getInteger("wwpg.experimental.wireTreeBudgetMillis", 20);
    public static final int MAX_PARTS = Integer.getInteger("wwpg.experimental.wireTreeMaxParts", 32);
    public static final boolean BOUNDED = ENABLED && BUDGET_MILLIS > 0;
    public static final boolean PRIORITIZE_UNRESOLVED = ENABLED && Boolean.parseBoolean(System.getProperty("wwpg.experimental.wireTreePrioritizeUnresolved", "true"));
    static {
        if (BUDGET_MILLIS < 0 || MAX_PARTS < 1) throw new IllegalArgumentException(
                "Invalid wire-tree budget: wireTreeBudgetMillis="+BUDGET_MILLIS+" (must be >= 0), wireTreeMaxParts="+MAX_PARTS+" (must be >= 1)");
    }
    private static final ThreadLocal<BaseWireEntity> READING = new ThreadLocal<>();
    private static final Map<WorldNetworks, State> PENDING = new IdentityHashMap<>();
    private static final Map<ServerLevel, Integer> LAST_FLUSH = new IdentityHashMap<>();
    private static long attachments, fallback, roots, traversals, covered, visited, flushes, forced;
    private static long discarded, yielded, maxFlushNanos, maxEntryNanos;
    private static long prioritySelections;
    private static int largest, maxProcessed;
    private WireTreeBatch() { }
    public interface Access {
        boolean wwpg$traceTree(IWireEndpoint endpoint, Set<IWireEndpoint> visited);
        Map<OwnedFloatingNode, Set<TransmissionLinePart>> wwpg$partNodes();
        boolean wwpg$makeLine(TransmissionLinePart part);
    }
    private static final class State {
        final RestorationQueue<IWireEndpoint, Attachment> queue = new RestorationQueue<>();
        boolean flushing;
    }
    private record Attachment(TransmissionLinePart part, BaseWireEntity owner) { }
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
        state.queue.add(first, second, new Attachment(part, wire));
        attachments++; roots += 2;
        return part;
    }
    /** CEE's tickServer HEAD hook precedes Minecraft's tickCount increment. */
    public static void flushBeforeServerTick(ServerLevel level) { flush(level, level.getServer().getTickCount() + 1); }
    public static void flush(ServerLevel level) { flush(level, level.getServer().getTickCount()); }
    private static void flush(ServerLevel level, int serverTick) {
        if (!ENABLED) return;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Wire restoration must run on the server thread");
        if (BOUNDED && Objects.equals(LAST_FLUSH.put(level, serverTick), serverTick)) return;
        if (PENDING.isEmpty()) return;
        long start = System.nanoTime();
        long budget = BOUNDED ? BUDGET_MILLIS * 1_000_000L : Long.MAX_VALUE;
        int processed = 0, limit = BOUNDED ? MAX_PARTS : Integer.MAX_VALUE;
        for (var entry : new ArrayList<>(PENDING.entrySet())) {
            var world = entry.getKey(); var state = entry.getValue();
            if (world.world != level || state.flushing) continue;
            if (processed >= limit || processed > 0 && System.nanoTime() - start >= budget) break;
            state.flushing = true;
            largest = Math.max(largest, state.queue.size() * 2); flushes++;
            var access = (Access) world;
            RestorationQueue.Slice counts;
            try {
            counts = state.queue.drain(limit - processed, Math.max(1, budget - (System.nanoTime() - start)), System::nanoTime,
                    attachment -> world.getPart(attachment.part.persistentOwnerId) == attachment.part
                            && attachment.part.owner == attachment.owner && !attachment.owner.isRemoved(), root -> {
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
            }, attachment -> {
                var part = attachment.part;
                // PG finishes makeTransmissionLine with this step after both
                // root walks. It seeds a closed saved loop that has no resolved
                // edge yet. Removing it would strand otherwise valid loops.
                if (world.getPart(part.persistentOwnerId) == part && part.owner != null && part.getLine() == null) {
                    access.wwpg$makeLine(part); forced++;
                    return true; // Any connection attempt invalidates prior coverage.
                }
                return false;
            }, PRIORITIZE_UNRESOLVED ? attachment -> attachment.part.getLine() == null : null);
            } finally { state.flushing = false; }
            traversals += counts.traversed(); covered += counts.covered();
            discarded += counts.discarded(); processed += counts.processed();
            prioritySelections += counts.prioritized();
            maxEntryNanos = Math.max(maxEntryNanos, counts.longestEntryNanos());
            if (state.queue.size() == 0) PENDING.remove(world);
        }
        if (processed > 0) {
            maxProcessed = Math.max(maxProcessed, processed);
            maxFlushNanos = Math.max(maxFlushNanos, System.nanoTime() - start);
            if (pending(level) > 0) yielded++;
        }
    }
    public static int pending(ServerLevel level) {
        return PENDING.entrySet().stream().filter(e -> e.getKey().world == level).mapToInt(e -> e.getValue().queue.size() * 2).sum();
    }
    /** The first bounded prototype holds the whole dimension until its queue drains. */
    public static boolean holding(ServerLevel level) { return BOUNDED && pending(level) != 0; }
    public static boolean hasPending() { return BOUNDED && !PENDING.isEmpty(); }
    public static void visitPending(WorldNetworks world, java.util.function.BiConsumer<IWireEndpoint, TransmissionLinePart> visitor) {
        var state = PENDING.get(world);
        if (state == null) return;
        state.queue.visitEndpoints((endpoint, attachment) -> {
            if (world.getPart(attachment.part.persistentOwnerId) == attachment.part
                    && attachment.part.owner == attachment.owner && !attachment.owner.isRemoved())
                visitor.accept(endpoint, attachment.part);
        });
    }
    public static long attached() { return attachments; }
    public static long discarded() { return discarded; }
    public static void unload(ServerLevel level) {
        discarded += pending(level);
        PENDING.keySet().removeIf(world -> world.world == level); LAST_FLUSH.remove(level);
    }
    public static void reportAndClear() {
        if (ENABLED) WorldWidePowerGrid.LOGGER.info("WWPG experimental wire-tree batch: attachments={}, fallback={}, roots={}, traversals={}, covered={}, visited={}, flushes={}, largest={}, pending={}, forced={}, discarded={}, budgetMillis={}, maxParts={}, yielded={}, maxProcessed={}, maxFlushNanos={}, maxEntryNanos={}, prioritizeUnresolved={}, prioritySelections={}",
                attachments, fallback, roots, traversals, covered, visited, flushes, largest,
                PENDING.values().stream().mapToInt(s -> s.queue.size() * 2).sum(), forced, discarded,
                BUDGET_MILLIS, MAX_PARTS, yielded, maxProcessed, maxFlushNanos, maxEntryNanos,
                PRIORITIZE_UNRESOLVED, prioritySelections);
        PENDING.clear(); LAST_FLUSH.clear(); READING.remove();
        attachments = fallback = roots = traversals = covered = visited = flushes = forced = discarded = yielded = maxFlushNanos = maxEntryNanos = 0;
        largest = maxProcessed = 0;
        prioritySelections = 0;
    }
}
