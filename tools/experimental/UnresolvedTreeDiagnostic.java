package org.cha0scollective.wwpg.benchmark;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

/** Read existing maps/chunks only. Never resolves an endpoint, creates an owner, or requests a chunk. */
public final class UnresolvedTreeDiagnostic {
    private static final com.google.gson.Gson JSON = new GsonBuilder().create();
    private UnresolvedTreeDiagnostic() { }

    public static void capture(MinecraftServer server, long tick, String phase) {
        try {
            var level = server.overworld();
            var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
            if (world == null) return;
            var parts = Collections.newSetFromMap(new IdentityHashMap<TransmissionLinePart, Boolean>());
            ((WireTreeBatch.Access) world).wwpg$partNodes().values().forEach(parts::addAll);
            var reasons = new TreeMap<String, Integer>();
            var examples = new LinkedHashMap<String, Object>();
            int resolved = 0, owned = 0, unowned = 0;
            for (var part : parts) {
                if (part.getLine() != null) { resolved++; continue; }
                if (part.owner == null) unowned++; else owned++;
                var first = describe(level, world, part.getEndpoint1(), part.getNode1());
                var second = describe(level, world, part.getEndpoint2(), part.getNode2());
                String a = (String) first.get("kind"), b = (String) second.get("kind");
                String pair = (a.compareTo(b) <= 0 ? a + " | " + b : b + " | " + a)
                        + " owner=" + (part.owner == null ? "absent" : part.owner.getClass().getSimpleName());
                reasons.merge(pair, 1, Integer::sum);
                examples.putIfAbsent(pair, Map.of("first", first, "second", second,
                        "same_saved_node", part.getNode1() == part.getNode2(),
                        "persistent_id", part.persistentOwnerId.toString()));
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("tick", tick); row.put("phase", phase); row.put("wall_unix_ms", System.currentTimeMillis());
            row.put("parts", parts.size()); row.put("resolved", resolved);
            row.put("unresolved_owned", owned); row.put("unresolved_unowned", unowned);
            row.put("pending_roots", WireTreeBatch.pending(level)); row.put("reasons", reasons); row.put("examples", examples);
            Files.writeString(Path.of("benchmark/unresolved-tree.jsonl"), JSON.toJson(row) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) { throw new IllegalStateException("Cannot inspect unresolved saved parts", failure); }
    }

    private static Map<String, Object> describe(ServerLevel level, WorldNetworks world,
            IWireEndpoint endpoint, OwnedFloatingNode savedNode) {
        var out = new LinkedHashMap<String, Object>();
        out.put("endpoint_type", endpoint.getClass().getName());
        var mapped = world.globalExternalNodes.get(endpoint);
        out.put("has_mapped_node", mapped != null);
        out.put("saved_node_is_mapped", savedNode == mapped);
        out.put("saved_node_has_network", savedNode != null && savedNode.getNetwork() != null);
        if (endpoint instanceof BlockWireEndpoint block) {
            var pos = block.getPos();
            out.put("position", java.util.List.of(pos.getX(), pos.getY(), pos.getZ()));
            out.put("terminal", block.getTerminal());
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) out.put("kind", "unloaded_block");
            else {
                var state = chunk.getBlockState(pos);
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                out.put("kind", id + " terminal=" + block.getTerminal());
                out.put("state", state.toString());
                out.put("entities_loaded", level.areEntitiesLoaded(chunk.getPos().toLong()));
                out.put("ticking", level.getChunkSource().isPositionTicking(chunk.getPos().toLong()));
                var entity = chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
                out.put("block_entity", entity == null ? "absent" : entity.getClass().getName());
            }
        } else out.put("kind", "other:" + endpoint.getClass().getSimpleName());
        return out;
    }
}
